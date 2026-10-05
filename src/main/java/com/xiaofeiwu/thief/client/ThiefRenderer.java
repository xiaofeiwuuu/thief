package com.xiaofeiwu.thief.client;

import com.xiaofeiwu.thief.ThiefEntity;
import com.xiaofeiwu.thief.ThiefMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Looks like a player in a burglar's clothes: a mask, a striped shirt, and what it carries in its hand. */
public class ThiefRenderer extends HumanoidMobRenderer<ThiefEntity, ThiefModel> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ThiefMod.MODID, "textures/entity/thief.png");
    private static final ResourceLocation MAGICIAN = new ResourceLocation(ThiefMod.MODID, "textures/entity/magician.png");

    public ThiefRenderer(EntityRendererProvider.Context context) {
        super(context, new ThiefModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new MagicianGearLayer(this, context.bakeLayer(ClientSetup.MAGICIAN_GEAR)));
        addLayer(new BindingsLayer(this, new ThiefModel(context.bakeLayer(ClientSetup.BINDINGS))));
    }

    /** Types whose picture failed to draw once: given up, so that one bad renderer does not fail every frame. */
    private static final java.util.Set<net.minecraft.world.entity.EntityType<?>> BROKEN = new java.util.HashSet<>();

    /** When the thief has drunk its potion, the creature it looks like is drawn in its place, turning and walking as the thief does. */
    @Override
    public void render(ThiefEntity thief, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        net.minecraft.world.entity.EntityType<?> type = thief.disguiseType();
        if (type != null && !BROKEN.contains(type)) {
            Entity dummy = thief.disguiseDummy();
            if (dummy != null) {
                try {
                    dummy.setYRot(thief.getYRot());
                    dummy.yRotO = thief.yRotO;
                    dummy.setXRot(thief.getXRot());
                    dummy.xRotO = thief.xRotO;
                    if (dummy instanceof LivingEntity living) {
                        living.yBodyRot = thief.yBodyRot;
                        living.yBodyRotO = thief.yBodyRotO;
                        living.yHeadRot = thief.yHeadRot;
                        living.yHeadRotO = thief.yHeadRotO;
                    }
                    entityRenderDispatcher.getRenderer(dummy).render(dummy, yaw, partialTick, pose, buffers, light);
                    return;
                } catch (RuntimeException e) {
                    BROKEN.add(type);
                    LogUtils.getLogger().warn("Thief: could not draw a thief that looks like {}, it will look like a thief instead", type, e);
                }
            }
        }
        super.render(thief, yaw, partialTick, pose, buffers, light);
        if (thief.isHung()) {
            drawHangingRope(thief, pose, buffers, light);
        }
    }

    /**
     * The rope a thief hangs by: a bar bound across both wrists, a rope from it up to the underside of the block, a knot where it meets the
     * block. Drawn here, not by the game's lead, so that it is as bright as the thief and there to be seen between the block and the hands.
     */
    private static void drawHangingRope(ThiefEntity thief, PoseStack pose, MultiBufferSource buffers, int light) {
        com.mojang.blaze3d.vertex.VertexConsumer vc = RopeDraw.consumer(buffers);
        float hands = (float) ThiefEntity.HANDS_UP;
        float top = hands + thief.hangRopeLength();
        RopeDraw.box(pose, vc, -0.52F, hands - 0.23F, -0.17F, 0.52F, hands - 0.08F, 0.17F, light);          // the wrists, bound together
        RopeDraw.box(pose, vc, -0.04F, hands - 0.08F, -0.04F, 0.04F, top, 0.04F, light);                     // the rope itself
        RopeDraw.box(pose, vc, -0.08F, top - 0.14F, -0.08F, 0.08F, top, 0.08F, light);                       // the knot at the top
    }

    @Override
    public ResourceLocation getTextureLocation(ThiefEntity entity) {
        return entity.looksLikeMagician() ? MAGICIAN : TEXTURE;
    }
}
