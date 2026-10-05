package com.xiaofeiwu.thief;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells the clients how a creature is tied up, so that they can draw the ropes on it. */
public record CaptivePacket(int entityId, int mode, long pos, int dir, double gap, int holderId) {

    static void encode(CaptivePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeByte(p.mode);
        buf.writeLong(p.pos);
        buf.writeByte(p.dir);
        buf.writeDouble(p.gap);
        buf.writeVarInt(p.holderId + 1);
    }

    static CaptivePacket decode(FriendlyByteBuf buf) {
        return new CaptivePacket(buf.readVarInt(), buf.readByte(), buf.readLong(), buf.readByte(), buf.readDouble(), buf.readVarInt() - 1);
    }

    static void handle(CaptivePacket p, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCaptives.apply(p)));
        context.get().setPacketHandled(true);
    }
}
