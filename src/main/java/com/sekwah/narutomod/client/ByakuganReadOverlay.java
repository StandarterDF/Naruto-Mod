package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.DoctorSightAbility;
import com.sekwah.narutomod.abilities.jutsus.EightTrigramsFieldAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
 * <p>Two techniques of the eye are readouts rather than pictures of the world. Doctor Sight puts a
 * healer's note on every living thing nearby - the health left and how many effects are running
 * through it - and the Eight Trigrams Field puts a thin health bar over everything inside its circle,
 * because a gentle fist fighting inside its own field needs to know which body is nearly done.</p>
 *
 * <p>Both are drawn from the synced toggle set, so the client never decides for itself that a stance
 * is up. The projection is the same world matrix the level render leaves behind that the mod's other
 * world-anchored tags use, which is why the labels sit on the body and slide off screen when it goes
 * behind the caster. Labels are capped: a crowd is not made more readable by a label on every head,
 * and this is drawn over the world on somebody else's frame budget.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ByakuganReadOverlay {

    /** How far the healer's read reaches, in blocks. */
    private static final double READ_RADIUS = 16.0d;

    /** How far the field's health bars reach, in blocks. */
    private static final double FIELD_RADIUS = 8.0d;

    /** How many healer labels may be on screen at once. */
    private static final int MAX_LABELS = 8;

    /** Allies and the caster are read in white, anything hostile in red. */
    private static final int ALLY_COLOUR = 0xFFFFFFFF;
    private static final int ENEMY_COLOUR = 0xFFFF5555;
    private static final int OUTLINE_COLOUR = 0xFF101010;

    /** The field's bar: dark backing, green while healthy, red when nearly spent. */
    private static final int BAR_BACK_COLOUR = 0xC0101010;
    private static final int BAR_FILL_COLOUR = 0xFF55FF55;
    private static final int BAR_LOW_COLOUR = 0xFFFF5555;
    private static final float BAR_LOW_AT = 0.34f;
    private static final int BAR_WIDTH = 30;
    private static final int BAR_HEIGHT = 3;

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
        boolean field = EightTrigramsFieldAbility.isActive(data) || EightTrigramsFieldAbility.isActive(self);
        if (!doctor && !field) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int halfWidth = graphics.guiWidth() / 2;
        int halfHeight = graphics.guiHeight() / 2;
        double readSqr = READ_RADIUS * READ_RADIUS;
        double fieldSqr = FIELD_RADIUS * FIELD_RADIUS;

        int labels = 0;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
                continue;
            }
            double distanceSqr = living.distanceToSqr(self);
            boolean inField = field && distanceSqr <= fieldSqr;
            boolean read = doctor && labels < MAX_LABELS && distanceSqr <= readSqr;
            if (!inField && !read) {
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

            if (inField) {
                drawHealthBar(graphics, living, x, y);
            }
            if (read) {
                drawLabel(graphics, font, living, x, y - 16);
                labels++;
            }
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

    /** A thin bar over the body, so the field can be fought inside without reading numbers. */
    private static void drawHealthBar(GuiGraphics graphics, LivingEntity body, int x, int y) {
        float ratio = Mth.clamp(body.getHealth() / Math.max(1.0f, body.getMaxHealth()), 0.0f, 1.0f);
        int left = x - BAR_WIDTH / 2;
        graphics.fill(left - 1, y - 1, left + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, BAR_BACK_COLOUR);
        int filled = Math.round(BAR_WIDTH * ratio);
        if (filled > 0) {
            graphics.fill(left, y, left + filled, y + BAR_HEIGHT,
                    ratio <= BAR_LOW_AT ? BAR_LOW_COLOUR : BAR_FILL_COLOUR);
        }
    }
}
