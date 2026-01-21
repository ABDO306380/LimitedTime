package com.AbdoAlabhar.LimitedTime;

import net.minecraft.client.Minecraft;
import java.util.UUID;

/**
 * Client-side time tracking with intelligent pause detection.
 *
 * This class handles the local countdown timer that displays in the overlay.
 * It implements several critical features:
 *
 * 1. Server Synchronization: Receives authoritative time values from the server
 * 2. Local Countdown: Calculates elapsed time to provide smooth countdown
 * 3. Pause Detection: Stops counting when the game is paused (Escape menu)
 * 4. Freeze State: Respects the global freeze state from the server
 */
public class ClientTimeData {
    private static long remainingMillis;
    private static String timezone = "UTC";
    private static long lastUpdateTime = 0;
    private static boolean isFrozen = true;
    private static int baseCountdownSeconds = 3600;

    // Track accumulated time while game was paused
    // This prevents the countdown from jumping when you unpause
    private static long pausedAtSystemTime = 0;
    private static boolean wasPausedLastCheck = false;

    /**
     * Updates client-side time data from a server packet.
     * This is called when the server sends us fresh time information.
     *
     * @param playerId The player's UUID
     * @param millis Remaining time in milliseconds from the server
     * @param tz The timezone string for display
     * @param frozen Whether time counting is globally frozen
     * @param baseSeconds The base countdown duration for progress bar calculations
     */
    public static void update(UUID playerId, long millis, String tz, boolean frozen, int baseSeconds) {
        remainingMillis = millis;
        timezone = tz;
        isFrozen = frozen;
        baseCountdownSeconds = baseSeconds;
        lastUpdateTime = System.currentTimeMillis();

        // Reset pause tracking when we get a fresh server update
        wasPausedLastCheck = false;
        pausedAtSystemTime = 0;
    }

    /**
     * Gets the current remaining time, accounting for elapsed time and pause state.
     *
     * This method is called every frame by the overlay to determine what to display.
     * It implements smart pause detection so the countdown freezes when you press Escape.
     *
     * The logic flow:
     * 1. If globally frozen, return static value (admin frozen the timer)
     * 2. Check if game is currently paused
     * 3. If paused, return the time from when pause started (freeze the display)
     * 4. If not paused but was paused before, adjust our tracking time
     * 5. Calculate and return time minus real elapsed time
     */
    public static long getRemainingMillis() {
        // If the server has frozen time globally, don't count down at all
        if (isFrozen) {
            return remainingMillis;
        }

        // Check if the game is currently paused (Escape menu open in single player)
        Minecraft mc = Minecraft.getInstance();
        boolean isPaused = mc.isPaused();

        if (isPaused) {
            // Game is paused right now
            if (!wasPausedLastCheck) {
                // We just entered pause - record when this happened
                pausedAtSystemTime = System.currentTimeMillis();
                wasPausedLastCheck = true;
            }

            // Return the time as it was when we paused
            // This keeps the display frozen while in the pause menu
            long now = pausedAtSystemTime;
            long elapsed = now - lastUpdateTime;
            return Math.max(0, remainingMillis - elapsed);
        } else {
            // Game is not paused
            if (wasPausedLastCheck) {
                // We just exited pause - adjust our tracking
                // Add the pause duration to lastUpdateTime so we don't count paused time
                long pauseDuration = System.currentTimeMillis() - pausedAtSystemTime;
                lastUpdateTime += pauseDuration;
                wasPausedLastCheck = false;
            }

            // Normal countdown - subtract real elapsed time
            long now = System.currentTimeMillis();
            long elapsed = now - lastUpdateTime;
            return Math.max(0, remainingMillis - elapsed);
        }
    }

    public static String getTimezone() {
        return timezone;
    }

    public static int getBaseCountdownSeconds() {
        return baseCountdownSeconds;
    }

    /**
     * Checks if time is currently frozen (either by pause or global freeze).
     * Useful for displaying different UI states.
     */
    public static boolean isCurrentlyFrozen() {
        Minecraft mc = Minecraft.getInstance();
        return isFrozen || mc.isPaused();
    }
}