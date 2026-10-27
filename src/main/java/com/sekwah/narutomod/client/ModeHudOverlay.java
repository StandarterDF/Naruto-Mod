package com.sekwah.narutomod.client;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealOneAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealTwoAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganAbility;
import com.sekwah.narutomod.abilities.jutsus.LightningModeAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The mode line: what a ninja is currently wearing, said in one glance.
 *
 * <p>A caster can have an eye open, a body mode burning and sage mode running all at the same time,
 * and each of those is drawn on the body in a way that is hard to read from the inside: the aura is
 * behind the camera, and a cloak looks much like the next cloak. The line answers the one question a
 * player actually asks mid-fight - "what am I paying for right now" - without opening the jutsu menu
 * and without a single texture.</p>
 *
 * <p>Only the labels are shown, and only for state the server has already synced: the eye helpers and
 * the toggle set are the same state the upkeeps run on, so the line can never claim a mode is up when
 * it is not being paid for. It is capped at {@link #MAX_LINES} short lines in the top-left corner -
 * the one part of the screen the chakra and stamina bars, which sit at the bottom centre, never
 * reach.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModeHudOverlay {

    /** Left edge of the line, clear of the hotbar and of the bars at the bottom. */
    private static final int X = 8;

    /** Top edge of the line, the first free row of the HUD. */
    private static final int Y = 8;

    private static final int LINE_HEIGHT = 10;

    /** Never more than this many lines, so a caster with everything up still sees the world. */
    private static final int MAX_LINES = 3;

    /** How wide a line may grow before the next label wraps, in GUI pixels. */
    private static final int MAX_WIDTH = 150;

    private static final int LABEL_COLOUR = 0xFFF2F2F2;

    private static final Component SEPARATOR = Component.literal(", ");

    private ModeHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.level == null) {
            return;
        }
        Player player = minecraft.player;
        if (!player.isAlive()) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !ninjaData.isNinjaModeEnabled()) {
            return;
        }
        List<Component> labels = activeLabels(ninjaData);
        if (labels.isEmpty()) {
            return;
        }
        List<Component> lines = wrap(minecraft.font, labels);
        for (int line = 0; line < lines.size(); line++) {
            event.getGuiGraphics().drawString(minecraft.font, lines.get(line),
                    X, Y + line * LINE_HEIGHT, LABEL_COLOUR, true);
        }
    }

    /**
     * @return one short label per mode that is up, in the order the modes are listed in the menu: eyes
     *         first, then the body modes, then the modes that are not toggles at all
     */
    private static List<Component> activeLabels(INinjaData ninjaData) {
        List<Component> labels = new ArrayList<>();
        if (ByakuganAbility.isActive(ninjaData)) {
            labels.add(label("byakugan"));
        }
        if (SharinganAbility.isActive(ninjaData)) {
            labels.add(label("sharingan"));
        }
        if (JouganAbility.isActive(ninjaData)) {
            labels.add(label("jougan"));
        }
        if (RinneganAbility.isActive(ninjaData)) {
            labels.add(label("rinnegan"));
        }
        if (SenriganAbility.isActive(ninjaData)) {
            labels.add(label("senrigan"));
        }
        // The body modes are read from the synced toggle set, which is what their upkeeps run on.
        Set<ResourceLocation> toggles = ninjaData.getToggleAbilityData().getAbilitiesHashSet();
        if (toggles.contains(SixPathsModeAbility.ID)) {
            labels.add(label("six_paths"));
        }
        if (toggles.contains(CurseSealOneAbility.ID) || toggles.contains(CurseSealTwoAbility.ID)) {
            labels.add(label("curse_seal"));
        }
        if (toggles.contains(ChakraCloakAbility.ID)) {
            labels.add(label("chakra_cloak"));
        }
        if (toggles.contains(TailedCloakAbility.ID)) {
            labels.add(label("tailed_cloak"));
        }
        if (ninjaData.getSealVesselIntegrity() > 0) {
            labels.add(Component.translatable("naruto.gui.mode.vessel",
                    ninjaData.getSealVesselIntegrity()));
        }
        if (toggles.contains(LightningModeAbility.ID)) {
            labels.add(label("lightning_mode"));
        }
        // Sage mode is entered with a cast rather than kept as a toggle, so it is read from its own
        // synced flag - the same one the nature energy bar is drawn from.
        if (ninjaData.isSageMode()) {
            labels.add(label("sage_mode"));
        }
        return labels;
    }

    private static Component label(String mode) {
        return Component.translatable("naruto.gui.mode." + mode);
    }

    /**
     * Packs the labels into at most {@link #MAX_LINES} lines, wrapping on width rather than on a fixed
     * count because the labels are translated and a Russian one is not the same length as an English
     * one. Whatever does not fit is folded into a count on the last line rather than dropped in
     * silence.
     */
    private static List<Component> wrap(Font font, List<Component> labels) {
        List<Component> lines = new ArrayList<>();
        MutableComponent current = Component.empty();
        boolean firstInLine = true;
        int placed = 0;
        for (Component label : labels) {
            MutableComponent candidate = firstInLine
                    ? Component.empty().append(label)
                    : Component.empty().append(current).append(SEPARATOR).append(label);
            if (!firstInLine && font.width(candidate) > MAX_WIDTH) {
                if (lines.size() == MAX_LINES - 1) {
                    break;
                }
                lines.add(current);
                current = Component.empty().append(label);
                placed++;
                continue;
            }
            current = candidate;
            firstInLine = false;
            placed++;
        }
        lines.add(current);
        int hidden = labels.size() - placed;
        if (hidden > 0) {
            int last = lines.size() - 1;
            lines.set(last, Component.empty().append(lines.get(last))
                    .append(Component.translatable("naruto.gui.mode.more", hidden)));
        }
        return lines;
    }
}
