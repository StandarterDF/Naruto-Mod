package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The Rinne Sharingan: the third eye on the forehead.
 *
 * <p>The Rinnegan reads chakra and the Sharingan reads a fight; this eye reads <em>attention</em>.
 * With it open the caster's illusions and black flames cost less to hold, because the eye is doing
 * the looking for them - the discount itself belongs to the techniques it applies to, and this class
 * only keeps the eye open and the mark that tells them it is.</p>
 *
 * <p>It cannot be opened on its own. The third eye is the Six Paths mode's eye, so the mode has to be
 * up first; when the mode closes, the eye has nothing left to sit on and its upkeep refuses it. The
 * night vision is renewed in short steps rather than given for the whole stance, so closing the eye
 * lets the dark back in within a couple of seconds instead of after a minute.</p>
 */
public class RinneSharinganAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** An eye opening is quiet. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C C C C V - one key on from the shape. */
    public static final long COMBO = 12113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinne_sharingan");

    /** Chakra paid for every tick the eye stays open. */
    private static final float COST_PER_TICK = 0.4f;
    private static final int CHAKRA_REGEN_DELAY = 20;

    /** The mark the discount hooks read. Kept a few ticks ahead while the eye is open. */
    public static final String UNTIL_TAG = "narutomod:rinne_sharingan_until";

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but a small
     * grace means a technique resolved a moment after the eye closes still gets its discount.
     */
    private static final int MARK_GRACE = 5;

    /** Night vision is handed out in short steps and renewed, so it fades with the eye. */
    private static final int NIGHT_VISION_TICKS = 40;
    private static final int NIGHT_VISION_INTERVAL = 20;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SixPathsModeAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.six_paths_mode.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG,
                player.level().getGameTime() + MARK_GRACE);
        if (player.tickCount % NIGHT_VISION_INTERVAL == 0) {
            // Ambient and invisible: the eye is a state, not a potion the caster should see ticking.
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                    NIGHT_VISION_TICKS, 0, true, false, false));
        }
    }

    /** The eye is mastered by holding it, so the tick credit is paid from the upkeep. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The night vision reaches the client as an ordinary effect; there is nothing else to draw.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** @return whether the third eye is open on this caster right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** @return whether the mark the discount hooks read is still live on this caster. */
    public static boolean isMarked(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }
}
