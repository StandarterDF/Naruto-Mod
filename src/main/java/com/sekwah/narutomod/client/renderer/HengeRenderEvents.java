package com.sekwah.narutomod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The client half of henge: making a disguised player look like somebody else.
 *
 * <p>A player's skin and their model (wide or slim) are both read off the player entity itself, from
 * {@code AbstractClientPlayer#getSkinTextureLocation()} and {@code getModelName()}, and neither can be
 * swapped from outside. So instead of trying to, the vanilla render is cancelled in
 * {@link RenderPlayerEvent.Pre} and the same vanilla {@link PlayerRenderer} is asked to draw the player
 * again through a copy of itself whose only change is
 * {@link HengePlayerRenderer#getTextureLocation(AbstractClientPlayer)} - everything else about the
 * render, down to the armour, the held items and the elytra, is the ordinary code path.</p>
 *
 * <p>The copy is made twice, once per model, because the copied player may well be slim while the
 * caster is not. It is created from the renderer context Forge hands out in
 * {@code NarutoRenderEvents#addLayers}, as there is no other way to bake a player model.</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HengeRenderEvents {

    /** Set while the disguise render is in flight, so the nested hook knows to keep out of the way. */
    private static boolean rendering;

    private static EntityRendererProvider.Context context;
    private static HengePlayerRenderer wideRenderer;
    private static HengePlayerRenderer slimRenderer;

    /** Handed to us by the mod bus event, see {@code NarutoRenderEvents#addLayers}. */
    public static void setContext(EntityRendererProvider.Context rendererContext) {
        context = rendererContext;
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (rendering) {
            return;
        }
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return;
        }
        String targetId = ninjaData.getHengeTarget();
        if (targetId.isEmpty()) {
            return;
        }
        UUID target;
        try {
            target = UUID.fromString(targetId);
        } catch (IllegalArgumentException malformed) {
            return;
        }
        HengePlayerRenderer renderer = renderer(isSlimModel(target));
        if (renderer == null) {
            return;
        }

        renderer.setTextureOverride(resolveSkin(target));
        event.setCanceled(true);
        rendering = true;
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(), player.yRotO, player.getYRot());
            renderer.render(player, yaw, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            rendering = false;
            renderer.setTextureOverride(null);
        }
    }

    /**
     * The name tag has to lie as well as the skin, or the disguise falls apart the moment anyone looks
     * at it. It is only replaced while the name tag is actually being drawn, which the event
     * guarantees.
     */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null || ninjaData.getHengeName().isEmpty()) {
            return;
        }
        event.setContent(Component.literal(ninjaData.getHengeName())
                .withStyle(event.getOriginalContent().getStyle()));
    }

    /** @return the skin to draw, preferring whoever else on the client already knows it. */
    private static ResourceLocation resolveSkin(UUID target) {
        PlayerInfo info = playerInfo(target);
        if (info != null) {
            return info.getSkinLocation();
        }
        AbstractClientPlayer entity = loadedPlayer(target);
        if (entity != null) {
            return entity.getSkinTextureLocation();
        }
        // Nobody knows this player: their default skin still beats showing the wrong face.
        return DefaultPlayerSkin.getDefaultSkin(target);
    }

    /** @return whether the copied player uses the slim model. */
    private static boolean isSlimModel(UUID target) {
        PlayerInfo info = playerInfo(target);
        if (info != null) {
            return "slim".equals(info.getModelName());
        }
        AbstractClientPlayer entity = loadedPlayer(target);
        return entity != null && "slim".equals(entity.getModelName());
    }

    private static PlayerInfo playerInfo(UUID target) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(target);
    }

    private static AbstractClientPlayer loadedPlayer(UUID target) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Player player = level.getPlayerByUUID(target);
        return player instanceof AbstractClientPlayer clientPlayer ? clientPlayer : null;
    }

    private static HengePlayerRenderer renderer(boolean slim) {
        if (context == null) {
            return null;
        }
        if (slim) {
            if (slimRenderer == null) {
                slimRenderer = new HengePlayerRenderer(context, true);
            }
            return slimRenderer;
        }
        if (wideRenderer == null) {
            wideRenderer = new HengePlayerRenderer(context, false);
        }
        return wideRenderer;
    }

    /**
     * An ordinary player renderer that draws one specific texture instead of the wearer's own. It is
     * only ever pointed at through {@link #onRenderPlayer}, and only for the length of one render.
     */
    private static class HengePlayerRenderer extends PlayerRenderer {

        private ResourceLocation textureOverride;

        private HengePlayerRenderer(EntityRendererProvider.Context context, boolean slim) {
            super(context, slim);
        }

        private void setTextureOverride(ResourceLocation texture) {
            this.textureOverride = texture;
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractClientPlayer player) {
            return this.textureOverride != null ? this.textureOverride : super.getTextureLocation(player);
        }
    }
}
