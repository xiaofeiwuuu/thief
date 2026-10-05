package com.xiaofeiwu.thief;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "1";

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ThiefMod.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    static void register() {
        CHANNEL.registerMessage(0, CaptivePacket.class, CaptivePacket::encode, CaptivePacket::decode, CaptivePacket::handle);
    }
}
