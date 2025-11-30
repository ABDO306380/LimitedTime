package com.AbdoAlabhar.LimitedTime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimeNotifier {

    private static final long TICK_MS = 50L;

    final CountdownConfigData savedConfig;
    private final Map<UUID, Long> remainingMillis = new HashMap<>();

    public TimeNotifier(ServerLevel world) {
        this.savedConfig = world.getDataStorage().computeIfAbsent(
                CountdownConfigData::load,
                CountdownConfigData::new,
                "timelimiter_countdown"
        );

        MinecraftForge.EVENT_BUS.register(this);
    }

    //--Get/Set Timer--
    public int getCountdownSeconds() {
        return savedConfig != null ? savedConfig.getCountdownSeconds() : 10;
    }
    public void setCountdownSeconds(int seconds) {
        if (savedConfig != null) {
            savedConfig.setCountdownSeconds(seconds);
            // clamp runtime values
            long max = (long) getCountdownSeconds() * 1000L;
            for (UUID u : remainingMillis.keySet()) {
                if (remainingMillis.get(u) > max) {
                    remainingMillis.put(u, max);
                    savedConfig.setRemainingMillis(u, max);
                }
            }
        }
    }
    public long getRemainingMillis(UUID uuid) {
        if (remainingMillis.containsKey(uuid)) return remainingMillis.get(uuid);
        if (savedConfig != null) {
            long computed = savedConfig.computeAndGetRemainingMillis(uuid);
            remainingMillis.put(uuid, computed);
            return computed;
        }
        return (long) getCountdownSeconds() * 1000L;
    }

    //--Get/Set Stackable days--
    public void setStackableDays(int days) {
        if (savedConfig != null) savedConfig.setStackableDays(days);
    }
    public int getStackableDays() {
        return savedConfig != null ? savedConfig.getStackableDays() : 3;
    }

    //--Get/Set Time Freezing--
    public boolean isFrozenGlobally(){
        return savedConfig.isFrozenGlobally();
    }
    public int setTimestate(boolean isFrozen){
        savedConfig.setTimeCountingStateGlobally(isFrozen);
        return isFrozen ? 1:0;
    }

    //--Reset All Countdowns
    public void resetAllCountdowns() {
        if (savedConfig == null) return;

        long baseMillis = (long) getCountdownSeconds() * 1000L;

        // iterate saved players (includes offline players)
        for (String key : savedConfig.getSavedPlayerKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                // set persistent value (persist happens in savedConfig.setRemainingMillis)
                savedConfig.setRemainingMillis(uuid, baseMillis);

                // sync runtime cache if online / present
                if (remainingMillis.containsKey(uuid)) {
                    savedConfig.markReset(uuid); // sets anchor to today
                    remainingMillis.put(uuid, baseMillis);
                }
            } catch (IllegalArgumentException ignored) {
                // skip any garbage keys
            }
        }
    }

    // ---------------- events ----------------
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // ensure anchor exists (first-join)
        savedConfig.markFirstJoin(uuid);

        // recompute remaining based on calendar (works even if server was offline)
        // computeAndGetRemainingMillis already persists the result internally
        long rem = savedConfig.computeAndGetRemainingMillis(uuid);
        remainingMillis.put(uuid, rem);

        player.sendSystemMessage(Component.literal("Playtime updated (calendar-based)."), true);
    }
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        long rem = remainingMillis.getOrDefault(uuid, savedConfig.getRemainingMillis(uuid));
        savedConfig.setRemainingMillis(uuid, rem);
        // keep runtime entry (optional)
    }
    // In the onServerTick method, modify the packet sending:
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (!isFrozenGlobally()) {
            UUID[] keys = remainingMillis.keySet().toArray(new UUID[0]);
            for (UUID uuid : keys) {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
                if (player == null) continue;

                if (!remainingMillis.containsKey(uuid)) continue;

                long rem = remainingMillis.get(uuid);
                rem -= TICK_MS;

                long baseMillis = (long) getCountdownSeconds() * 1000L;

                System.out.println("Sending time update to " + player.getScoreboardName() + ": " + rem + "ms, base: " + baseMillis + "ms");

                LimitedTimeNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RemainingTimePacket(player.getUUID(), rem, savedConfig.getGlobalTimezone().toString(), baseMillis)
                );

                if (rem <= 0L) {
                    player.displayClientMessage(Component.literal(getCountdownSeconds() + " seconds passed"), true);
                    player.connection.disconnect(Component.literal("Time is up!"));
                    long recomputed = savedConfig.computeAndGetRemainingMillis(uuid);
                    rem = recomputed;
                }

                remainingMillis.put(uuid, rem);
                savedConfig.setRemainingMillis(uuid, rem);
            }
        }
    }
}