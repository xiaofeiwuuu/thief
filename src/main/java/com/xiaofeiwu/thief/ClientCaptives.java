package com.xiaofeiwu.thief;

import java.util.HashMap;
import java.util.Map;

/**
 * What the client has been told about the creatures that are tied up (other than thieves, which carry it in their own data): by entity
 * number. Plain data with nothing of the client in it, so that it can be asked on either side.
 */
public final class ClientCaptives {

    private static final Map<Integer, Captives.State> STATES = new HashMap<>();

    private ClientCaptives() {
    }

    public static Captives.State get(int entityId) {
        return STATES.getOrDefault(entityId, Captives.State.NONE);
    }

    static void apply(CaptivePacket packet) {
        if (packet.mode() == Captives.Mode.NONE.ordinal()) {
            STATES.remove(packet.entityId());
        } else {
            STATES.put(packet.entityId(), new Captives.State(Captives.Mode.values()[packet.mode()], net.minecraft.core.BlockPos.of(packet.pos()),
                    net.minecraft.core.Direction.from2DDataValue(packet.dir()), packet.gap(), packet.holderId()));
        }
    }

    public static void clear() {
        STATES.clear();
    }
}
