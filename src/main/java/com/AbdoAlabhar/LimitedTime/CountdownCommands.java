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
                        .requires(source -> source.hasPermission(2)) // OPs only
                        //setCountdown
                        .then(Commands.literal("setCountdown")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setCountdownSeconds(seconds);

                                            // Send update to all online players
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Countdown set to " + seconds + " seconds"),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        //setStackableDays
                        .then(Commands.literal("setStackableDays")
                                .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int days = IntegerArgumentType.getInteger(ctx, "days");
                                            TimeNotifier notifier = LimitedTime.getNotifier();
                                            notifier.setStackableDays(days);

                                            // Send update to all online players
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Stackable Days set to " + days),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        //setGlobalTimezone
                        .then(Commands.literal("setGlobalTimezone")
                                .then(Commands.argument("zone", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String zone = StringArgumentType.getString(ctx, "zone");
                                            CountdownConfigData data = LimitedTime.getNotifier().savedConfig;
                                            data.setGlobalTimezone(zone);

                                            // Send update to all online players
                                            sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), LimitedTime.getNotifier());

                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("Global timezone set to " + zone),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        //Reset All countdowns
                        .then(Commands.literal("resetAllCountdowns")
                                .executes(ctx -> {
                                    TimeNotifier notifier = LimitedTime.getNotifier();
                                    if (notifier != null) {
                                        notifier.resetAllCountdowns();

                                        // Send update to all online players
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
                                // Global toggle
                                .executes(ctx -> {
                                    TimeNotifier notifier = LimitedTime.getNotifier();
                                    if (notifier != null) {
                                        boolean currentlyFrozen = notifier.isFrozenGlobally();
                                        notifier.setTimestate(!currentlyFrozen);
                                        String msg = !currentlyFrozen ? "All Timers Now Frozen!" : "All Timers Now Ticking!";

                                        // Send update to all online players with new frozen state
                                        sendTimeUpdateToAllPlayers(ctx.getSource().getServer(), notifier);

                                        ctx.getSource().sendSuccess(() -> Component.literal(msg), true);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        );
    }

    // Helper method to send time updates to all online players
    private static void sendTimeUpdateToAllPlayers(net.minecraft.server.MinecraftServer server, TimeNotifier notifier) {
        if (server == null || notifier == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            long rem = notifier.getRemainingMillis(uuid);
            long baseMillis = (long) notifier.getCountdownSeconds() * 1000L;
            boolean isFrozen = notifier.isFrozenGlobally();

            LimitedTimeNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new RemainingTimePacket(uuid, rem, notifier.savedConfig.getGlobalTimezone().toString(), baseMillis, isFrozen)
            );
        }
    }

    // Optional: Method to send update to a specific player
    private static void sendTimeUpdateToPlayer(ServerPlayer player, TimeNotifier notifier) {
        if (player == null || notifier == null) return;

        UUID uuid = player.getUUID();
        long rem = notifier.getRemainingMillis(uuid);
        long baseMillis = (long) notifier.getCountdownSeconds() * 1000L;
        boolean isFrozen = notifier.isFrozenGlobally();

        LimitedTimeNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new RemainingTimePacket(uuid, rem, notifier.savedConfig.getGlobalTimezone().toString(), baseMillis, isFrozen)
        );
    }
}