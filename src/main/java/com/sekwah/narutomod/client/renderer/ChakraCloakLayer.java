package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * The chakra cloak worn on the body: a translucent shell drawn over the caster's own skin.
 *
 * <p>The plain cloak burns orange-red and thin; the tail drawn over it burns a deeper red and a
 * little thicker. The sealed vessel's cloaks burn gold instead: the divided one a thin pale film
 * shared with the whole line, the perfect one a bright thick shroud, the strongest shell in the
 * branch. The tint comes from the vertex colour over one shared white texture, so all four stages
 * are one layer and one texture. Which shell to draw is read from the synced toggle set - the same
 * state the upkeeps run on - so the shell can never linger after the stance is paid for.</p>
 */
public class ChakraCloakLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(NarutoMod.MOD_ID, "textures/entity/chakra_cloak.png");

    public ChakraCloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return;
        }
        Set<ResourceLocation> toggles = ninjaData.getToggleAbilityData().getAbilitiesHashSet();
        float red;
        float green;
        float blue;
        float alpha;
        float scale;
        if (toggles.contains(PerfectCloakAbility.ID)) {
            red = 1.0f;
            green = 0.78f;
            blue = 0.15f;
            alpha = 0.55f;
            scale = 1.1f;
        } else if (toggles.contains(TailedCloakAbility.ID)) {
            red = 0.72f;
            green = 0.06f;
            blue = 0.04f;
            alpha = 0.55f;
            scale = 1.07f;
        } else if (toggles.contains(DividedCloakAbility.ID)) {
            red = 1.0f;
            green = 0.72f;
            blue = 0.3f;
            alpha = 0.3f;
            scale = 1.0f;
        } else if (toggles.contains(ChakraCloakAbility.ID)) {
            red = 1.0f;
            green = 0.32f;
            blue = 0.08f;
            alpha = 0.42f;
            scale = 1.0f;
        } else {
            return;
        }

        poseStack.pushPose();
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale);
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(TEXTURE));
        this.getParentModel().renderToBuffer(poseStack, consumer, packedLight,
                OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
        poseStack.popPose();
    }
}
