package com.sekwah.narutomod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.entity.SmokeCloudEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberately empty renderer for the smoke cloud.
 *
 * <p>The cloud is nothing but the particles it spawns itself, so there is no model to draw. The
 * renderer exists only so the entity has a registered one: leave a type without a renderer and the
 * game logs about it and has nothing to hand the level renderer when it walks its entities.</p>
 */
public class SmokeCloudRenderer extends EntityRenderer<SmokeCloudEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/misc/white.png");

    public SmokeCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SmokeCloudEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Nothing to draw - the smoke is particles.
    }

    @Override
    public ResourceLocation getTextureLocation(SmokeCloudEntity entity) {
        return TEXTURE;
    }
}
