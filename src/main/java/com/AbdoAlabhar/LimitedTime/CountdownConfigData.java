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
    private boolean isFrozenGlobally= true;
    private String globalTimezone = null;

    // persisted player maps (keys are UUID strings)
    private final Map<String, Long> remainingMap = new HashMap<>();
    private final Map<String, LocalDate> anchorDate = new HashMap<>();

    //-- Get/Set for timer control--
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }
    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));setDirty();
    }
    public long getRemainingMillis(UUID uuid) {
        return computeAndGetRemainingMillis(uuid);
    }
    public long computeAndGetRemainingMillis(UUID uuid) {
        String k = uuid.toString();
        LocalDate today = LocalDate.now(getGlobalTimezone());
        LocalDate anchor = anchorDate.get(k);

        // if no anchor, set it to today and give base
        if (anchor == null) {
            anchorDate.put(k, today);
            setDirty();
            long base = (long) countdownSeconds * 1000L;
            remainingMap.put(k, base);
            setDirty();
            return base;
        }

        long daysSince = ChronoUnit.DAYS.between(anchor, today);
        if (daysSince < 0) daysSince = 0;

        long baseMillis = (long) countdownSeconds * 1000L;
        int stackable = getStackableDays();
        long cap = baseMillis * (long) stackable;

        long cyclesPassed = daysSince / stackable;    // how many full cycles passed
        long remainderDays = daysSince % stackable;   // days into the current cycle (0.stackable-1)

        long current;
        if (cyclesPassed > 0) {
            // advance anchor forward by whole cycles and start current at base (new cycle)
            LocalDate newAnchor = anchor.plusDays(cyclesPassed * (long) stackable);
            anchorDate.put(k, newAnchor);
            setDirty();
            current = baseMillis;
        } else {
            // no full cycles passed — start from stored remaining (or base if missing)
            current = remainingMap.getOrDefault(k, baseMillis);
        }

        // simulate each missed calendar day in order, preserving leftover millis
        for (long i = 0; i < remainderDays; i++) {
            if (current >= cap) {
                // if already at cap, the next day resets the stack to base
                current = baseMillis;
            } else {
                current = Math.min(cap, current + baseMillis);
            }
        }

        // persist and return bounded value
        remainingMap.put(k, Math.min(current, cap));
        setDirty();
        return remainingMap.get(k);
    }

    //-- Get/Set for stackable days
    public int getStackableDays() { return Math.max(1, stackableDays); }
    public void setStackableDays(int days) {
        stackableDays = Math.max(1, days);
        setDirty();
    }

    //-- Get/Set for Timezone
    public void setGlobalTimezone(String zoneId) {
        globalTimezone = zoneId;
        setDirty();
    }
    public LocalDate getAnchorDate(UUID uuid) {
        return anchorDate.get(uuid.toString());
    }
    public ZoneId getGlobalTimezone() {
        if (globalTimezone == null || globalTimezone.isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(globalTimezone);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    //-- Get/Set for Freeze or Unfreeze Time Globally--
    public boolean isFrozenGlobally() {
        return isFrozenGlobally;
    }
    public void setTimeCountingStateGlobally(boolean isFrozenGlobally) {
        this.isFrozenGlobally = isFrozenGlobally;
    }

    //Mark or reset last day joined
    public void markFirstJoin(UUID uuid) {
        String k = uuid.toString();
        if (!anchorDate.containsKey(k)) {
            anchorDate.put(k, LocalDate.now(getGlobalTimezone()));
            setDirty();
        }
    }
    public void markReset(UUID uuid) {
        anchorDate.put(uuid.toString(), LocalDate.now(ZoneId.systemDefault()));
        setDirty();
    }

    //player keys
    public Iterable<String> getSavedPlayerKeys() {
        return remainingMap.keySet();
    }
    public void removePlayer(UUID uuid) {
        String k = uuid.toString();
        remainingMap.remove(k);
        anchorDate.remove(k);
        setDirty();
    }

    // --- NBT saving/loading ---
    @Override
    public CompoundTag save(CompoundTag nbt) {
        nbt.putInt("CountdownSeconds", countdownSeconds);
        nbt.putInt("StackableDays", stackableDays);
        nbt.putBoolean("IsFrozenGlobally", isFrozenGlobally);

        if (globalTimezone != null) nbt.putString("GlobalTimezone", globalTimezone);

        CompoundTag playersTag = new CompoundTag();
        for (Map.Entry<String, Long> e : remainingMap.entrySet()) {
            playersTag.putLong(e.getKey(), e.getValue());
        }
        nbt.put("PlayerRemaining", playersTag);

        CompoundTag anchorTag = new CompoundTag();
        for (Map.Entry<String, LocalDate> e : anchorDate.entrySet()) {
            anchorTag.putString(e.getKey(), e.getValue().toString());
        }
        nbt.put("PlayerAnchorDate", anchorTag);

        return nbt;
    }

    public static CountdownConfigData load(CompoundTag nbt) {
        CountdownConfigData d = new CountdownConfigData();
        if (nbt.contains("GlobalTimezone")) d.globalTimezone = nbt.getString("GlobalTimezone");
        if (nbt.contains("CountdownSeconds")) d.countdownSeconds = nbt.getInt("CountdownSeconds");
        if (nbt.contains("StackableDays")) d.stackableDays = nbt.getInt("StackableDays");
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
        if (nbt.contains("IsFrozenGlobally")){d.isFrozenGlobally = nbt.getBoolean("IsFrozenGlobally");}

        return d;
    }
}
