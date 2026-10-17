package com.sekwah.narutomod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

import java.util.UUID;

/**
 * Renders a shadow clone with its caster's skin, armour and held item.
 *
 * <p>Uses the vanilla player model layers directly, so no custom model or textures are needed.</p>
 */
public class ShadowCloneRenderer extends LivingEntityRenderer<ShadowCloneEntity, PlayerModel<ShadowCloneEntity>> {

    private final PlayerModel<ShadowCloneEntity> wideModel;
    private final PlayerModel<ShadowCloneEntity> slimModel;

    public ShadowCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.wideModel = this.model;
        this.slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);

        HumanoidModel<ShadowCloneEntity> innerArmor =
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidModel<ShadowCloneEntity> outerArmor =
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        this.addLayer(new HumanoidArmorLayer<>(this, innerArmor, outerArmor, context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public void render(ShadowCloneEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        // The caster's skin decides which of the two vanilla player meshes fits.
        this.model = this.isSlim(entity) ? this.slimModel : this.wideModel;
        // Vanilla player poses (raising a shield, drawing a bow) are picked from the item the entity
        // is using, exactly like PlayerRenderer#setModelProperties does for real players.
        this.model.rightArmPose = getArmPose(entity, InteractionHand.MAIN_HAND);
        this.model.leftArmPose = getArmPose(entity, InteractionHand.OFF_HAND);
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    private static HumanoidModel.ArmPose getArmPose(ShadowCloneEntity entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim animation = stack.getUseAnimation();
            if (animation == UseAnim.BLOCK) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (animation == UseAnim.BOW) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (animation == UseAnim.SPEAR) {
                return HumanoidModel.ArmPose.THROW_SPEAR;
            }
            if (animation == UseAnim.CROSSBOW) {
                return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        }
        if (stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    protected void scale(ShadowCloneEntity entity, PoseStack poseStack, float partialTick) {
        if (entity.isChibi()) {
            poseStack.scale(ShadowCloneEntity.CHIBI_SCALE, ShadowCloneEntity.CHIBI_SCALE,
                    ShadowCloneEntity.CHIBI_SCALE);
        }
    }

    /**
     * Clones are meant to be indistinguishable from their caster, so a name tag floating over them
     * would defeat the point of the technique.
     */
    @Override
    protected boolean shouldShowName(ShadowCloneEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowCloneEntity entity) {
        AbstractClientPlayer owner = this.getClientOwner(entity);
        return owner != null
                ? owner.getSkinTextureLocation()
                : DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    private boolean isSlim(ShadowCloneEntity entity) {
        return "slim".equals(this.getSkinModelName(entity));
    }

    /**
     * The model the caster's skin needs, falling back to the default skin the clone would get
     * when its caster is not around (offline or in another dimension).
     */
    private String getSkinModelName(ShadowCloneEntity entity) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        UUID owner = entity.getOwnerUUID().orElse(null);
        if (connection != null && owner != null) {
            PlayerInfo playerInfo = connection.getPlayerInfo(owner);
            if (playerInfo != null) {
                return playerInfo.getModelName();
            }
        }
        return DefaultPlayerSkin.getSkinModelName(entity.getUUID());
    }

    /**
     * Clones always spawn right next to their caster, so on the client the caster is normally
     * loaded and their own skin can be used directly instead of going through the skin manager.
     */
    private AbstractClientPlayer getClientOwner(ShadowCloneEntity entity) {
        ClientLevel level = Minecraft.getInstance().level;
        UUID owner = entity.getOwnerUUID().orElse(null);
        if (level == null || owner == null) {
            return null;
        }
        for (AbstractClientPlayer player : level.players()) {
            if (player.getUUID().equals(owner)) {
                return player;
            }
        }
        return null;
    }
}
