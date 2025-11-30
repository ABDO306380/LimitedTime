package com.AbdoAlabhar.LimitedTime;

import java.util.UUID;

public class ClientTimeData {
    private static long remainingMillis;
    private static String timezone = "UTC";
    private static long baseMillis = 3600 * 1000L;
    private static boolean isFrozen = false; // NEW: Track frozen state

    public static void update(UUID playerId, long millis, String tz, long base, boolean frozen) {
        remainingMillis = millis;
        timezone = tz;
        baseMillis = base;
        isFrozen = frozen; // NEW

        // Sync with the overlay
        ClientOverlay.setInitialTime(millis, tz, base, frozen);
    }

    public static long getRemainingMillis() {
        return remainingMillis;
    }

    public static String getTimezone() {
        return timezone;
    }

    public static long getBaseMillis() {
        return baseMillis;
    }

    // NEW: Get frozen state
    public static boolean isFrozen() {
        return isFrozen;
    }
}