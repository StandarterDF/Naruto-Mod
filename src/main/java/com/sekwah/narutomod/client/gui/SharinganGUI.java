package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The Sharingan's readout: what the eye sees when it looks at somebody.
 *
 * <p>Nothing is drawn on the body itself - the eye is state, and the mod does not paint eyes over
 * the screen. What the caster gets instead is a small panel in the corner with the plain facts the
 * eye can read off a creature: how much health it has left, how well it is armoured, and every
 * effect running through it. That turns the eye into what it is in a fight - a way to know whether
 * the target in front of you is fresh, poisoned, hasted or already dying.</p>
 *
 * <p>The panel only appears while the eye is open, and only when something is actually under the
 * crosshair at reading distance. It fades out with the stance, so closing the eye also closes the
 * readout.</p>
 */
public class SharinganGUI implements PlayerGUI {

    /** How far the eye can read a target, matching the technique's own look range. */
    private static final double READ_RANGE = 24.0d;

    private static final int PANEL_COLOUR = 0xB0100000;
    private static final int BORDER_COLOUR = 0xFF8B1A1A;
    private static final int TITLE_COLOUR = 0xFFFF5555;
    private static final int TEXT_COLOUR = 0xFFE8E8E8;
    private static final int HEALTH_COLOUR = 0xFF55FF55;
    private static final int ARMOUR_COLOUR = 0xFFAAAAFF;

    private final Minecraft minecraft;

    private boolean open;
    /** 1 while the eye is open, sliding to 0 as it closes, so the panel does not blink out. */
    private float fade;

    public SharinganGUI(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void tick(Player player) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            open = SharinganAbility.isActive(data);
        });
        fade = open ? 1f : Math.max(0f, fade - 0.1f);
    }

    @Override
    public void render(GuiGraphics guiGraphics, Matrix4f worldMatrix, Vec3 cameraPos) {
        if (fade <= 0f || this.minecraft.player == null) {
            return;
        }
        if (!(this.minecraft.crosshairPickEntity instanceof LivingEntity target)) {
            return;
        }
        Player self = this.minecraft.player;
        if (target == self || target.distanceToSqr(self) > READ_RANGE * READ_RANGE) {
            return;
        }
        if (!self.hasLineOfSight(target)) {
            return;
        }

        List<String> effects = new ArrayList<>();
        for (MobEffectInstance effect : target.getActiveEffects()) {
            effects.add(effect.getEffect().getDisplayName().getString()
                    + (effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : ""));
        }

        String name = target.getDisplayName().getString();
        String health = (int) Math.ceil(target.getHealth()) + "/" + (int) target.getMaxHealth();
        String armour = String.valueOf(target.getArmorValue());

        int width = 132;
        int x = 8;
        int y = 8;
        int height = 44 + Math.max(1, effects.size()) * 10;
        int alpha = (int) (0xFF * fade) << 24;

        guiGraphics.fill(x - 3, y - 3, x + width + 3, y + height, (PANEL_COLOUR & 0x00FFFFFF) | alpha);
        guiGraphics.fill(x - 3, y - 3, x + width + 3, y - 2, (BORDER_COLOUR & 0x00FFFFFF) | alpha);
        guiGraphics.fill(x - 3, y + height - 1, x + width + 3, y + height, (BORDER_COLOUR & 0x00FFFFFF) | alpha);

        guiGraphics.drawString(this.minecraft.font, Component.translatable("naruto.gui.sharingan.readout"),
                x, y, withAlpha(TITLE_COLOUR, alpha), false);
        guiGraphics.drawString(this.minecraft.font, name, x, y + 12, withAlpha(TEXT_COLOUR, alpha), false);
        guiGraphics.drawString(this.minecraft.font, health, x, y + 24, withAlpha(HEALTH_COLOUR, alpha), false);
        guiGraphics.drawString(this.minecraft.font, armour, x + 60, y + 24, withAlpha(ARMOUR_COLOUR, alpha), false);

        int lineY = y + 38;
        if (effects.isEmpty()) {
            guiGraphics.drawString(this.minecraft.font,
                    Component.translatable("naruto.gui.sharingan.noeffects"),
                    x, lineY, withAlpha(TEXT_COLOUR, alpha), false);
        } else {
            for (String effect : effects) {
                guiGraphics.drawString(this.minecraft.font, effect, x, lineY, withAlpha(TEXT_COLOUR, alpha), false);
                lineY += 10;
            }
        }
    }

    private static int withAlpha(int colour, int alpha) {
        return (colour & 0x00FFFFFF) | alpha;
    }
}
