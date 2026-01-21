package com.AbdoAlabhar.LimitedTime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Manages custom game rules with instant change detection.
 *
 * This class uses Minecraft's built-in game rule callback system to detect
 * changes the moment they happen, whether from /gamerule commands or during
 * world creation. This provides instant synchronization with zero polling delay.
 */
public class TimeLimiterGameRules {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Game rule keys
    public static GameRules.Key<GameRules.IntegerValue> BASE_SECONDS;
    public static GameRules.Key<GameRules.IntegerValue> STACKABLE_DAYS;
    public static GameRules.Key<GameRules.BooleanValue> TIME_FROZEN;

    /**
     * Registers all custom game rules with instant change detection callbacks.
     *
     * The callbacks we register here will fire immediately when any game rule changes,
     * whether that change comes from /gamerule commands, world creation screen,
     * or programmatic changes. This eliminates any delay in synchronization.
     *
     * Note: These callbacks fire even before TimeManager exists (like during world
     * creation), so they need to safely handle the case where TimeManager is null.
     */
    public static void register() {
        LOGGER.info("Registering TimeLimiter game rules with instant change detection...");

        // Register base countdown seconds with a change callback
        // The callback receives the MinecraftServer instance, which we use to find our TimeManager
        BASE_SECONDS = GameRules.register(
                "timeLimiterBaseSeconds",
                GameRules.Category.PLAYER,
                GameRules.IntegerValue.create(
                        3600,  // Default value: 1 hour
                        // This callback fires INSTANTLY when the game rule changes
                        (server, intValue) -> {
                            int newValue = intValue.get();
                            LOGGER.info("Game rule 'timeLimiterBaseSeconds' changed to: {}", newValue);

                            // Notify TimeManager if it exists
                            TimeManager manager = LimitedTime.getNotifier();
                            if (manager != null) {
                                manager.onGameRuleChanged_BaseSeconds(newValue);
                            } else {
                                LOGGER.debug("TimeManager not yet initialized, change will be picked up on init");
                            }
                        }
                )
        );

        // Register stackable days with a change callback
        STACKABLE_DAYS = GameRules.register(
                "timeLimiterStackableDays",
                GameRules.Category.PLAYER,
                GameRules.IntegerValue.create(
                        3,  // Default value: 3 days
                        (server, intValue) -> {
                            int newValue = intValue.get();
                            LOGGER.info("Game rule 'timeLimiterStackableDays' changed to: {}", newValue);

                            TimeManager manager = LimitedTime.getNotifier();
                            if (manager != null) {
                                manager.onGameRuleChanged_StackableDays(newValue);
                            } else {
                                LOGGER.debug("TimeManager not yet initialized, change will be picked up on init");
                            }
                        }
                )
        );

        // Register freeze state with a change callback
        TIME_FROZEN = GameRules.register(
                "timeLimiterFrozen",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(
                        true,  // Default: frozen for safety
                        (server, boolValue) -> {
                            boolean newValue = boolValue.get();
                            LOGGER.info("Game rule 'timeLimiterFrozen' changed to: {}", newValue);

                            TimeManager manager = LimitedTime.getNotifier();
                            if (manager != null) {
                                manager.onGameRuleChanged_Frozen(newValue);
                            } else {
                                LOGGER.debug("TimeManager not yet initialized, change will be picked up on init");
                            }
                        }
                )
        );

        LOGGER.info("TimeLimiter game rules registered successfully with instant callbacks");
    }

    // ==================== Getter Methods ====================

    /**
     * Gets the current base seconds value from game rules.
     * This reads the actual game rule value directly, so it's always current.
     */
    public static int getBaseSeconds(GameRules gameRules) {
        if (BASE_SECONDS == null) {
            LOGGER.warn("BASE_SECONDS game rule not initialized, returning default");
            return 3600;
        }
        return gameRules.getInt(BASE_SECONDS);
    }

    /**
     * Gets the current stackable days value from game rules.
     */
    public static int getStackableDays(GameRules gameRules) {
        if (STACKABLE_DAYS == null) {
            LOGGER.warn("STACKABLE_DAYS game rule not initialized, returning default");
            return 3;
        }
        return gameRules.getInt(STACKABLE_DAYS);
    }

    /**
     * Gets the current frozen state from game rules.
     */
    public static boolean isFrozen(GameRules gameRules) {
        if (TIME_FROZEN == null) {
            LOGGER.warn("TIME_FROZEN game rule not initialized, returning default");
            return true;
        }
        return gameRules.getBoolean(TIME_FROZEN);
    }

    // ==================== Setter Methods ====================

    /**
     * Sets the base seconds game rule.
     *
     * Important: When you call this method, it will trigger the callback we registered
     * above, which means the TimeManager will be instantly notified of the change.
     * You don't need to manually call any sync methods - it all happens automatically.
     */
    public static void setBaseSeconds(GameRules gameRules, int seconds) {
        if (BASE_SECONDS == null) {
            LOGGER.error("Cannot set BASE_SECONDS - game rule not initialized");
            return;
        }
        if (seconds < 1) {
            LOGGER.warn("Attempted to set invalid base seconds: {}, clamping to 1", seconds);
            seconds = 1;
        }
        // This set() call will automatically trigger our callback above
        gameRules.getRule(BASE_SECONDS).set(seconds, null);
    }

    /**
     * Sets the stackable days game rule.
     * Automatically triggers the change callback for instant synchronization.
     */
    public static void setStackableDays(GameRules gameRules, int days) {
        if (STACKABLE_DAYS == null) {
            LOGGER.error("Cannot set STACKABLE_DAYS - game rule not initialized");
            return;
        }
        if (days < 1) {
            LOGGER.warn("Attempted to set invalid stackable days: {}, clamping to 1", days);
            days = 1;
        }
        gameRules.getRule(STACKABLE_DAYS).set(days, null);
    }

    /**
     * Sets the frozen state game rule.
     * Automatically triggers the change callback for instant synchronization.
     */
    public static void setFrozen(GameRules gameRules, boolean frozen) {
        if (TIME_FROZEN == null) {
            LOGGER.error("Cannot set TIME_FROZEN - game rule not initialized");
            return;
        }
        gameRules.getRule(TIME_FROZEN).set(frozen, null);
    }
}