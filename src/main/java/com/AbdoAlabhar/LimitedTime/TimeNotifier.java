package com.AbdoAlabhar.LimitedTime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeNotifier {
    private static final long TICK_MS = 50L;
    private final CountdownConfigData savedConfig;
    private final Map<UUID, Long> remainingMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTickTime = new ConcurrentHashMap<>();

    public TimeNotifier(ServerLevel world) {
        this.savedConfig = world.getDataStorage().computeIfAbsent(
                CountdownConfigData::load,
                CountdownConfigData::new,
                "timelimiter_countdown"
        );
        MinecraftForge.EVENT_BUS.register(this);
    }

    public CountdownConfigData getConfig() {
        return savedConfig;
    }

    public int getCountdownSeconds() {
        return savedConfig != null ? savedConfig.getCountdownSeconds() : 3600;
    }

    public void setCountdownSeconds(int seconds) {
        if (savedConfig != null) savedConfig.setCountdownSeconds(seconds);
    }

    public long getRemainingMillis(UUID uuid) {
        return savedConfig.getRemainingMillis(uuid);
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMillis.put(uuid, millis);
        savedConfig.setRemainingMillis(uuid, millis);
    }

    public int getStackableDays() {
        return savedConfig != null ? savedConfig.getStackableDays() : 3;
    }

    public void setStackableDays(int days) {
        if (savedConfig != null) savedConfig.setStackableDays(days);
    }

    public boolean isFrozenGlobally() {
        return savedConfig.isFrozenGlobally();
    }

    public void setFrozenGlobally(boolean frozen) {
        if (savedConfig != null) savedConfig.setTimeCountingStateGlobally(frozen);
    }

    public void resetAllCountdowns() {
        if (savedConfig == null) return;
        for (String key : savedConfig.getSavedPlayerKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                savedConfig.resetPlayer(uuid);
                remainingMillis.put(uuid, savedConfig.getRemainingMillis(uuid));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        savedConfig.markFirstJoin(uuid);
        long currentRemaining = savedConfig.getRemainingMillis(uuid);
        long accumulated = savedConfig.getAccumulatedMillis(uuid);
        long maxPossible = savedConfig.getMaxPossibleMillis(uuid);

        remainingMillis.put(uuid, currentRemaining);
        lastTickTime.put(uuid, System.currentTimeMillis());

        String status = String.format("Time: %s (Accumulated: %s/%s)",
                formatTimeWithMillis(currentRemaining),
                formatTimeWithMillis(accumulated),
                formatTimeWithMillis(maxPossible));
        player.sendSystemMessage(Component.literal(status), true);

        LimitedTimeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RemainingTimePacket(uuid, currentRemaining,
                        savedConfig.getGlobalTimezone().getId(),
                        (long) getCountdownSeconds() * 1000L,
                        isFrozenGlobally(),
                        accumulated,
                        maxPossible)
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();

        Long currentTime = remainingMillis.get(uuid);
        if (currentTime != null) {
            savedConfig.setRemainingMillis(uuid, currentTime);
        }
        remainingMillis.remove(uuid);
        lastTickTime.remove(uuid);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || isFrozenGlobally()) return;

        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : remainingMillis.entrySet()) {
            UUID uuid = entry.getKey();
            Long rem = entry.getValue();

            if (rem == null || rem <= 0) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            Long lastTick = lastTickTime.get(uuid);
            if (lastTick == null) {
                lastTickTime.put(uuid, currentTime);
                continue;
            }

            long elapsed = currentTime - lastTick;
            long newRem = rem - elapsed;

            if (newRem <= 0) {
                newRem = 0;
                savedConfig.deductTime(uuid, rem);
                player.displayClientMessage(Component.literal("Time limit reached!"), true);
                player.connection.disconnect(Component.literal("Your accumulated playtime has expired. More time will be added tomorrow!"));
            } else {
                savedConfig.deductTime(uuid, elapsed);

                if (newRem <= 300000 && newRem > 240000) {
                    player.displayClientMessage(Component.literal("Warning: Less than 5 minutes remaining"), true);
                } else if (newRem <= 60000) {
                    player.displayClientMessage(Component.literal("Warning: Less than 1 minute remaining!"), true);
                }
            }

            remainingMillis.put(uuid, newRem);
            lastTickTime.put(uuid, currentTime);

            if (currentTime % 10000 < 50) {
                long accumulated = savedConfig.getAccumulatedMillis(uuid);
                long maxPossible = savedConfig.getMaxPossibleMillis(uuid);
                LimitedTimeNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new RemainingTimePacket(uuid, newRem,
                                savedConfig.getGlobalTimezone().getId(),
                                (long) getCountdownSeconds() * 1000L,
                                isFrozenGlobally(),
                                accumulated,
                                maxPossible)
                );
            }
        }
    }

    private String formatTimeWithMillis(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long remainingMillis = millis % 1000;

        if (hours > 0) {
            return String.format("%dh %02dm %02d.%03ds", hours, minutes, seconds, remainingMillis);
        } else if (minutes > 0) {
            return String.format("%dm %02d.%03ds", minutes, seconds, remainingMillis);
        } else {
            return String.format("%d.%03ds", seconds, remainingMillis);
        }
    }
}