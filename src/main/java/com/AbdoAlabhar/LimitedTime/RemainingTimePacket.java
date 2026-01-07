package com.AbdoAlabhar.LimitedTime;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemainingTimePacket {
    private final UUID playerUUID;
    private final long remainingMillis;
    private final String timezone;
    private final long baseMillis;
    private final boolean isFrozen;
    private final long accumulatedMillis;
    private final long maxAccumulatedMillis;

    public RemainingTimePacket(UUID playerUUID, long remainingMillis, String timezone, long baseMillis, boolean isFrozen) {
        this(playerUUID, remainingMillis, timezone, baseMillis, isFrozen, remainingMillis, baseMillis * 3);
    }

    public RemainingTimePacket(UUID playerUUID, long remainingMillis, String timezone, long baseMillis,
                               boolean isFrozen, long accumulatedMillis, long maxAccumulatedMillis) {
        this.playerUUID = playerUUID;
        this.remainingMillis = remainingMillis;
        this.timezone = timezone;
        this.baseMillis = baseMillis;
        this.isFrozen = isFrozen;
        this.accumulatedMillis = accumulatedMillis;
        this.maxAccumulatedMillis = maxAccumulatedMillis;
    }

    public RemainingTimePacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.remainingMillis = buf.readLong();
        this.timezone = buf.readUtf(100);
        this.baseMillis = buf.readLong();
        this.isFrozen = buf.readBoolean();
        this.accumulatedMillis = buf.readLong();
        this.maxAccumulatedMillis = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeLong(remainingMillis);
        buf.writeUtf(timezone);
        buf.writeLong(baseMillis);
        buf.writeBoolean(isFrozen);
        buf.writeLong(accumulatedMillis);
        buf.writeLong(maxAccumulatedMillis);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        if (ctx.get().getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            return;
        }

        ctx.get().enqueueWork(() -> {
            ClientTimeData.updateFromServer(playerUUID, remainingMillis, timezone, baseMillis, isFrozen);
            ClientOverlay.setAccumulationInfo(accumulatedMillis, maxAccumulatedMillis);
        });
        ctx.get().setPacketHandled(true);
    }
}