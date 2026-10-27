package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * The blade held ready between blows rather than swung.
 *
 * <p>A swordsman who has learned to read a fight does not need to be faster than the arm coming at
 * them, only to have the edge already where that arm will be. While the stance is up a share of
 * every blow that lands - a sword, a fist or an arrow - is turned aside on the flat of the blade and
 * answered with a cut of the caster's own, paid for out of stamina per parry rather than per second.
 * The blade has to be in the hand for any of it: sheath the sword and the guard is gone.</p>
 *
 * <p>An arrow can be knocked out of the air but cannot be cut back, so a parried projectile only
 * costs the parry and its shooter nothing. The rolls and the counter-blow live in
 * {@code KenjutsuTaijutsuEvents}, because a technique cannot see a blow that is about to land; all
 * this class does is hold the guard mark the hit hook reads.</p>
 *
 * <p>The guard is raised with the right mouse button while one of the mod's swords is in hand. The
 * node on the tree teaches the move; there is no combo to remember and no row to click in the jutsu
 * menu.</p>
 */
public class ParryAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A guard is held, not announced: the clash itself is what is heard. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** The guard has no combo: it is raised with the right mouse button while a mod sword is held. */
    public static final long COMBO = -1L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "blade_parry");

    /** The mark the hit hook reads: the next blow met by the raised blade. */
    public static final String UNTIL_TAG = "narutomod:blade_parry_until";

    /** Game time before which the blade cannot be raised again. */
    public static final String READY_TAG = "narutomod:blade_parry_ready";

    /** How long the blade stays ready after the right-click, in ticks. */
    private static final int GUARD_TICKS = 30;

    /** How long the arm needs before it can raise the guard again, in ticks. */
    private static final int GUARD_COOLDOWN = 20;

    /** What raising the guard costs in stamina. */
    private static final float GUARD_COST = 8.0f;

    private static final int STAMINA_REGEN_DELAY = 10;

    /** One deliberate raise turns aside the next blow outright; it is spent when it does. */
    public static final float BLOCK_STAMINA = 4.0f;

    /** What the blade gives back to whoever swung a melee blow into it. */
    public static final float COUNTER_DAMAGE = 4.0f;

    /** The regen delay a parry puts on the stamina bar, so blocking cannot be spammed for free. */
    public static final int BLOCK_REGEN_DELAY = 10;

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
        // The guard is no longer a stance in the toggle set; it is raised from the sword's own use.
        return false;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
    }

    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /**
     * Raises the blade for a short window. Called by the mod's swords on right-click, so the guard is
     * a move rather than a technique: no combo, no slot in the jutsu menu, and the same cost in
     * stamina each time. The next blow that lands inside the window is turned aside and the guard is
     * spent, which makes the timing matter.
     *
     * @return true when the blade actually went up.
     */
    public static boolean raiseGuard(Player player) {
        if (!hasSword(player)) {
            return false;
        }
        INinjaData ninjaData = player.getCapability(
                com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !AbilityUnlocks.isUnlocked(ninjaData, ID)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (now < player.getPersistentData().getLong(READY_TAG)) {
            return false;
        }
        float cost = Mastery.cost(ninjaData, NarutoAbilities.BLADE_PARRY.get(), GUARD_COST);
        if (ninjaData.getStamina() < cost) {
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        player.getPersistentData().putLong(UNTIL_TAG, now + GUARD_TICKS);
        player.getPersistentData().putLong(READY_TAG, now + GUARD_COOLDOWN);
        player.swing(InteractionHand.MAIN_HAND);

        if (player.level() instanceof ServerLevel level) {
            Vec3 guard = JutsuEffects.handPosition(player);
            level.sendParticles(ParticleTypes.END_ROD, guard.x, guard.y, guard.z,
                    6, 0.15d, 0.15d, 0.15d, 0.01d);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.35f, 1.5f);
        }
        return true;
    }

    /** @return whether the blade is up right now, asked by the hit hook. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** Spends the raised guard after it has met a blow. */
    public static void consume(Player player) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data instead. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** @return whether the caster is holding a sword in the main hand, which the guard needs. */
    public static boolean hasSword(Player player) {
        return player.getMainHandItem().getItem() instanceof SwordItem;
    }
}
