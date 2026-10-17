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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
 *
 * <p>When the copied form is a mob rather than a player there is no model to swap, so a stand-in
 * entity of that type is kept and drawn with its own renderer in the player's place. It is parked
 * exactly where the player is and never ticked, so {@link #syncPose} carries the pose - and the limb
 * swing - over onto it every frame.</p>
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HengeRenderEvents {

    /** Set while the disguise render is in flight, so the nested hook knows to keep out of the way. */
    private static boolean rendering;

    private static EntityRendererProvider.Context context;
    private static HengePlayerRenderer wideRenderer;
    private static HengePlayerRenderer slimRenderer;

    /** The mob model currently drawn in place of a player, rebuilt when the copied form changes. */
    private static Entity fakeMob;
    private static String fakeMobType = "";
    private static ClientLevel fakeMobLevel;
    private static long fakeMobTick = Long.MIN_VALUE;

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
        String entityType = ninjaData.getHengeEntityType();
        if (!entityType.isEmpty()) {
            renderAsMob(player, ninjaData, entityType, event);
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
     * Draws a disguised player as the mob they copied. There is no way to tell a player model to look
     * like a cow, so a stand-in entity of the copied type is kept around and its own vanilla renderer
     * is asked to draw it in the player's place - whatever model, layers and textures that mob has.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderAsMob(AbstractClientPlayer player, INinjaData ninjaData, String typeId,
                                    RenderPlayerEvent.Pre event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity mob = standInMob(level, typeId);
        if (mob == null) {
            return;
        }
        syncPose(mob, player, ninjaData.getHengeName());
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob);
        if (renderer == null) {
            return;
        }

        event.setCanceled(true);
        rendering = true;
        try {
            float yaw = Mth.rotLerp(event.getPartialTick(), player.yRotO, player.getYRot());
            renderer.render(mob, yaw, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            rendering = false;
        }
    }

    /**
     * @return the stand-in mob for a copied form, created once per type and reused, or null if the
     *     type is not known on this client.
     */
    private static Entity standInMob(ClientLevel level, String typeId) {
        if (fakeMob == null || fakeMobLevel != level || !typeId.equals(fakeMobType)) {
            fakeMob = EntityType.byString(typeId).map(type -> type.create(level)).orElse(null);
            fakeMobType = typeId;
            fakeMobLevel = level;
            fakeMobTick = Long.MIN_VALUE;
        }
        return fakeMob;
    }

    /**
     * Puts the stand-in mob exactly where the player is standing and copy their pose onto it. The mob
     * is never ticked, so anything animated about it - the limb swing above all - has to be carried
     * over from the player here.
     */
    private static void syncPose(Entity mob, AbstractClientPlayer player, String name) {
        mob.setPos(player.getX(), player.getY(), player.getZ());
        mob.setYRot(player.getYRot());
        mob.yRotO = player.yRotO;
        mob.setXRot(player.getXRot());
        mob.xRotO = player.xRotO;
        mob.tickCount = player.tickCount;
        mob.setCustomName(name.isEmpty() ? null : Component.literal(name));
        mob.setCustomNameVisible(!name.isEmpty());

        if (mob instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot;
            living.yBodyRotO = player.yBodyRotO;
            living.setYHeadRot(player.getYHeadRot());
            living.yHeadRotO = player.yHeadRotO;
            // The limb swing is a per-tick accumulation, so it must be advanced once per tick and
            // not once per frame - feeding it every frame is what made the legs twitch far too fast.
            long gameTime = player.level().getGameTime();
            if (gameTime != fakeMobTick) {
                fakeMobTick = gameTime;
                living.walkAnimation.update(player.walkAnimation.speed(), 1.0f);
            }
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
