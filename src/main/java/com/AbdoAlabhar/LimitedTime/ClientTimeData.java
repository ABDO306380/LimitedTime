package com.AbdoAlabhar.LimitedTime;

import java.util.UUID;

public class ClientTimeData {
    private static long remainingMillis;
    private static String timezone = "UTC";
    private static long baseMillis = 3600 * 1000L;

    public static void update(UUID playerId, long millis, String tz, long base) {
        remainingMillis = millis;
        timezone = tz;
        baseMillis = base;
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
}