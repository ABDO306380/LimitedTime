package com.AbdoAlabhar.TimeLimiter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CountdownConfigData extends SavedData {

    private int countdownSeconds = 600; // default playtime in seconds
    private final Map<String, Long> remainingMap = new HashMap<>(); // uuidStr -> remainingMillis
    private long lastResetTime = 0; // timestamp of last reset in millis

    public CountdownConfigData() {}

    // --- Playtime getters/setters ---
    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }

    public long getRemainingMillis(UUID uuid) {
        return remainingMap.getOrDefault(uuid.toString(), (long) getCountdownSeconds() * 1000L);
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));
        setDirty();
    }

    // --- Stackable time helpers ---
    public void addRemainingMillis(UUID uuid, long extraMillis) {
        long current = getRemainingMillis(uuid);
        setRemainingMillis(uuid, current + extraMillis);
    }

    public void reduceRemainingMillis(UUID uuid, long millis) {
        long current = getRemainingMillis(uuid);
        setRemainingMillis(uuid, Math.max(0L, current - millis));
    }
    public void resetRemainingMillis(UUID uuid) {
        setRemainingMillis(uuid, (long) getCountdownSeconds() * 1000L);
    }

    // --- Reset tracking ---
    public boolean shouldReset(long currentTimeMillis) {
        // if 2 days have passed since last reset
        return currentTimeMillis - lastResetTime >= 2L * 24 * 60 * 60 * 1000;
    }

    public void markReset(long currentTimeMillis) {
        lastResetTime = currentTimeMillis;
        setDirty();
    }

    public void removePlayer(UUID uuid) {
        remainingMap.remove(uuid.toString());
        setDirty();
    }

    // --- NBT saving/loading ---
    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);
        nbt.putLong("LastResetTime", lastResetTime);

        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<String, Long> e : remainingMap.entrySet()) {
            playersTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("PlayerRemaining", playersTag);
        return nbt;
    }

    public static CountdownConfigData load(CompoundTag nbt) {
        CountdownConfigData d = new CountdownConfigData();
        if (nbt.contains("CountdownSeconds")) d.countdownSeconds = nbt.getInt("CountdownSeconds");
        if (nbt.contains("LastResetTime")) d.lastResetTime = nbt.getLong("LastResetTime");
        if (nbt.contains("PlayerRemaining")) {
            CompoundTag playersTag = nbt.getCompound("PlayerRemaining");
            for (String key : playersTag.getAllKeys()) {
                long val = playersTag.getLong(key);
                d.remainingMap.put(key, val);
            }
        }
        return d;
    }
}
