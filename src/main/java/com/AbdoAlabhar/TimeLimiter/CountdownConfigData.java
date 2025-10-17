package com.AbdoAlabhar.TimeLimiter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CountdownConfigData extends SavedData {
    private int countdownSeconds = 10; // default
    private final Map<String, Long> remainingMap = new HashMap<>(); // uuidStr -> remainingMillis

    public CountdownConfigData() {}

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

    public void removePlayer(UUID uuid) {
        remainingMap.remove(uuid.toString());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);

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
