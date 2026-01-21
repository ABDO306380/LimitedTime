package com.AbdoAlabhar.LimitedTime;

import com.AbdoAlabhar.LimitedTime.ClientTimeData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class RemainingTimePacket {
    private final UUID playerUUID;
    private final long remainingMillis;
    private final String timezone;
    private final boolean isFrozen;
    private final int baseCountdownSeconds; // NEW: so client knows the base time

    public RemainingTimePacket(UUID playerUUID, long remainingMillis, String timezone, boolean isFrozen, int baseCountdownSeconds) {
        this.playerUUID = playerUUID;
        this.remainingMillis = remainingMillis;
        this.timezone = timezone;
        this.isFrozen = isFrozen;
        this.baseCountdownSeconds = baseCountdownSeconds;
    }

    public RemainingTimePacket(FriendlyByteBuf buf) {
        this.playerUUID = buf.readUUID();
        this.remainingMillis = buf.readLong();
        this.timezone = buf.readUtf(50);
        this.isFrozen = buf.readBoolean();
        this.baseCountdownSeconds = buf.readInt(); // Read the base time
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(playerUUID);
        buf.writeLong(remainingMillis);
        buf.writeUtf(timezone);
        buf.writeBoolean(isFrozen);
        buf.writeInt(baseCountdownSeconds); // Write the base time
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ClientTimeData.update(playerUUID, remainingMillis, timezone, isFrozen, baseCountdownSeconds);
        });
        ctx.setPacketHandled(true);
    }
}