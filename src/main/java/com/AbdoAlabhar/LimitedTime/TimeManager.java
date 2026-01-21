package com.AbdoAlabhar.LimitedTime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TimeManager with instant game rule synchronization.
 *
 * This implementation responds immediately to game rule changes through callbacks
 * registered in TimeLimiterGameRules. There is zero delay between a game rule
 * change and the corresponding update to saved config and client notifications.
 */
public class TimeManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TICK_MS = 50L;
    private static final int SAVE_INTERVAL_TICKS = 100; // Save every 5 seconds

    private int tickCounter = 0;

    final CountdownConfigData savedConfig;
    private final Map<UUID, Long> remainingMillis = new HashMap<>();
    private final MinecraftServer server;
    private final ServerLevel world;

    // Flag to prevent circular updates (when we set game rules programmatically)
    private boolean suppressGameRuleCallbacks = false;

    public TimeManager(ServerLevel world) {
        this.server = world.getServer();
        this.world = world;

        this.savedConfig = world.getDataStorage().computeIfAbsent(
                CountdownConfigData::load,
                CountdownConfigData::new,
                "timelimiter_countdown"
        );

        // Initialize config from game rules during world creation
        // For existing worlds, sync game rules to match saved config
        initializeFromGameRules(world.getGameRules());

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("TimeManager initialized with instant game rule synchronization");
    }

    /**
     * Initializes configuration based on whether this is a new or existing world.
     *
     * For NEW worlds (during creation):
     * - The game rules have been set by the user in the world creation screen
     * - We read those game rule values and use them to initialize our saved config
     * - This ensures the user's choices in world creation are respected
     *
     * For EXISTING worlds (loading saved game):
     * - The saved config already has persistent values from previous sessions
     * - We update the game rules to match the saved config
     * - This maintains consistency between restarts
     */
    private void initializeFromGameRules(GameRules gameRules) {
        // Detect if this is a brand new configuration (default values suggest new world)
        boolean isNewConfig = savedConfig.getCountdownSeconds() == 3600
                && savedConfig.getStackableDays() == 3
                && savedConfig.isFrozenGlobally() == true;

        if (isNewConfig) {
            // NEW WORLD: Use game rule values set during world creation
            int baseSeconds = TimeLimiterGameRules.getBaseSeconds(gameRules);
            int stackableDays = TimeLimiterGameRules.getStackableDays(gameRules);
            boolean frozen = TimeLimiterGameRules.isFrozen(gameRules);

            // Only update if game rules differ from defaults
            // (meaning user customized them during world creation)
            if (baseSeconds != 3600 || stackableDays != 3 || frozen != true) {
                savedConfig.setCountdownSeconds(baseSeconds);
                savedConfig.setStackableDays(stackableDays);
                savedConfig.setTimeCountingStateGlobally(frozen);

                LOGGER.info("New world: initialized config from game rules: {}s base, {} stackable days, frozen: {}",
                        baseSeconds, stackableDays, frozen);
            } else {
                LOGGER.info("New world: using default configuration values");
            }
        } else {
            // EXISTING WORLD: Sync game rules to match saved config
            syncGameRulesToConfig(gameRules);
        }
    }

    /**
     * Syncs game rules to match saved configuration.
     * Used when loading existing worlds to maintain consistency.
     *
     * We suppress callbacks here because we're the ones making the changes,
     * and we don't want to trigger our own callback handlers.
     */
    private void syncGameRulesToConfig(GameRules gameRules) {
        suppressGameRuleCallbacks = true;
        try {
            TimeLimiterGameRules.setBaseSeconds(gameRules, savedConfig.getCountdownSeconds());
            TimeLimiterGameRules.setStackableDays(gameRules, savedConfig.getStackableDays());
            TimeLimiterGameRules.setFrozen(gameRules, savedConfig.isFrozenGlobally());

            LOGGER.info("Existing world: synced game rules to match saved config");
        } finally {
            suppressGameRuleCallbacks = false;
        }
    }

    // ==================== Game Rule Change Callbacks ====================

    /**
     * Called instantly when timeLimiterBaseSeconds game rule changes.
     * This is triggered by the callback registered in TimeLimiterGameRules.
     *
     * This method is called whether the change comes from:
     * - /gamerule command
     * - World creation screen
     * - Programmatic changes
     */
    public void onGameRuleChanged_BaseSeconds(int newValue) {
        // Ignore if we're the ones setting the game rule (prevents circular updates)
        if (suppressGameRuleCallbacks) return;

        LOGGER.info("Detected instant game rule change: base seconds -> {}", newValue);

        // Update saved config to match
        savedConfig.setCountdownSeconds(newValue);

        // Clamp any player times exceeding new maximum
        long max = (long) newValue * 1000L;
        for (UUID uuid : remainingMillis.keySet()) {
            long current = remainingMillis.get(uuid);
            if (current > max) {
                remainingMillis.put(uuid, max);
                savedConfig.setRemainingMillis(uuid, max);
            }
        }

        // Instantly notify all online players
        broadcastTimeUpdate();
    }

    /**
     * Called instantly when timeLimiterStackableDays game rule changes.
     */
    public void onGameRuleChanged_StackableDays(int newValue) {
        if (suppressGameRuleCallbacks) return;

        LOGGER.info("Detected instant game rule change: stackable days -> {}", newValue);

        savedConfig.setStackableDays(newValue);

        // Notify all online players
        broadcastTimeUpdate();
    }

    /**
     * Called instantly when timeLimiterFrozen game rule changes.
     */
    public void onGameRuleChanged_Frozen(boolean newValue) {
        if (suppressGameRuleCallbacks) return;

        LOGGER.info("Detected instant game rule change: frozen -> {}", newValue);

        savedConfig.setTimeCountingStateGlobally(newValue);

        // Instantly notify all online players so their timers freeze/unfreeze immediately
        broadcastTimeUpdate();
    }

    // ==================== Configuration Getters/Setters ====================

    public int getCountdownSeconds() {
        return savedConfig != null ? savedConfig.getCountdownSeconds() : 3600;
    }

    /**
     * Sets base countdown seconds via command or code.
     * Also updates game rule to keep them in sync.
     *
     * Note: Setting the game rule will trigger our callback, but we suppress it
     * to avoid duplicate broadcasts since we're already broadcasting here.
     */
    public void setCountdownSeconds(int seconds) {
        if (savedConfig != null) {
            savedConfig.setCountdownSeconds(seconds);

            // Update game rule to match (suppress callback to avoid double-broadcast)
            suppressGameRuleCallbacks = true;
            try {
                if (world != null) {
                    TimeLimiterGameRules.setBaseSeconds(world.getGameRules(), seconds);
                }
            } finally {
                suppressGameRuleCallbacks = false;
            }

            // Clamp player times
            long max = (long) seconds * 1000L;
            for (UUID uuid : remainingMillis.keySet()) {
                long current = remainingMillis.get(uuid);
                if (current > max) {
                    remainingMillis.put(uuid, max);
                    savedConfig.setRemainingMillis(uuid, max);
                }
            }

            broadcastTimeUpdate();
            LOGGER.info("Set countdown seconds to {} via command", seconds);
        }
    }

    public int getStackableDays() {
        return savedConfig != null ? savedConfig.getStackableDays() : 3;
    }

    public void setStackableDays(int days) {
        if (savedConfig != null) {
            savedConfig.setStackableDays(days);

            suppressGameRuleCallbacks = true;
            try {
                if (world != null) {
                    TimeLimiterGameRules.setStackableDays(world.getGameRules(), days);
                }
            } finally {
                suppressGameRuleCallbacks = false;
            }

            broadcastTimeUpdate();
            LOGGER.info("Set stackable days to {} via command", days);
        }
    }

    public boolean isFrozenGlobally() {
        return savedConfig != null && savedConfig.isFrozenGlobally();
    }

    public int setTimestate(boolean isFrozen) {
        if (savedConfig != null) {
            savedConfig.setTimeCountingStateGlobally(isFrozen);

            suppressGameRuleCallbacks = true;
            try {
                if (world != null) {
                    TimeLimiterGameRules.setFrozen(world.getGameRules(), isFrozen);
                }
            } finally {
                suppressGameRuleCallbacks = false;
            }

            broadcastTimeUpdate();
            LOGGER.info("Set time state to {} via command", isFrozen ? "FROZEN" : "COUNTING");
        }
        return isFrozen ? 1 : 0;
    }

    public void setGlobalTimezone(String zoneId) {
        if (savedConfig != null) {
            savedConfig.setGlobalTimezone(zoneId);
            broadcastTimeUpdate();
            LOGGER.info("Set global timezone to {}", zoneId);
        }
    }

    public long getRemainingMillis(UUID uuid) {
        if (remainingMillis.containsKey(uuid)) {
            return remainingMillis.get(uuid);
        }

        if (savedConfig != null) {
            long computed = savedConfig.computeAndGetRemainingMillis(uuid);
            remainingMillis.put(uuid, computed);
            return computed;
        }

        return (long) getCountdownSeconds() * 1000L;
    }

    public void resetAllCountdowns() {
        if (savedConfig == null) return;

        long baseMillis = (long) getCountdownSeconds() * 1000L;

        for (String key : savedConfig.getSavedPlayerKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                savedConfig.markReset(uuid);
                savedConfig.setRemainingMillis(uuid, baseMillis);

                if (remainingMillis.containsKey(uuid)) {
                    remainingMillis.put(uuid, baseMillis);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        broadcastTimeUpdate();
        LOGGER.info("Reset all countdowns to base time");
    }

    // ==================== Network Communication ====================

    private void sendTimeUpdate(ServerPlayer player, long timeMillis) {
        LimitedTimeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RemainingTimePacket(
                        player.getUUID(),
                        timeMillis,
                        savedConfig.getGlobalTimezone().toString(),
                        isFrozenGlobally(),
                        getCountdownSeconds()
                )
        );
    }

    private void broadcastTimeUpdate() {
        if (server == null) return;

        int count = 0;
        for (Map.Entry<UUID, Long> entry : remainingMillis.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendTimeUpdate(player, entry.getValue());
                count++;
            }
        }

        if (count > 0) {
            LOGGER.debug("Broadcasted time update to {} player(s)", count);
        }
    }

    // ==================== Event Handlers ====================

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        savedConfig.markFirstJoin(uuid);

        long remaining = savedConfig.computeAndGetRemainingMillis(uuid);
        remainingMillis.put(uuid, remaining);
        savedConfig.setRemainingMillis(uuid, remaining);

        sendTimeUpdate(player, remaining);

        player.sendSystemMessage(
                Component.literal("Playtime updated (calendar-based)."),
                true
        );

        LOGGER.info("Player {} logged in with {} ms remaining",
                player.getGameProfile().getName(), remaining);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        long remaining = remainingMillis.getOrDefault(uuid, savedConfig.getRemainingMillis(uuid));
        savedConfig.setRemainingMillis(uuid, remaining);

        LOGGER.info("Player {} logged out with {} ms remaining",
                player.getGameProfile().getName(), remaining);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;

        // Only count down when time is NOT frozen
        if (!isFrozenGlobally()) {
            UUID[] keys = remainingMillis.keySet().toArray(new UUID[0]);

            for (UUID uuid : keys) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) continue;

                long remaining = remainingMillis.get(uuid);
                remaining -= TICK_MS;

                if (remaining <= 0L) {
                    player.displayClientMessage(
                            Component.literal("Your playtime has expired!"),
                            true
                    );
                    player.connection.disconnect(
                            Component.literal("Time is up!")
                    );

                    long recomputed = savedConfig.computeAndGetRemainingMillis(uuid);
                    remaining = recomputed;
                    sendTimeUpdate(player, remaining);

                    LOGGER.info("Player {} ran out of time and was disconnected",
                            player.getGameProfile().getName());
                }

                remainingMillis.put(uuid, remaining);
            }
        }

        // Periodic save to disk
        if (tickCounter >= SAVE_INTERVAL_TICKS) {
            tickCounter = 0;
            for (Map.Entry<UUID, Long> entry : remainingMillis.entrySet()) {
                savedConfig.setRemainingMillis(entry.getKey(), entry.getValue());
            }
        }
    }
}