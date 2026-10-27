package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.TravellingArcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * A scythe of wind drawn across the space in front of the caster - and thrown.
 *
 * <p>It is the answer to being shot at from close range: the crescent leaves the caster, crosses
 * the ground ahead widening a little as it goes, and whatever the front passes through is cut.
 * Arrows, fireballs and thrown things come apart where they hang, and bodies caught in it are
 * opened a little without being pushed anywhere. That last part is the point - a technique that
 * scattered everything would put the archer back at a comfortable distance, and this one is meant
 * to leave them standing inside the caster's reach with nothing left to shoot.</p>
 *
 * <p>It is silent enough to be lost in a fight and cheap enough to be thrown between steps, but it
 * does not stop a blade, only what flies. The flight itself lives in {@code TravellingArcEntity}:
 * this class only pays for the draw and lets it go.</p>
 */
public class ProjectileCutAbility extends Ability implements Ability.Cooldown {

    /** A cut through the air travels no further than a raised voice. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** B C B - short enough to throw between two arrows already in the air. */
    public static final long COMBO = 13L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "projectile_cut");

    /** How far the scythe reaches. */
    private static final double RANGE = 6.0d;

    /** Stamina paid, and the wait before the arm can cut again. */
    private static final float COST = 18.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 120;

    /** Half of the hundred-and-twenty degree arc, as a dot product: cos(60 degrees). */
    private static final double ARC_DOT = 0.5d;

    /** What a body caught in the cut takes. It is a cut, not a blow, so nothing is pushed. */
    private static final float DAMAGE = 3.0f;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        TravellingArcEntity wave = new TravellingArcEntity(level, player, RANGE, ARC_DOT, DAMAGE,
                TravellingArcEntity.KIND_CUT, true, 0, 0.0f);
        level.addFreshEntity(wave);
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }
}
