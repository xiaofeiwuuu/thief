package com.xiaofeiwu.thief.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaofeiwu.thief.ThiefEntity;
import com.xiaofeiwu.thief.ThiefMod;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** What the magician wears over its suit: a top hat with a red band, and a black cape lined in red that swings as it walks. The copies wear them too. */
public class MagicianGearLayer extends RenderLayer<ThiefEntity, ThiefModel> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ThiefMod.MODID, "textures/entity/magician_gear.png");

    private final ModelPart hat;
    private final ModelPart cape;

    public MagicianGearLayer(RenderLayerParent<ThiefEntity, ThiefModel> parent, ModelPart root) {
        super(parent);
        this.hat = root.getChild("hat");
        this.cape = root.getChild("cape");
    }

    public static LayerDefinition definition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // in the head's own space, the head being 8 high and standing on the neck: the brim on top of it, the crown above the brim, the band round the foot of the crown
        root.addOrReplaceChild("hat", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -17.0F, -3.5F, 7.0F, 8.0F, 7.0F)
                        .texOffs(0, 16).addBox(-3.5F, -11.0F, -3.5F, 7.0F, 2.0F, 7.0F, new CubeDeformation(0.1F))
                        .texOffs(0, 28).addBox(-6.0F, -9.0F, -6.0F, 12.0F, 1.0F, 12.0F),
                PartPose.rotation(0.0F, 0.0F, 0.08F));         // a little to one side
        // in the body's space: hung from the shoulders, just behind the back
        root.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 42).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 16.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, 2.2F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, ThiefEntity thief, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (!thief.looksLikeMagician() || thief.isInvisible()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(thief, 0.0F);
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        hat.render(pose, vc, light, overlay);
        pose.popPose();
        // against a post or a rack the cape would be in the wood
        if (!thief.isPosted() && !thief.isRacked()) {
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            boolean still = thief.isHung() || thief.isRopeTied();
            cape.xRot = still ? 0.08F : 0.12F + Math.min(1.0F, limbSwingAmount) * 0.7F + (float) Math.sin(ageInTicks * 0.09F) * 0.04F;
            cape.render(pose, vc, light, overlay);
            pose.popPose();
        }
    }
}
