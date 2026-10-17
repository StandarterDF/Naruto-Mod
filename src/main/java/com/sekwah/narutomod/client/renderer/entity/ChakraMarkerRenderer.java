package com.sekwah.narutomod.client.renderer.entity;

import com.sekwah.narutomod.client.model.entity.SubstitutionLogModel;
import com.sekwah.narutomod.entity.ChakraMarkerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the chakra marker log with the same model and texture the substitution technique uses, so
 * the two read as the same trick to a player.
 */
public class ChakraMarkerRenderer extends MobRenderer<ChakraMarkerEntity, SubstitutionLogModel<ChakraMarkerEntity>> {

    public ChakraMarkerRenderer(EntityRendererProvider.Context context) {
        super(context, new SubstitutionLogModel<>(context.bakeLayer(SubstitutionLogModel.LAYER_LOCATION)), 0.3f);
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(ChakraMarkerEntity entity) {
        return SubstitutionLogRenderer.SUBSTITUTION_LOG;
    }
}
