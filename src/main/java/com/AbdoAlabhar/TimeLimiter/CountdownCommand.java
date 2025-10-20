package com.AbdoAlabhar.TimeLimiter;

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
                Commands.literal("setcountdown")
                        .requires(source -> source.hasPermission(2)) // OPs only
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");

                                    // Update the countdown
                                    TimeNotifier notifier = TimeLimiter.getNotifier();
                                    notifier.setCountdownSeconds(seconds);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Countdown set to " + seconds + " seconds"),
                                            true
                                    );

                                    return 1;
                                })
                        )
        );
        dispatcher.register(
                Commands.literal("setStackableDays")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int days = IntegerArgumentType.getInteger(ctx, "days");
                                    TimeNotifier notifier = TimeLimiter.getNotifier();
                                    if (notifier != null) {
                                        notifier.setStackableDays(days); // you need to implement this in TimeNotifier
                                    }
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Stackable Days set to " + days),
                                            true
                                    );
                                    return 1;
                                })
                        )
        );
        dispatcher.register(
                Commands.literal("setGlobalTimezone")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("zone", StringArgumentType.string())
                                .executes(ctx -> {
                                    String zone = StringArgumentType.getString(ctx, "zone");
                                    CountdownConfigData data = TimeLimiter.getNotifier().savedConfig;
                                    data.setGlobalTimezone(zone);

                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Global timezone set to " + zone),
                                            true
                                    );
                                    return 1;
                                })
                        )
        );

    }
}
