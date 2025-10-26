package com.AbdoAlabhar.LimitedTime;

import java.util.UUID;

public class ClientTimeData {

    private static long remainingMillis;
    private static String timezone = "UTC";

    public static void update(UUID playerId, long millis, String tz) {
        // Only update for local player
        remainingMillis = millis;
        timezone = tz;
    }

    public static long getRemainingMillis() {
        return remainingMillis;
    }

    public static String getTimezone() {
        return timezone;
    }
}
