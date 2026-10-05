package com.xiaofeiwu.thief.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.xiaofeiwu.thief.Captives;
import com.xiaofeiwu.thief.ClientCaptives;
import com.xiaofeiwu.thief.ThiefEntity;
import com.xiaofeiwu.thief.ThiefMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The ropes on a tied-up creature other than a thief (a thief has its own ropes, drawn on its own model): bands round the body at the chest, belly,
 * hips, thighs, knees and ankles, a single band at the waist for one that is only led, and, for one hung up, the rope over its head up to the block.
 * They are plain boxes sized to the creature, so they fit anything.
 */
@Mod.EventBusSubscriber(modid = ThiefMod.MODID, value = Dist.CLIENT)
public final class RopeBands {

    /**
     * The bands round the body are only for one tied all round to a tree or a post, standing on the ground: chest, belly, hips, then, with the legs
     * together, thigh, knee and ankle, as a fraction of its height. One that is hung, on a rack or led has its own rope (over the head, at the
     * corners of the frame, from the hand that leads it), and no bands.
     */
    private static final float[] POST_BANDS = {0.72F, 0.62F, 0.52F, 0.42F, 0.28F, 0.15F, 0.04F};

    private RopeBands() {
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCaptives.clear();
    }

    @SubscribeEvent
    public static void draw(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob) || entity instanceof ThiefEntity) {
            return;
        }
        Captives.State state = ClientCaptives.get(entity.getId());
        if (state.mode() == Captives.Mode.NONE) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        int light = event.getPackedLight();
        VertexConsumer vc = RopeDraw.consumer(event.getMultiBufferSource());
        float k = entity.getBbHeight() / 1.95F;                      // a person is 1.95 high: what is drawn for a person is scaled to this one
        float h = entity.getBbHeight();
        // a person's trunk is 8 wide and 4 deep (a quarter and an eighth of a block each way from the middle); a robe (villagers, illagers, witches) is wider and deeper
        boolean person = event.getRenderer().getModel() instanceof HumanoidModel<?>;
        float hx = (person ? 0.28F : 0.31F) * k;
        float hz = (person ? 0.16F : 0.24F) * k;
        float thick = 0.045F * k;
        if (state.mode() == Captives.Mode.LED && state.holderId() >= 0) {
            // the rope from the hand of the one who leads it to the hands of the creature, which are bound in front of it at the waist
            net.minecraft.world.entity.Entity holder = entity.level().getEntity(state.holderId());
            if (holder != null) {
                float partial = event.getPartialTick();
                net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()), Mth.lerp(partial, entity.zo, entity.getZ()));
                double yaw = Math.toRadians(Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot));
                RopeDraw.line(pose, vc, holder.getRopeHoldPosition(partial).subtract(at), new net.minecraft.world.phys.Vec3(-Math.sin(yaw) * 0.55D * k, 0.95D * k, Math.cos(yaw) * 0.55D * k), light);
            }
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(event.getPartialTick(), entity.yBodyRotO, entity.yBodyRot)));
        float[] heights = state.mode() == Captives.Mode.POST ? POST_BANDS : new float[0];
        for (float f : heights) {
            float y = h * f;
            RopeDraw.box(pose, vc, -hx, y, -hz, hx, y + thick, hz, light);
        }
        if (state.mode() == Captives.Mode.HUNG) {
            float hands = (float) ThiefEntity.HANDS_UP * k;
            float top = hands + (float) state.gap();
            boolean armsUp = event.getRenderer().getModel() instanceof HumanoidModel<?>;
            if (armsUp) {
                RopeDraw.box(pose, vc, -0.5F * k, hands - 0.2F * k, -0.14F * k, 0.5F * k, hands - 0.1F * k, 0.14F * k, light);          // the wrists, bound together
            }
            float base = armsUp ? hands - 0.08F * k : h;
            RopeDraw.box(pose, vc, -0.04F, base, -0.04F, 0.04F, top, 0.04F, light);                                         // the rope up to the block
            RopeDraw.box(pose, vc, -0.08F, top - 0.14F, -0.08F, 0.08F, top, 0.08F, light);                                  // the knot
        }
        pose.popPose();
    }
}
