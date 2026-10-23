package com.sekwah.narutomod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.entity.jutsuprojectile.ChibakuTenseiEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * A deliberately empty renderer for the Chibaku Tensei core.
 *
 * <p>The core is not drawn: the ball the technique makes is the real terrain it tears loose, held
 * on a shell around this invisible centre, and the dust around it comes from its own tick. The
 * renderer exists only so the entity type has one - a type without a renderer is logged about and
 * leaves the level renderer with nothing to draw it with.</p>
 */
public class ChibakuTenseiRenderer extends EntityRenderer<ChibakuTenseiEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/misc/white.png");

    public ChibakuTenseiRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ChibakuTenseiEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Nothing to draw - the ball is made of falling blocks and particles.
    }

    @Override
    public ResourceLocation getTextureLocation(ChibakuTenseiEntity entity) {
        return TEXTURE;
    }
}
