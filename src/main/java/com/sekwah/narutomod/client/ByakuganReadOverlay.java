package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.DoctorSightAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nullable;

/**
 * The paper half of the Byakugan's reading stances: what a body has left, written over its head.
 *
 * <p>Doctor Sight puts a healer's note on every living thing nearby - the health left and how many
 * effects are running through it. It is drawn from the synced toggle set, so the client never
 * decides for itself that a stance is up. The projection is the same world matrix the level render
 * leaves behind that the mod's other world-anchored tags use, which is why the labels sit on the
 * body and slide off screen when it goes behind the caster. Labels are capped: a crowd is not made
 * more readable by a label on every head, and this is drawn over the world on somebody else's frame
 * budget.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ByakuganReadOverlay {

    /** How far the healer's read reaches, in blocks. */
    private static final double READ_RADIUS = 16.0d;

    /** How many healer labels may be on screen at once. */
    private static final int MAX_LABELS = 8;

    /** Allies and the caster are read in white, anything hostile in red. */
    private static final int ALLY_COLOUR = 0xFFFFFFFF;
    private static final int ENEMY_COLOUR = 0xFFFF5555;
    private static final int OUTLINE_COLOUR = 0xFF101010;

    /** The world matrix and camera the level render last left behind, used to place the labels. */
    private static Matrix4f worldMatrix;
    private static Vec3 cameraPos;

    private ByakuganReadOverlay() {
    }

    /**
     * Keeps the world projection of the frame. It is taken after the solid blocks rather than at the
     * end of the level pass so it is the same matrix the mature overlays read, and it is only ever
     * used in the GUI pass that follows in the same frame.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        worldMatrix = new Matrix4f(event.getProjectionMatrix());
        worldMatrix.mul(event.getPoseStack().last().pose());
        cameraPos = event.getCamera().getPosition();
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player self = minecraft.player;
        if (self == null || minecraft.level == null || minecraft.options.hideGui || !NarutoConfig.byakuganEnabled) {
            return;
        }
        if (worldMatrix == null || cameraPos == null) {
            return;
        }
        INinjaData data = self.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) {
            return;
        }
        boolean doctor = DoctorSightAbility.isActive(data);
        if (!doctor) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int halfWidth = graphics.guiWidth() / 2;
        int halfHeight = graphics.guiHeight() / 2;
        double readSqr = READ_RADIUS * READ_RADIUS;

        int labels = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
                continue;
            }
            double distanceSqr = living.distanceToSqr(self);
            boolean read = doctor && labels < MAX_LABELS && distanceSqr <= readSqr;
            if (!read) {
                continue;
            }

            Vector4f projected = project(living.getX(), living.getY() + living.getBbHeight() + 0.35d, living.getZ());
            if (projected == null) {
                continue;
            }
            int x = Math.round(halfWidth + projected.x() * halfWidth);
            int y = Math.round(halfHeight - projected.y() * halfHeight);
            if (x < -80 || x > graphics.guiWidth() + 80 || y < -40 || y > graphics.guiHeight() + 40) {
                continue;
            }

            drawLabel(graphics, font, living, x, y);
            labels++;
        }
    }

    /**
     * The head of the body in screen space, or null when it is behind the camera or outside the
     * depth range it could be seen in. This is the same projection the marker overlays use.
     */
    @Nullable
    private static Vector4f project(double x, double y, double z) {
        Vector4f projected = new Vector4f((float) (x - cameraPos.x), (float) (y - cameraPos.y),
                (float) (z - cameraPos.z), 1.0f);
        projected.mul(worldMatrix);
        if (projected.w() <= 0.0f) {
            return null;
        }
        projected.div(projected.w());
        if (projected.z() <= 0.0f || projected.z() >= 1.0f) {
            return null;
        }
        return projected;
    }

    /** The healer's note: what is left of the body, and what is running through it. */
    private static void drawLabel(GuiGraphics graphics, Font font, LivingEntity body, int x, int y) {
        int colour = body instanceof Enemy ? ENEMY_COLOUR : ALLY_COLOUR;
        String health = Component.translatable("naruto.gui.doctor.hp",
                Math.round(body.getHealth()), Math.round(body.getMaxHealth())).getString();
        String effects = Component.translatable("naruto.gui.doctor.effects",
                body.getActiveEffects().size()).getString();
        GuiUtils.centeredTextOutlined(graphics, font, health, x, y, colour, OUTLINE_COLOUR);
        GuiUtils.centeredTextOutlined(graphics, font, effects, x, y + 9, colour, OUTLINE_COLOUR);
    }
}
