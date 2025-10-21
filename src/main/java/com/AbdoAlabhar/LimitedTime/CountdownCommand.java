package com.AbdoAlabhar.LimitedTime;

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
public class CountdownCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("TimeControl") // Root command
                        .requires(source -> source.hasPermission(2)) // OPs only
                        // Subcommand: setcountdown
                        .then(Commands.literal("setcountdown")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setCountdownSeconds(seconds);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Countdown set to " + seconds + " seconds"),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        // Subcommand: setStackableDays
                        .then(Commands.literal("setStackableDays")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int days = IntegerArgumentType.getInteger(ctx, "days");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setStackableDays(days);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Stackable Days set to " + days),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        // Subcommand: setGlobalTimezone
                        .then(Commands.literal("setGlobalTimezone")
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String zone = StringArgumentType.getString(ctx, "zone");
                                            CountdownConfigData data = LimitedTime.getNotifier().savedConfig;
                                            data.setGlobalTimezone(zone);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Global timezone set to " + zone),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
        );

    }
}
