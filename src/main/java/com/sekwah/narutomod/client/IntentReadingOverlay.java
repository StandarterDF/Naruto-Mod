package com.sekwah.narutomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.IntentReading;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The reading of intent: what every body within reach means to do, said with a dot over its head.
 *
 * <p>The passive is about the one thing a fight is decided by and a caster cannot see - intent. A
 * mob that has already picked the caster out of the crowd faces and reads exactly like one that has
 * not until it is on top of them, and a swing gives no warning at all. So while the Sharingan is
 * open every body inside the reading's reach carries a small dot: red when it is aimed at the caster
 * or already swinging, yellow when it is busy with somebody else, white when it has no quarrel at
 * all. Only the two coloured cases get a word, because those are the two a player has to react to; a
 * white dot is simply a body that is not a threat, and a screen of words would bury the ones that
 * matter.</p>
 *
 * <p>Nothing here decides anything: the swing is the vanilla swing animation and the aim is where the
 * body's head is actually pointed, so the client is only reading state the server already ticked. A
 * mob's chosen target is server-side AI state that vanilla never sends, which is why the aim of the
 * head rather than the target field is what the reading judges by; a target the server does send is
 * used as it is. The labels are capped and sorted nearest first - a crowd is not made more readable
 * by a label on every head - and the projection is the same world matrix the mod's other
 * head-anchored tags use, which is why a dot slides off screen with its body instead of pinning
 * itself to the HUD.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IntentReadingOverlay {

    /** How far the reading reaches, in blocks. */
    private static final double RANGE = 12.0d;

    /** The most bodies drawn at once, nearest first. */
    private static final int MAX_LABELS = 12;

    /** Aimed at the caster, or mid-swing: the only case that has to be reacted to now. */
    private static final int ATTACK_COLOUR = 0xFFFF5555;

    /** Aimed at somebody else: worth knowing, not yet urgent. */
    private static final int WATCH_COLOUR = 0xFFFFD24A;

    /** No target at all: the body is there, and that is all the reading says about it. */
    private static final int NEUTRAL_COLOUR = 0xFFFFFFFF;

    /** The black ring around the dot and the outline of the word, so both read against any sky. */
    private static final int OUTLINE_COLOUR = 0xFF101010;

    /** The dot is three pixels across inside a one pixel ring. */
    private static final int DOT_RADIUS = 1;

    /** How far above the dot the word sits, in GUI pixels before scaling. */
    private static final int WORD_GAP = 10;

    /** A word over a head is a hint, not a sign: it is drawn smaller than the screen's own text. */
    private static final float WORD_SCALE = 0.8f;

    /**
     * How close to dead centre a body's head has to be pointed before its look counts as aimed at
     * something: the cosine of about twenty-five degrees, wide enough for a mob that is turning, too
     * narrow for one that merely faces the same half of the world.
     */
    private static final double AIM_DOT = 0.90d;

    /** How high above the head the dot floats, in blocks. */
    private static final double HEAD_GAP = 0.45d;

    /** The world matrix and camera the level render last left behind, used to place the dots. */
    private static Matrix4f worldMatrix;
    private static Vec3 cameraPos;

    private IntentReadingOverlay() {
    }

    /**
     * Keeps the world projection of the frame. It is taken after the solid blocks rather than at the
     * end of the level pass so it is the same matrix the mod's other head-anchored overlays read,
     * and it is only ever used in the GUI pass that follows in the same frame.
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
        if (self == null || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        if (worldMatrix == null || cameraPos == null) {
            return;
        }
        INinjaData ninjaData = self.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        // The reading is the copy wheel's work: without the eye open there is nothing to read.
        if (!IntentReading.has(ninjaData) || !SharinganAbility.isActive(ninjaData)) {
            return;
        }

        List<LivingEntity> bodies = bodiesInReach(minecraft, self);
        if (bodies.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int halfWidth = graphics.guiWidth() / 2;
        int halfHeight = graphics.guiHeight() / 2;
        for (LivingEntity body : bodies) {
            Vector4f projected = project(body.getX(), body.getY() + body.getBbHeight() + HEAD_GAP, body.getZ());
            if (projected == null) {
                continue;
            }
            int x = Math.round(halfWidth + projected.x() * halfWidth);
            int y = Math.round(halfHeight - projected.y() * halfHeight);
            if (x < -40 || x > graphics.guiWidth() + 40 || y < -20 || y > graphics.guiHeight() + 20) {
                continue;
            }
            drawRead(graphics, minecraft, body, self, bodies, x, y);
        }
    }

    /**
     * The living bodies inside the reading, nearest first and cut at {@link #MAX_LABELS}. The caster
     * is not one of them - there is nothing to read about oneself - and neither are spectators, who
     * are not in the world to be aimed at.
     */
    private static List<LivingEntity> bodiesInReach(Minecraft minecraft, Player self) {
        double rangeSqr = RANGE * RANGE;
        List<LivingEntity> bodies = new ArrayList<>();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living == self
                    || !living.isAlive() || living.isRemoved() || living.isSpectator()) {
                continue;
            }
            if (living.distanceToSqr(self) > rangeSqr) {
                continue;
            }
            bodies.add(living);
        }
        bodies.sort(Comparator.comparingDouble(self::distanceToSqr));
        return bodies.size() <= MAX_LABELS ? bodies : bodies.subList(0, MAX_LABELS);
    }

    /** One body's read: the dot, and the word over it when there is something to say. */
    private static void drawRead(GuiGraphics graphics, Minecraft minecraft, LivingEntity body, Player self,
                                 List<LivingEntity> bodies, int x, int y) {
        LivingEntity target = intendedTarget(body, self, bodies);
        boolean aimedAtCaster = target == self;
        boolean swinging = body.attackAnim > 0.0f;

        int colour;
        String word;
        if (aimedAtCaster || swinging) {
            colour = ATTACK_COLOUR;
            word = "naruto.intent.attack";
        } else if (target != null) {
            colour = WATCH_COLOUR;
            word = "naruto.intent.watch";
        } else {
            colour = NEUTRAL_COLOUR;
            word = null;
        }

        graphics.fill(x - DOT_RADIUS - 1, y - DOT_RADIUS - 1, x + DOT_RADIUS + 2, y + DOT_RADIUS + 2,
                OUTLINE_COLOUR);
        graphics.fill(x - DOT_RADIUS, y - DOT_RADIUS, x + DOT_RADIUS + 1, y + DOT_RADIUS + 1, colour);
        if (word == null) {
            return;
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y - WORD_GAP, 0.0f);
        pose.scale(WORD_SCALE, WORD_SCALE, 1.0f);
        GuiUtils.centeredTextOutlined(graphics, minecraft.font, Component.translatable(word).getString(),
                0, 0, colour, OUTLINE_COLOUR);
        pose.popPose();
    }

    /**
     * What the body means to hit, or null when it means nothing.
     *
     * <p>A target the server actually sends is taken as it is; vanilla only sends it for the mod's
     * own entities, so the usual case is worked out from aim instead. Whoever the body's head is
     * pointed at, nearest first, is the read: the caster, or one of the other bodies in reach.</p>
     */
    @Nullable
    private static LivingEntity intendedTarget(LivingEntity body, Player self, List<LivingEntity> bodies) {
        if (body instanceof Mob mob && mob.getTarget() != null) {
            return mob.getTarget();
        }
        LivingEntity nearest = aimedAt(body, self) ? self : null;
        double best = nearest == null ? Double.MAX_VALUE : body.distanceToSqr(self);
        for (LivingEntity other : bodies) {
            if (other == body || !aimedAt(body, other)) {
                continue;
            }
            double distance = body.distanceToSqr(other);
            if (distance < best) {
                best = distance;
                nearest = other;
            }
        }
        return nearest;
    }

    /** @return whether the body's head is pointed within {@link #AIM_DOT} of the target's centre. */
    private static boolean aimedAt(LivingEntity body, Entity target) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(body.getEyePosition());
        double distance = toTarget.length();
        if (distance < 1.0E-4d) {
            return false;
        }
        return body.getViewVector(1.0f).normalize().dot(toTarget.scale(1.0d / distance)) >= AIM_DOT;
    }

    /**
     * The head of the body in screen space, or null when it is behind the camera or outside the
     * depth range it could be seen in. This is the same projection the mod's other world tags use,
     * and it is what keeps an entity standing behind the caster from folding back into view.
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
}
