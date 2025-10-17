package com.AbdoAlabhar.TimeLimiter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TimeNotifier {

    private static final long TICK_MS = 50L;

    private final CountdownConfigData savedConfig;
    // runtime copy for fast access (keeps in sync with savedConfig)
    private final Map<UUID, Long> remainingMillis = new HashMap<>();

    public TimeNotifier(ServerLevel world) {
        this.savedConfig = world.getDataStorage().computeIfAbsent(
                CountdownConfigData::load,
                CountdownConfigData::new,
                "timelimiter_countdown"
        );

        // initialize runtime map from saved data
        for (String key : this.savedConfig.save(new net.minecraft.nbt.CompoundTag()).getCompound("PlayerRemaining").getAllKeys()) {
            // defensive: we'll populate lazily in onPlayerLogin instead
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    // get/set persistent countdown length
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

    // return remaining millis for player (runtime-first, fallback to be saved)
    public long getRemainingMillis(UUID uuid) {
        if (remainingMillis.containsKey(uuid)) return remainingMillis.get(uuid);
        // fallback to saved data
        return savedConfig != null ? savedConfig.getRemainingMillis(uuid) : (long) getCountdownSeconds() * 1000L;
    }

    // ---------------- events ----------------

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // load persisted remaining into runtime map (or full duration if absent)
        long persisted = savedConfig.getRemainingMillis(uuid);
        remainingMillis.put(uuid, persisted);

        // optional user feedback
        player.sendSystemMessage(Component.literal("Countdown started!"), true);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        // persist current runtime remaining (if any)
        long rem = remainingMillis.getOrDefault(uuid, (long) getCountdownSeconds() * 1000L);
        if (savedConfig != null) savedConfig.setRemainingMillis(uuid, rem);
        // keep runtime entry (optional) — we keep it so server restart retains it from saved data
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // iterate copy of keys to avoid concurrent modification
        UUID[] keys = remainingMillis.keySet().toArray(new UUID[0]);
        for (UUID uuid : keys) {
            // only decrement if player online
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            long rem = remainingMillis.getOrDefault(uuid, (long) getCountdownSeconds() * 1000L);
            rem -= TICK_MS;
            long totalMillis = (long) getCountdownSeconds() * 1000L;

            if (rem <= 0L) {
                // finished -> notify and reset
                player.displayClientMessage(Component.literal(getCountdownSeconds() + " seconds passed"), true);
                rem = totalMillis; // reset for repeating
            }

            remainingMillis.put(uuid, rem);
            // persist the updated remaining so quitting saves current resume point
            if (savedConfig != null) savedConfig.setRemainingMillis(uuid, rem);
        }
    }
}
