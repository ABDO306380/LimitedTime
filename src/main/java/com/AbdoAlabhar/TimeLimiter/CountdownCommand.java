package com.AbdoAlabhar.TimeLimiter;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
    }
}
