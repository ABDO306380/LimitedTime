package com.AbdoAlabhar.LimitedTime;

import com.AbdoAlabhar.LimitedTime.RemainingTimePacket;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public class LimitedTimeNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("timelimiter", "main"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                RemainingTimePacket.class,
                RemainingTimePacket::encode,
                RemainingTimePacket::new,
                RemainingTimePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

    }
}
