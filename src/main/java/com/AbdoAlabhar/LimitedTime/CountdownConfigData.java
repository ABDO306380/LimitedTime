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

    private int stackableDays = 3; // max stackable days
    private int countdownSeconds = 3600; // base daily playtime in


    // persisted player maps (keys are UUID strings)
    private final Map<String, Long> remainingMap = new HashMap<>(); // uuid -> remainingMillis
    private final Map<String, LocalDate> anchorDate = new HashMap<>(); // uuid -> first-join or cycle-anchor date
    private String globalTimezone = null; // null = server default

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

    public CountdownConfigData() {}

    // --- getters / setters for global config ---
    public int getCountdownSeconds() { return countdownSeconds; }
    public void setCountdownSeconds(int seconds) {
        countdownSeconds = Math.max(1, seconds);
        setDirty();
    }

    public int getStackableDays() { return Math.max(1, stackableDays); }
    public void setStackableDays(int days) {
        stackableDays = Math.max(1, days);
        setDirty();
    }

    // --- anchor / first-join helpers ---
    /** Ensure a player has an anchor date (first join). Does not override an existing anchor. */
    public void markFirstJoin(UUID uuid) {
        String k = uuid.toString();
        if (!anchorDate.containsKey(k)) {
            anchorDate.put(k, LocalDate.now(getGlobalTimezone()));
            setDirty();
        }
    }

    /** Force set anchor to today (used when admin/cycle reset is desired). */
    public void markReset(UUID uuid) {
        anchorDate.put(uuid.toString(), LocalDate.now(ZoneId.systemDefault()));
        setDirty();
    }

    public LocalDate getAnchorDate(UUID uuid) {
        return anchorDate.get(uuid.toString());
    }

    // --- remaining millis accessors ---
    /**
     * Compute remaining millis for uuid based on anchor date and stack logic, persist it and return value.
     * This is the canonical place where calendar-based stacking is applied.
     *
     * Important: this now auto-advances the anchor by whole cycles (stackableDays) if full cycles passed,
     * so the anchor doesn't drift and daysSince stays within [0, stackableDays-1].
     */
    /**
     * Canonical calendar-based recompute that:
     *  - auto-advances anchor by full cycles
     *  - applies day-by-day additions to the player's existing remaining millis
     *  - resets to base when a full cycle completes
     */
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

    /**
     * Get remaining millis (always recomputes based on calendar to be server/offline-safe).
     * Use this where you need authoritative remaining time.
     */
    public long getRemainingMillis(UUID uuid) {
        return computeAndGetRemainingMillis(uuid);
    }

    public void setRemainingMillis(UUID uuid, long millis) {
        remainingMap.put(uuid.toString(), Math.max(0L, millis));
        setDirty();
    }

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

        return d;
    }
}
