package com.AbdoAlabhar.TimeLimiter;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

    public void setStackableDays(int days) {
        if (savedConfig != null) savedConfig.setStackableDays(days);
    }

    public int getStackableDays() {
        return savedConfig != null ? savedConfig.getStackableDays() : 3;
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

        if (savedConfig != null && savedConfig.shouldReset()) {
            long baseMillis = (long) savedConfig.getCountdownSeconds() * 1000L;
            long cap = baseMillis * savedConfig.getStackableDays();

            LocalDate last = savedConfig.getLastResetDate();
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            long daysSinceLastReset = (last == null) ? Long.MAX_VALUE :
                    ChronoUnit.DAYS.between(last, today);

            // Debug log
            // LOGGER.info("Reset check: last={}, today={}, daysSinceLastReset={}, stackableDays={}",
            //         last, today, daysSinceLastReset, savedConfig.getStackableDays());

            // iterate all saved players so offline players also get handled
            for (String key : savedConfig.getSavedPlayerKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long current = savedConfig.getRemainingMillis(uuid);

                    if (daysSinceLastReset >= savedConfig.getStackableDays()) {
                        // stack window expired → reset to base daily amount (NOT stacked)
                        current = baseMillis;
                        // LOGGER.info("Reset expired for {}, resetting to base {}", uuid, baseMillis);
                    } else {
                        // within stack window → add one day's allotment, capped
                        current = Math.min(cap, current + baseMillis);
                        // LOGGER.info("Stacking for {}, next = {}", uuid, current);
                    }

                    savedConfig.setRemainingMillis(uuid, current);

                    // update runtime map if player currently online
                    if (remainingMillis.containsKey(uuid)) {
                        remainingMillis.put(uuid, current);
                    }
                } catch (IllegalArgumentException ex) {
                    // invalid UUID string — skip
                    // LOGGER.warn("Invalid saved player key: {}", key);
                }
            }

            // mark reset date to today so we don't run again till next calendar boundary
            savedConfig.markReset();
        }

        // --- existing decrement logic (unchanged) ---
        UUID[] keys = remainingMillis.keySet().toArray(new UUID[0]);
        for (UUID uuid : keys) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            long rem = remainingMillis.getOrDefault(uuid, (long) getCountdownSeconds() * 1000L);
            rem -= TICK_MS;

            if (rem <= 0L) {
                player.displayClientMessage(Component.literal(getCountdownSeconds() + " seconds passed"), true);
                rem = (long) getCountdownSeconds() * 1000L; // repeating behaviour
            }

            remainingMillis.put(uuid, rem);
            if (savedConfig != null) savedConfig.setRemainingMillis(uuid, rem);
        }
    }

}
