package com.sekwah.narutomod.client.renderer.entity.jutsuprojectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.client.model.jutsu.TruthSeekerOrbModel;
import com.sekwah.narutomod.entity.jutsuprojectile.TailedBallEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the flying Tailed Beast Bomb as what it is: a Truth-Seeking-black sphere, sized by the
 * charge the caster put in.
 *
 * <p>The geometry is the shared {@link TruthSeekerOrbModel} sphere, so the bomb in flight is the
 * same black ball as the halo behind a Six Paths caster - only bigger, and fullbright so it reads
 * at night.</p>
 */
public class TailedBallRenderer extends EntityRenderer<TailedBallEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/block/black_concrete.png");

    public TailedBallRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TailedBallEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(TEXTURE));
        TruthSeekerOrbModel.renderSphere(poseStack, consumer, 0.0d, 0.0d, 0.0d,
                entity.getRenderRadius(partialTicks), packedLight);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(TailedBallEntity entity) {
        return TEXTURE;
    }
}
