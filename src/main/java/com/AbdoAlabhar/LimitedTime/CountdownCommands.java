package com.AbdoAlabhar.LimitedTime;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

@Mod.EventBusSubscriber
public class CountdownCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("TimeControl")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("setCountdown")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setCountdownSeconds(seconds);
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Countdown set to " + seconds + " seconds"),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("setStackableDays")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int days = IntegerArgumentType.getInteger(ctx, "days");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setStackableDays(days);
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Stackable Days set to " + days),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("setGlobalTimezone")
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String zone = StringArgumentType.getString(ctx, "zone");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.getConfig().setGlobalTimezone(zone);
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Global timezone set to " + zone),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("resetAllCountdowns")
                                .executes(ctx -> {
                                    TimeNotifier notifier = LimitedTime.getNotifier();
                                    if (notifier != null) {
                                        notifier.resetAllCountdowns();
                                        sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);
                                    }
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("All Timers Reset!"),
                                            true
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("freezeOrUnfreezeTime")
                                .executes(ctx -> {
                                    TimeNotifier notifier = LimitedTime.getNotifier();
                                    if (notifier != null) {
                                        boolean currentlyFrozen = notifier.isFrozenGlobally();
                                        notifier.setFrozenGlobally(!currentlyFrozen);
                                        String msg = currentlyFrozen ? "All Timers Now Ticking!" : "All Timers Now Frozen!";
                                        sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);
                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }

    private static void sendTimeUpdateToAllPlayers(net.minecraft.server.MinecraftServer server, TimeNotifier notifier) {
        if (server == null || notifier == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTimeUpdateToPlayer(player, notifier);
        }
    }

    private static void sendTimeUpdateToPlayer(ServerPlayer player, TimeNotifier notifier) {
        if (player == null || notifier == null) return;

        UUID uuid = player.getUUID();
        long rem = notifier.getRemainingMillis(uuid);
        long baseMillis = (long) notifier.getCountdownSeconds() * 1000L;
        boolean isFrozen = notifier.isFrozenGlobally();
        String tz = notifier.getConfig().getGlobalTimezone().getId();

        LimitedTimeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RemainingTimePacket(uuid, rem, tz, baseMillis, isFrozen)
        );
    }
}