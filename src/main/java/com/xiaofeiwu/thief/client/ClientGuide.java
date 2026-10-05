package com.xiaofeiwu.thief.client;

import net.minecraft.client.Minecraft;

/** Opens the guide on the screen. Client only. */
public final class ClientGuide {

    private ClientGuide() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GuideScreen());
    }
}
