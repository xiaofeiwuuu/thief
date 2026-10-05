package com.xiaofeiwu.thief;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ThiefMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {

    private ModEvents() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THIEF.get(), ThiefEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.THIEF_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.THIEF_GUIDE);
            event.accept(ModItems.THIEF_TRACKER);
            event.accept(ModItems.ROPE);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RACK);
            event.accept(ModItems.MAGICIAN_TOKEN);
            event.accept(ModItems.BOUNTY_BOARD);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.WHIP);
        }
    }
}
