package com.xiaofeiwu.thief.client;

import com.xiaofeiwu.thief.ModEntities;
import com.xiaofeiwu.thief.ThiefMod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ThiefMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** The body the ropes are drawn on: the same shape as the thief, a little bigger. */
    static final net.minecraft.client.model.geom.ModelLayerLocation BINDINGS = new net.minecraft.client.model.geom.ModelLayerLocation(new net.minecraft.resources.ResourceLocation(ThiefMod.MODID, "thief"), "bindings");

    /** The magician's hat and cape. */
    static final net.minecraft.client.model.geom.ModelLayerLocation MAGICIAN_GEAR = new net.minecraft.client.model.geom.ModelLayerLocation(new net.minecraft.resources.ResourceLocation(ThiefMod.MODID, "thief"), "magician_gear");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void layers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BINDINGS, () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.HumanoidModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(0.3F), 0.0F), 64, 32));
    }

    @SubscribeEvent
    public static void gear(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MAGICIAN_GEAR, MagicianGearLayer::definition);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.THIEF.get(), ThiefRenderer::new);
        PosedRenderers.register(event);
        event.registerEntityRenderer(ModEntities.ROPE_KNOT.get(), net.minecraft.client.renderer.entity.LeashKnotRenderer::new);
    }
}
