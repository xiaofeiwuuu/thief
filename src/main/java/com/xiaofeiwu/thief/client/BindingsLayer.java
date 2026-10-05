package com.xiaofeiwu.thief.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaofeiwu.thief.ThiefEntity;
import com.xiaofeiwu.thief.ThiefMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** The ropes wound round a thief that is tied to a post: a second, slightly larger body that is see-through except for the turns of rope. */
public class BindingsLayer extends RenderLayer<ThiefEntity, ThiefModel> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(ThiefMod.MODID, "textures/entity/thief_bindings.png");

    private final ThiefModel model;

    public BindingsLayer(RenderLayerParent<ThiefEntity, ThiefModel> parent, ThiefModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, ThiefEntity thief, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (!thief.isPosted()) {
            return;
        }
        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(thief, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(thief, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        renderColoredCutoutModel(model, TEXTURE, pose, buffers, light, thief, 1.0F, 1.0F, 1.0F);
    }
}
