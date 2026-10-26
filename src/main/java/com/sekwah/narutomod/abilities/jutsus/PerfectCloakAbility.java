package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The agreement rather than the cage: the beast's chakra worn over the body with its consent, and two
 * arms of it hanging at the caster's sides.
 *
 * <p>This is what a well-kept vessel buys. The cloak of a divided beast is thin; this one is a whole
 * tail's worth of chakra wrapped around one body, and it comes with limbs of its own - the arms strike
 * the closest bodies every second, whether or not the caster is swinging. The ninja is faster, far
 * harder to cut and heavy-handed with anything they touch.</p>
 *
 * <p>It asks for a vessel at least two fifths whole and wears it down slowly while it is worn, which
 * is the trade: the form is strong, so a caster cannot simply live in it. The arms and the wear are
 * enforced by {@code events/TailedBeastEvents}, because they happen whether or not anything is cast.</p>
 */
public class PerfectCloakAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A settled cloak rustles; it does not announce itself. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B B B B - the seal, then the whole tail. */
    public static final long COMBO = 332222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "perfect_cloak");

    /** The mark that says the cloak is settled, refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:perfect_cloak_until";

    /** A stable id, so the same modifier is re-applied rather than stacked. */
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("c4e07a83-59b1-42df-8a6e-0d73f2b41c96");

    /** Six points of force, on top of whatever the body already carries. */
    private static final double DAMAGE_BONUS = 6.0d;

    /** The vessel has to be at least this whole for the beast to agree to the cloak. */
    public static final int MIN_INTEGRITY = 40;

    /** How often the two arms strike, how far they reach and how hard they land. */
    public static final int ARM_INTERVAL = 20;
    public static final double ARM_RADIUS = 4.0d;
    public static final float ARM_DAMAGE = 5.0f;

    /** Chakra paid to settle the cloak, before mastery, and every tick it is worn. */
    private static final float OPEN_COST = 80.0f;
    private static final float UPKEEP = 1.0f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How long the boons are handed out for; refreshed every tick, so this is just a hold time. */
    private static final int EFFECT_DURATION = 200;

    /** How far ahead the mark is kept while the cloak is settled. */
    private static final int MARK_GRACE = 10;

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
        if (SealVesselAbility.integrity(player) < MIN_INTEGRITY) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.weak",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW),
                    MIN_INTEGRITY).withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= player.level().getGameTime();
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
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
        // A stance is mastered by the time it is worn, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        long now = player.level().getGameTime();
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;
        applyModifier(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 1, true, false));
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (opening) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    60, 0.7d, 1.0d, 0.7d, 0.04d);
            level.playSound(null, player, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.8f);
        } else if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.8d, player.getZ(),
                    3, 0.4d, 0.6d, 0.4d, 0.01d);
        }
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applyModifier(Player player) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(DAMAGE_MODIFIER_ID) == null) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID,
                    "narutomod:perfect_cloak", DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Removes the cloak's force from the caster's attributes. */
    public static void removeModifier(Player player) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }

    /** Takes the cloak off entirely: its mark, its force and its arms with it. */
    public static void clear(Player player) {
        player.getPersistentData().remove(UNTIL_TAG);
        removeModifier(player);
    }

    /** @return whether the cloak is currently settled on the caster. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** A stance is mastered by the time it is up, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The effects and the attribute are synced from the server, so there is nothing to do here.
    }

    /** Dropping the cloak takes its mark and its force with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        clear(player);
    }
}
