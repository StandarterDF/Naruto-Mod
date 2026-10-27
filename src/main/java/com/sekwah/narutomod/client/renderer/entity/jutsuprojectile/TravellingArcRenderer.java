package com.sekwah.narutomod.client.renderer.entity.jutsuprojectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.entity.jutsuprojectile.TravellingArcEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The travelling wind arc draws nothing itself: the crescent is server-sent particles, so there
 * is no model to pose. The renderer exists because the dispatcher insists on one.
 */
public class TravellingArcRenderer extends EntityRenderer<TravellingArcEntity> {

    public TravellingArcRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TravellingArcEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Particles only, see TravellingArcEntity#drawFront.
    }

    @Override
    public ResourceLocation getTextureLocation(TravellingArcEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
