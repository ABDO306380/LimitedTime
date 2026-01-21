package com.AbdoAlabhar.LimitedTime;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class CountdownCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("timeControl")
                        .requires(source -> source.hasPermission(2)) // OPs only

                        // Set the base countdown duration in seconds
                        .then(Commands.literal("setCountdown")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            TimeManager notifier = LimitedTime.getNotifier();

                                            if (notifier == null) {
                                                ctx.getSource().sendFailure(Component.literal("TimeManager not initialized!"));
                                                return 0;
                                            }

                                            notifier.setCountdownSeconds(seconds);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Countdown set to " + seconds + " seconds. All players notified."),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // Set the maximum stackable days before reset
                        .then(Commands.literal("setStackableDays")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int days = IntegerArgumentType.getInteger(ctx, "days");
                                            TimeManager notifier = LimitedTime.getNotifier();

                                            if (notifier == null) {
                                                ctx.getSource().sendFailure(Component.literal("TimeManager not initialized!"));
                                                return 0;
                                            }

                                            notifier.setStackableDays(days);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Stackable Days set to " + days + ". All players notified."),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // Set the global timezone for time calculations
                        // CRITICAL FIX: Now goes through TimeManager instead of direct config access
                        .then(Commands.literal("setGlobalTimezone")
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String zone = StringArgumentType.getString(ctx, "zone");
                                            TimeManager notifier = LimitedTime.getNotifier();

                                            if (notifier == null) {
                                                ctx.getSource().sendFailure(Component.literal("TimeManager not initialized!"));
                                                return 0;
                                            }

                                            // Use TimeManager method instead of direct config access
                                            // This ensures clients get notified of the change
                                            notifier.setGlobalTimezone(zone);

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Global timezone set to " + zone + ". All players notified."),
                                                    true
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )

                        // Reset all player countdowns to base time
                        .then(Commands.literal("resetAllCountdowns")
                                .executes(ctx -> {
                                    TimeManager notifier = LimitedTime.getNotifier();

                                    if (notifier == null) {
                                        ctx.getSource().sendFailure(Component.literal("TimeManager not initialized!"));
                                        return 0;
                                    }

                                    notifier.resetAllCountdowns();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("All Timers Reset! All players notified."),
                                            true
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                        )

                        // Toggle global freeze/unfreeze state
                        .then(Commands.literal("freezeOrUnfreezeTime")
                                .executes(ctx -> {
                                    TimeManager notifier = LimitedTime.getNotifier();

                                    if (notifier == null) {
                                        ctx.getSource().sendFailure(Component.literal("TimeManager not initialized!"));
                                        return 0;
                                    }

                                    boolean currentlyFrozen = notifier.isFrozenGlobally();
                                    notifier.setTimestate(!currentlyFrozen);

                                    String msg = !currentlyFrozen
                                            ? "All Timers Frozen! Players notified."
                                            : "All Timers Now Ticking! Players notified.";

                                    ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }
}