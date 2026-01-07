package com.AbdoAlabhar.LimitedTime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CountdownConfigData extends SavedData {
    private int stackableDays = 3;  // Max days to accumulate
    private int countdownSeconds = 3600;  // Base daily time (1 hour)
    private boolean isFrozenGlobally = true;
    private String globalTimezone = null;

    private final Map<String, Long> remainingMap = new HashMap<>();
    private final Map<String, LocalDate> lastCheckDate = new HashMap<>();
    private final Map<String, Long> accumulatedTime = new HashMap<>();  // Total accumulated time including today

    public int getCountdownSeconds() { return countdownSeconds; }

    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));
        setDirty();
    }

    public long getRemainingMillis(UUID uuid) {
        return computeAccumulatedRemainingMillis(uuid);
    }

    private long computeAccumulatedRemainingMillis(UUID uuid) {
        String key = uuid.toString();
        ZonedDateTime now = ZonedDateTime.now(getGlobalTimezone());
        LocalDate today = now.toLocalDate();

        long baseMillis = (long) countdownSeconds * 1000L;
        long maxAccumulatedMillis = baseMillis * (long) stackableDays;

        if (!lastCheckDate.containsKey(key)) {
            lastCheckDate.put(key, today);
            accumulatedTime.put(key, baseMillis);  // Start with base time
            remainingMap.put(key, baseMillis);
            setDirty();
            return baseMillis;
        }

        LocalDate lastDate = lastCheckDate.get(key);
        long currentAccumulated = accumulatedTime.getOrDefault(key, baseMillis);
        long currentRemaining = remainingMap.getOrDefault(key, baseMillis);

        if (lastDate.isBefore(today)) {
            long daysPassed = ChronoUnit.DAYS.between(lastDate, today);

            for (int i = 0; i < daysPassed; i++) {
                long unusedFromPrevious = Math.max(0, currentRemaining);

                currentAccumulated = Math.min(maxAccumulatedMillis, currentAccumulated + baseMillis);

                currentRemaining = Math.min(currentAccumulated, currentRemaining);
            }

            lastCheckDate.put(key, today);
        }

        currentRemaining = Math.min(currentRemaining, currentAccumulated);

        // Update stored values
        accumulatedTime.put(key, currentAccumulated);
        remainingMap.put(key, currentRemaining);
        setDirty();

        return currentRemaining;
    }

    public void deductTime(UUID uuid, long millisToDeduct) {
        String key = uuid.toString();
        long currentRemaining = remainingMap.getOrDefault(key, (long) countdownSeconds * 1000L);
        long newRemaining = Math.max(0, currentRemaining - millisToDeduct);
        remainingMap.put(key, newRemaining);
        setDirty();
    }

    public int getStackableDays() { return Math.max(1, stackableDays); }

    public void setStackableDays(int days) {
        stackableDays = Math.max(1, days);
        setDirty();
    }

    public void setGlobalTimezone(String zoneId) {
        globalTimezone = zoneId;
        setDirty();
    }

    public ZoneId getGlobalTimezone() {
        if (globalTimezone == null || globalTimezone.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(globalTimezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public boolean isFrozenGlobally() {
        return isFrozenGlobally;
    }

    public void setTimeCountingStateGlobally(boolean frozen) {
        this.isFrozenGlobally = frozen;
        setDirty();
    }

    public void markFirstJoin(UUID uuid) {
        String key = uuid.toString();
        if (!lastCheckDate.containsKey(key)) {
            ZonedDateTime now = ZonedDateTime.now(getGlobalTimezone());
            lastCheckDate.put(key, now.toLocalDate());
            long baseMillis = (long) countdownSeconds * 1000L;
            accumulatedTime.put(key, baseMillis);
            remainingMap.put(key, baseMillis);
            setDirty();
        }
    }

    public void resetPlayer(UUID uuid) {
        String key = uuid.toString();
        ZonedDateTime now = ZonedDateTime.now(getGlobalTimezone());
        lastCheckDate.put(key, now.toLocalDate());
        long baseMillis = (long) countdownSeconds * 1000L;
        accumulatedTime.put(key, baseMillis);
        remainingMap.put(key, baseMillis);
        setDirty();
    }

    public long getAccumulatedMillis(UUID uuid) {
        return accumulatedTime.getOrDefault(uuid.toString(), (long) countdownSeconds * 1000L);
    }

    public long getMaxPossibleMillis(UUID uuid) {
        return (long) countdownSeconds * 1000L * (long) stackableDays;
    }

    public Iterable<String> getSavedPlayerKeys() {
        return remainingMap.keySet();
    }

    public void removePlayer(UUID uuid) {
        String key = uuid.toString();
        remainingMap.remove(key);
        lastCheckDate.remove(key);
        accumulatedTime.remove(key);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);
        nbt.putInt("StackableDays", stackableDays);
        nbt.putBoolean("IsFrozenGlobally", isFrozenGlobally);
        if (globalTimezone != null) nbt.putString("GlobalTimezone", globalTimezone);

        CompoundTag remainingTag = new CompoundTag();
        for (Map.Entry<String, Long> e : remainingMap.entrySet()) {
            remainingTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("PlayerRemaining", remainingTag);

        CompoundTag dateTag = new CompoundTag();
        for (Map.Entry<String, LocalDate> e : lastCheckDate.entrySet()) {
            dateTag.putString(e.getKey(), e.getValue().toString());
        }
        nbt.put("LastCheckDate", dateTag);

        CompoundTag accumulatedTag = new CompoundTag();
        for (Map.Entry<String, Long> e : accumulatedTime.entrySet()) {
            accumulatedTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("AccumulatedTime", accumulatedTag);

        return nbt;
    }

    public static CountdownConfigData load(CompoundTag nbt) {
        CountdownConfigData data = new CountdownConfigData();

        if (nbt.contains("GlobalTimezone")) data.globalTimezone = nbt.getString("GlobalTimezone");
        if (nbt.contains("CountdownSeconds")) data.countdownSeconds = nbt.getInt("CountdownSeconds");
        if (nbt.contains("StackableDays")) data.stackableDays = nbt.getInt("StackableDays");
        if (nbt.contains("IsFrozenGlobally")) data.isFrozenGlobally = nbt.getBoolean("IsFrozenGlobally");

        if (nbt.contains("PlayerRemaining")) {
            CompoundTag remainingTag = nbt.getCompound("PlayerRemaining");
            for (String key : remainingTag.getAllKeys()) {
                data.remainingMap.put(key, remainingTag.getLong(key));
            }
        }

        if (nbt.contains("LastCheckDate")) {
            CompoundTag dateTag = nbt.getCompound("LastCheckDate");
            for (String key : dateTag.getAllKeys()) {
                String dateStr = dateTag.getString(key);
                if (dateStr != null && !dateStr.isEmpty()) {
                    data.lastCheckDate.put(key, LocalDate.parse(dateStr));
                }
            }
        }

        if (nbt.contains("AccumulatedTime")) {
            CompoundTag accumulatedTag = nbt.getCompound("AccumulatedTime");
            for (String key : accumulatedTag.getAllKeys()) {
                data.accumulatedTime.put(key, accumulatedTag.getLong(key));
            }
        }

        return data;
    }
}