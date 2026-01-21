package com.AbdoAlabhar.LimitedTime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CountdownConfigData extends SavedData {

    private int stackableDays = 3;
    private int countdownSeconds = 3600;
    private boolean timeFrozenGlobally = true;
    private String globalTimezone = null;

    // We MUST save this map to NBT to prevent "infinite time" exploits on server restart
    private final Map<String, LocalDate> lastUpdateDate = new HashMap<>();
    private final Map<String, Long> remainingMap = new HashMap<>();
    private final Map<String, LocalDate> anchorDate = new HashMap<>();

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));
        setDirty();
    }

    public long getRemainingMillis(UUID uuid) {
        return computeAndGetRemainingMillis(uuid);
    }

    public long computeAndGetRemainingMillis(UUID uuid) {
        String k = uuid.toString();
        LocalDate today = LocalDate.now(getGlobalTimezone());

        // 1. Initialize New Player (Day 1)
        if (!anchorDate.containsKey(k)) {
            anchorDate.put(k, today);
            lastUpdateDate.put(k, today); // Mark today as "processed"
            long base = (long) countdownSeconds * 1000L;
            remainingMap.put(k, base);
            setDirty();
            return base;
        }

        LocalDate anchor = anchorDate.get(k);
        // If we lost track of last update (e.g. old data), assume it was the anchor date
        LocalDate lastUpdate = lastUpdateDate.getOrDefault(k, anchor);

        long daysFromAnchor = ChronoUnit.DAYS.between(anchor, today);
        int stackable = getStackableDays();
        long baseMillis = (long) countdownSeconds * 1000L;

        // 2. CHECK FOR RESET (Day 4 Logic)
        // If we have passed the stackable limit (e.g. 3 days), RESET everything.
        // Logic: 0 (Day1), 1 (Day2), 2 (Day3). If daysFromAnchor >= 3, it is Day 4+.
        if (daysFromAnchor >= stackable) {
            anchorDate.put(k, today);
            lastUpdateDate.put(k, today);
            remainingMap.put(k, baseMillis); // Reset to X
            setDirty();
            return baseMillis;
        }

        // 3. ACCUMULATION (Day 2 & Day 3 Logic)
        // Only add time if "today" is strictly AFTER the "last update"
        long daysSinceLastLogin = ChronoUnit.DAYS.between(lastUpdate, today);

        if (daysSinceLastLogin > 0) {
            long cap = baseMillis * (long) stackable;
            long current = remainingMap.getOrDefault(k, baseMillis);

            // Add X for every day passed since LAST LOGIN
            // This loop handles if they missed a day (e.g. skip Day 2, join Day 3)
            for (long i = 0; i < daysSinceLastLogin; i++) {
                current = Math.min(cap, current + baseMillis);
            }

            remainingMap.put(k, current);
            lastUpdateDate.put(k, today); // IMPORTANT: Mark today as processed so we don't add again
            setDirty();
        }

        // 4. Return current value (Same Day Logic)
        // If daysSinceLastLogin == 0, we just return the saved map value without adding anything.
        return remainingMap.getOrDefault(k, baseMillis);
    }

    public int getStackableDays() {
        return Math.max(1, stackableDays);
    }

    public void setStackableDays(int days) {
        stackableDays = Math.max(1, days);
        setDirty();
    }

    public void setGlobalTimezone(String zoneId) {
        globalTimezone = zoneId;
        setDirty();
    }

    public LocalDate getAnchorDate(UUID uuid) {
        return anchorDate.get(uuid.toString());
    }

    public ZoneId getGlobalTimezone() {
        if (globalTimezone == null || globalTimezone.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(globalTimezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public boolean isFrozenGlobally() {
        return timeFrozenGlobally;
    }

    public void setTimeCountingStateGlobally(boolean frozen) {
        this.timeFrozenGlobally = frozen;
        setDirty();
    }

    public void markFirstJoin(UUID uuid) {
        // This is handled inside computeAndGetRemainingMillis now,
        // but we keep this for compatibility if TimeManager calls it.
        String k = uuid.toString();
        if (!anchorDate.containsKey(k)) {
            LocalDate today = LocalDate.now(getGlobalTimezone());
            anchorDate.put(k, today);
            lastUpdateDate.put(k, today);
            setDirty();
        }
    }

    public void markReset(UUID uuid) {
        String k = uuid.toString();
        LocalDate today = LocalDate.now(getGlobalTimezone());
        anchorDate.put(k, today);
        lastUpdateDate.put(k, today);
        setDirty();
    }

    public Iterable<String> getSavedPlayerKeys() {
        return remainingMap.keySet();
    }

    public void removePlayer(UUID uuid) {
        String k = uuid.toString();
        remainingMap.remove(k);
        anchorDate.remove(k);
        lastUpdateDate.remove(k);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);
        nbt.putInt("StackableDays", stackableDays);
        nbt.putBoolean("IsFrozenGlobally", timeFrozenGlobally);

        if (globalTimezone != null) {
            nbt.putString("GlobalTimezone", globalTimezone);
        }

        // Save Remaining Time
        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<String, Long> e : remainingMap.entrySet()) {
            playersTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("PlayerRemaining", playersTag);

        // Save Anchor Dates
        CompoundTag anchorTag = new CompoundTag();
        for (Map.Entry<String, LocalDate> e : anchorDate.entrySet()) {
            anchorTag.putString(e.getKey(), e.getValue().toString());
        }
        nbt.put("PlayerAnchorDate", anchorTag);

        // Save Last Update Dates (CRITICAL FIX)
        CompoundTag lastUpdateTag = new CompoundTag();
        for (Map.Entry<String, LocalDate> e : lastUpdateDate.entrySet()) {
            lastUpdateTag.putString(e.getKey(), e.getValue().toString());
        }
        nbt.put("PlayerLastUpdateDate", lastUpdateTag);

        return nbt;
    }

    public static CountdownConfigData load(CompoundTag nbt) {
        CountdownConfigData d = new CountdownConfigData();

        if (nbt.contains("GlobalTimezone")) {
            d.globalTimezone = nbt.getString("GlobalTimezone");
        }

        if (nbt.contains("CountdownSeconds")) {
            d.countdownSeconds = nbt.getInt("CountdownSeconds");
        }

        if (nbt.contains("StackableDays")) {
            d.stackableDays = nbt.getInt("StackableDays");
        }

        if (nbt.contains("IsFrozenGlobally")) {
            d.timeFrozenGlobally = nbt.getBoolean("IsFrozenGlobally");
        }

        if (nbt.contains("PlayerRemaining")) {
            CompoundTag playersTag = nbt.getCompound("PlayerRemaining");
            for (String key : playersTag.getAllKeys()) {
                d.remainingMap.put(key, playersTag.getLong(key));
            }
        }

        if (nbt.contains("PlayerAnchorDate")) {
            CompoundTag anchorTag = nbt.getCompound("PlayerAnchorDate");
            for (String key : anchorTag.getAllKeys()) {
                String s = anchorTag.getString(key);
                if (s != null && !s.isEmpty()) {
                    d.anchorDate.put(key, LocalDate.parse(s));
                }
            }
        }

        // Load Last Update Dates (CRITICAL FIX)
        if (nbt.contains("PlayerLastUpdateDate")) {
            CompoundTag lastUpdateTag = nbt.getCompound("PlayerLastUpdateDate");
            for (String key : lastUpdateTag.getAllKeys()) {
                String s = lastUpdateTag.getString(key);
                if (s != null && !s.isEmpty()) {
                    d.lastUpdateDate.put(key, LocalDate.parse(s));
                }
            }
        }

        return d;
    }
}