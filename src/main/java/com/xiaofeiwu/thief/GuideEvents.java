package com.xiaofeiwu.thief;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A player gets the guide the first time they come into a world with the mod. */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID)
public final class GuideEvents {

    private GuideEvents() {
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (ThiefConfig.GIVE_GUIDE.get() && !event.getEntity().level().isClientSide) {
            GuideBook.giveOnce(event.getEntity());
        }
    }
}
