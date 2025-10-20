package com.AbdoAlabhar.TimeLimiter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CountdownConfigData extends SavedData {

    private int stackableDays = 3;                 // persisted stackable days (was StackableDays)
    private int countdownSeconds = 3600;          // default playtime in seconds
    private final Map<String, Long> remainingMap = new HashMap<>(); // uuidStr -> remainingMillis
    private LocalDate lastResetDate = null;       // last reset date

    public CountdownConfigData() {}

    // --- Playtime getters/setters ---
    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }

    // stackableDays getter/setter
    public int getStackableDays() {
        return Math.max(1, stackableDays);
    }

    public void setStackableDays(int days) {
        stackableDays = Math.max(1, days);
        setDirty();
    }

    public long getRemainingMillis(UUID uuid) {
        return remainingMap.getOrDefault(uuid.toString(), (long) getCountdownSeconds() * 1000L);
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));
        setDirty();
    }

    public void addRemainingMillis(UUID uuid, long extraMillis) {
        long current = getRemainingMillis(uuid);
        setRemainingMillis(uuid, current + extraMillis);
    }

    // --- Calendar-based reset helpers ---
    public boolean shouldReset() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (lastResetDate == null) {
            // initialize to today (avoid immediate reset on first load)
            lastResetDate = today;
            setDirty();
            return false;
        }
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(lastResetDate, today);
        return daysBetween >= 1; // run reset once per calendar day boundary (adjust if you want >=2)
    }

    public void markReset() {
        lastResetDate = LocalDate.now(ZoneId.systemDefault());
        setDirty();
    }

    public LocalDate getLastResetDate() {
        return lastResetDate;
    }

    public void removePlayer(UUID uuid) {
        remainingMap.remove(uuid.toString());
        setDirty();
    }

    public Iterable<String> getSavedPlayerKeys() {
        return remainingMap.keySet();
    }
    // --- NBT saving/loading ---
    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);
        nbt.putInt("StackableDays", stackableDays);
        if (lastResetDate != null) nbt.putString("LastResetDate", lastResetDate.toString());

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
        if (nbt.contains("StackableDays")) d.stackableDays = nbt.getInt("StackableDays");
        if (nbt.contains("LastResetDate")) d.lastResetDate = LocalDate.parse(nbt.getString("LastResetDate"));
        // if LastResetDate missing we leave it null so shouldReset() can initialize or you can set default here

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
