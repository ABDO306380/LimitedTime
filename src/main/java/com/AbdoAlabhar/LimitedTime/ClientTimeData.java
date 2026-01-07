package com.AbdoAlabhar.LimitedTime;

import java.util.UUID;

public class ClientTimeData {
    private static long remainingMillis;
    private static String timezone = "UTC";
    private static long baseMillis = 3600 * 1000L;
    private static boolean isFrozen = false;

    public static synchronized void updateFromServer(UUID playerId, long serverMillis, String tz, long base, boolean frozen) {
        if (remainingMillis <= 0) {
            remainingMillis = serverMillis;
        } else {
            remainingMillis = Math.min(remainingMillis, serverMillis);
        }

        timezone = (tz == null || tz.isEmpty()) ? "UTC" : tz;
        baseMillis = base > 0 ? base : 3600 * 1000L;
        isFrozen = frozen;

        ClientOverlay.syncWithServer(remainingMillis, timezone, baseMillis, frozen);
    }

    public static void saveLocalTime(long millis) {
        remainingMillis = millis;
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

    public static boolean isFrozen() {
        return isFrozen;
    }

    public static void reset() {
        remainingMillis = 0;
    }
}