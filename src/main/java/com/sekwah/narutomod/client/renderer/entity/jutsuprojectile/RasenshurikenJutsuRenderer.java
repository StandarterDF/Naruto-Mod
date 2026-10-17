package com.sekwah.narutomod.client.renderer.entity.jutsuprojectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.jutsuprojectile.RasenshurikenJutsuEntity;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the thrown disc as a shuriken the size of a person, spinning as it flies.
 *
 * <p>The technique has no model of its own: it is the mod's shuriken item blown up and spun, which
 * is what a rasenshuriken looks like from a distance anyway, and it means the wind trail is the only
 * thing that needs particles.</p>
 */
public class RasenshurikenJutsuRenderer extends EntityRenderer<RasenshurikenJutsuEntity> {

    /**
     * The disc is drawn from the shuriken item model, so this is never actually bound - the
     * renderer still has to name a texture of its own.
     */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NarutoMod.MOD_ID, "textures/items/weapons/shuriken.png");

    /** How fast the disc spins, in degrees a tick. */
    private static final float SPIN_SPEED = -90.0f;

    /** How big the thrown shuriken is compared with the one an inventory draws. */
    private static final float SCALE = 2.6f;

    private final ItemRenderer itemRenderer;
    private final ItemStack rendered = new ItemStack(NarutoItems.SHURIKEN.get());

    public RasenshurikenJutsuRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(RasenshurikenJutsuEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTicks, entity.getPrevSpinTicks(), entity.getSpinTicks()) * SPIN_SPEED));
        poseStack.scale(SCALE, SCALE, SCALE);
        BakedModel model = this.itemRenderer.getModel(this.rendered, entity.level(), null, entity.getId());
        this.itemRenderer.render(this.rendered, ItemDisplayContext.FIXED, false, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RasenshurikenJutsuEntity entity) {
        return TEXTURE;
    }
}
