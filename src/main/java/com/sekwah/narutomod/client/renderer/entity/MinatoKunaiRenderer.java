package com.sekwah.narutomod.client.renderer.entity;

import com.sekwah.narutomod.entity.projectile.MinatoKunaiEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class MinatoKunaiRenderer extends ArrowRenderer<MinatoKunaiEntity> {
    public static final ResourceLocation RES_ARROW = new ResourceLocation("narutomod", "textures/entity/projectiles/kunai.png");

    public MinatoKunaiRenderer(EntityRendererProvider.Context manager) {
        super(manager);
    }

    @Override
    public ResourceLocation getTextureLocation(MinatoKunaiEntity entity) {
        return RES_ARROW;
    }
}
