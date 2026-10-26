package com.sekwah.narutomod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.entity.jutsuprojectile.EarthWallEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws one block of a rising earth wall.
 *
 * <p>The model is drawn up to {@link #RISE_DEPTH} blocks below its final position and eased back up,
 * so it comes out of the ground (or out of the block below it) rather than popping into place. The
 * ease-out means it slows down as it settles instead of stopping dead, which is what makes the wall
 * look heavy.</p>
 */
public class EarthWallBlockRenderer extends EntityRenderer<EarthWallEntity> {

    /**
     * How far below its final spot a block starts. Two full blocks, so the cube sits completely
     * inside the ground (or inside the block below it) and cannot be seen before it starts climbing.
     */
    private static final float RISE_DEPTH = 2.0f;

    public EarthWallBlockRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(EarthWallEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        BlockState state = entity.getBlockState();
        if (state == null || state.isAir()) {
            return;
        }
        float age = entity.tickCount + partialTicks - entity.getDelay();
        float progress = Mth.clamp(age / EarthWallEntity.RISE_TICKS, 0.0f, 1.0f);
        // Smoothstep: slow at both ends, quickest halfway up.
        float eased = progress * progress * (3.0f - 2.0f * progress);
        poseStack.pushPose();
        poseStack.translate(-0.5d, -(1.0f - eased) * RISE_DEPTH, -0.5d);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(EarthWallEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
