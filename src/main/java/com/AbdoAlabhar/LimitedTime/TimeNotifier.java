package com.AbdoAlabhar.LimitedTime;

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

    // proxy/global accessors (so commands keep working)
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

    public void setStackableDays(int days) {
        if (savedConfig != null) savedConfig.setStackableDays(days);
    }

    public int getStackableDays() {
        return savedConfig != null ? savedConfig.getStackableDays() : 3;
    }

    // runtime-first remaining getter. If we don't have it in memory, recompute from savedConfig (calendar-safe).
    public long getRemainingMillis(UUID uuid) {
        if (remainingMillis.containsKey(uuid)) return remainingMillis.get(uuid);
        if (savedConfig != null) {
            long computed = savedConfig.computeAndGetRemainingMillis(uuid);
            remainingMillis.put(uuid, computed);
            return computed;
        }
        return (long) getCountdownSeconds() * 1000L;
    }

    // ---------------- events ----------------

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        // ensure anchor exists (first-join)
        savedConfig.markFirstJoin(uuid);

        // recompute remaining based on calendar (works even if server was offline)
        long rem = savedConfig.computeAndGetRemainingMillis(uuid);
        remainingMillis.put(uuid, rem);
        // persist (so NBT has fresh value)
        savedConfig.setRemainingMillis(uuid, rem);

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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        UUID[] keys = remainingMillis.keySet().toArray(new UUID[0]);
        for (UUID uuid : keys) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue; // only decrement online players

            // Skip players who have not logged in at least once
            if (!remainingMillis.containsKey(uuid)) continue;

            long rem = remainingMillis.get(uuid);
            rem -= TICK_MS;

            if (rem <= 0L) {
                player.displayClientMessage(Component.literal(getCountdownSeconds() + " seconds passed"), true);
                player.connection.disconnect(Component.literal("Time is up!"));
                // recompute for next session
                long recomputed = savedConfig.computeAndGetRemainingMillis(uuid);
                rem = recomputed;
            }

            remainingMillis.put(uuid, rem);
            savedConfig.setRemainingMillis(uuid, rem);
        }
    }
}
