package com.AbdoAlabhar.LimitedTime;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemainingTimePacket {
    private final UUID playerUUID;
    private final long remainingMillis;
    private final String timezone;
    private final long baseMillis;
    private final boolean isFrozen; // NEW: Include frozen state

    public RemainingTimePacket(UUID playerUUID, long remainingMillis, String timezone, long baseMillis, boolean isFrozen) {
        this.playerUUID = playerUUID;
        this.remainingMillis = remainingMillis;
        this.timezone = timezone;
        this.baseMillis = baseMillis;
        this.isFrozen = isFrozen; // NEW
    }

    // Decode from buffer
    public RemainingTimePacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.remainingMillis = buf.readLong();
        this.timezone = buf.readUtf(50);
        this.baseMillis = buf.readLong();
        this.isFrozen = buf.readBoolean(); // NEW
    }

    // Encode to buffer
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeLong(remainingMillis);
        buf.writeUtf(timezone);
        buf.writeLong(baseMillis);
        buf.writeBoolean(isFrozen); // NEW
    }

    // Handler for client
    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // Update client-side data for overlay
            ClientTimeData.update(playerUUID, remainingMillis, timezone, baseMillis, isFrozen); // UPDATED
            System.out.println("Player " + playerUUID + " has " + remainingMillis + " ms left in " + timezone + ", base: " + baseMillis + "ms, frozen: " + isFrozen);
        });
        ctx.setPacketHandled(true);
    }
}