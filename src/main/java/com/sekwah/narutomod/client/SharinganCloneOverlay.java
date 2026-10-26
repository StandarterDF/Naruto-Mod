package com.sekwah.narutomod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.DecoyCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.IllusoryDoubleAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What an open Sharingan writes over a crowd of bodies: which of them are copies, and which is the
 * person.
 *
 * <p>The eye does not see shapes, it sees chakra, and a chakra construct wearing a face is the one
 * thing it will never be fooled by. So every clone inside the caster's reach carries a small word
 * above it, and the word is coloured by what the copy is: red for a real shadow clone - a body with
 * chakra in it and a hand that can hurt - and grey for a decoy or an illusory double, which are
 * nothing but a shape left behind. A living person gets their own name in the same red, so the
 * question "which one is really them" is answered by looking, not by counting swings.</p>
 *
 * <p>Nothing here is a gameplay effect: the labels are drawn on the client that has the eye open and
 * are only ever the client's read of a synced stance. The world model is never touched, and the
 * nearest {@link #MAX_LABELS} bodies are all that is labelled, so a battlefield of clones cannot
 * turn into a wall of text.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SharinganCloneOverlay {

    /** How far the eye picks a copy out of the crowd, in blocks. */
    private static final double RANGE = 24.0d;

    /** The most labels drawn at once, nearest first. */
    private static final int MAX_LABELS = 12;

    /** A label is a word over a head, not a sign: it is drawn smaller than the screen's own text. */
    private static final float LABEL_SCALE = 0.8f;

    /** Copies with no chakra in them: decoys and illusions. */
    private static final int GREY = 0xB4B4B4;

    /** Real bodies, and the person the eye is actually looking at. */
    private static final int RED = 0xFF6060;

    /** The same black outline the mod's other world text uses, so a label reads against any sky. */
    private static final int OUTLINE = 0x000000;

    /** What every copy is called, whatever kind of copy it is. */
    private static final String CLONE_KEY = "narutomod:overlay.clone";

    /** The world-to-screen picture of the frame being drawn, captured while the level renders. */
    private static Matrix4f worldMatrix;
    private static Vec3 cameraPos;
    private static float partialTick;

    private SharinganCloneOverlay() {
    }

    /**
     * The projection and view of this frame, taken while the level is still on screen. The labels are
     * drawn later, with the GUI, and this is the only moment the camera's own matrices are handed out.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        worldMatrix = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getPoseStack().last().pose());
        cameraPos = event.getCamera().getPosition();
        partialTick = event.getPartialTick();
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        if (worldMatrix == null || cameraPos == null) {
            return;
        }
        INinjaData ninjaData = minecraft.player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .resolve().orElse(null);
        if (ninjaData == null || !SharinganAbility.isActive(ninjaData)) {
            return;
        }

        List<Entity> bodies = bodiesInSight(minecraft);
        if (bodies.isEmpty()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int halfWidth = minecraft.getWindow().getGuiScaledWidth() / 2;
        int halfHeight = minecraft.getWindow().getGuiScaledHeight() / 2;
        for (Entity body : bodies) {
            drawLabel(graphics, minecraft, body, halfWidth, halfHeight);
        }
    }

    /**
     * The bodies worth labelling, nearest first: every clone and every other living person inside the
     * eye's reach. The caster is not labelled - there is no need to tell yourself who you are - and
     * the list is cut at {@link #MAX_LABELS} so a crowd of copies cannot bury the screen.
     */
    private static List<Entity> bodiesInSight(Minecraft minecraft) {
        Player self = minecraft.player;
        AABB reach = self.getBoundingBox().inflate(RANGE);
        List<Entity> found = new ArrayList<>(minecraft.level.getEntities(self, reach,
                entity -> entity.isAlive() && (entity instanceof ShadowCloneEntity
                        || (entity instanceof Player other && other != self && !other.isSpectator()))));
        found.removeIf(entity -> self.distanceToSqr(entity) > RANGE * RANGE);
        found.sort(Comparator.comparingDouble(self::distanceToSqr));
        return found.size() <= MAX_LABELS ? found : found.subList(0, MAX_LABELS);
    }

    /** Projects one body's head to the screen and writes the eye's read of it there. */
    private static void drawLabel(GuiGraphics graphics, Minecraft minecraft, Entity body,
                                  int halfWidth, int halfHeight) {
        Vec3 pos = body.getPosition(partialTick);
        Vector4f screen = new Vector4f(
                (float) (pos.x - cameraPos.x),
                (float) (pos.y + body.getBbHeight() + 0.45d - cameraPos.y),
                (float) (pos.z - cameraPos.z),
                1.0f);
        screen.mul(worldMatrix);
        if (screen.w() <= 0.001f) {
            // Behind the camera: the perspective divide would fold it back into view.
            return;
        }
        float depth = screen.z() / screen.w();
        if (depth < 0.0f || depth > 1.0f) {
            return;
        }
        int x = Math.round(halfWidth + screen.x() / screen.w() * halfWidth);
        int y = Math.round(halfHeight - screen.y() / screen.w() * halfHeight);

        boolean illusion = body.getPersistentData().getBoolean(IllusoryDoubleAbility.MARK_TAG)
                || body.getPersistentData().getBoolean(DecoyCloneAbility.DECOY_KEY);
        String label = body instanceof Player player
                ? player.getDisplayName().getString()
                : Component.translatable(CLONE_KEY).getString();
        int colour = body instanceof Player || !illusion ? RED : GREY;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate((float) x, (float) y, 0.0f);
        pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0f);
        GuiUtils.centeredTextOutlined(graphics, minecraft.font, label, 0, 0, colour, OUTLINE);
        pose.popPose();
    }
}
