package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * Suggestion: a look that quietly takes the caster out of a creature's mind.
 *
 * <p>An open Sharingan can plant one small thought in a mind that is already fixed on the caster -
 * <em>this one is not here</em> - and the creature drops what it was doing. It does not become a
 * friend and it is not held still: it simply forgets the caster for eight seconds, and the fight it
 * was in has to be started again from the beginning. A creature too strong to have a mind worth
 * bending, measured by a body that can take a hundred points of punishment, shrugs it off.</p>
 *
 * <p>The window is for walking away or for setting up the next technique, and it closes the moment
 * the caster spoils it by attacking: the mark left here is what the caster's own blows clear.</p>
 */
public class SuggestionAbility extends Ability implements Ability.Cooldown {

    /** A suggestion is said, not shouted: it carries about as far as a conversation. */
    @Override
    public int noiseRadius() {
        return 6;
    }

    /** C V V B B C - the Sharingan combo (C V V B) and two more keys. */
    public static final long COMBO = 122312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "suggestion");

    /** Game time at which the creature starts seeing the caster again. Read by the upkeep hook. */
    public static final String UNTIL_TAG = "narutomod:suggestion_until";

    /** UUID string of the ninja the creature has been made to forget. */
    public static final String SOURCE_TAG = "narutomod:suggestion_source";

    /** How far down the look ray the suggestion can be laid. Walls stop it, as they stop every eye. */
    private static final double RANGE = 8.0d;

    /** What it costs and how long before the eye can suggest again. */
    private static final float COST = 35.0f;
    private static final int COOLDOWN = 400;

    /** How long the creature forgets the caster - the same 8 seconds the tag stores, in ticks. */
    private static final int FORGET_TICKS = 160;

    /**
     * Where a body stops being a mind that can be nudged. Anything that can absorb a hundred points
     * of damage is a boss as far as a suggestion is concerned; the test is the health pool rather
     * than the entity class because the mod's own bosses are ordinary subclassed mobs.
     */
    private static final float BOSS_HEALTH = 100.0f;

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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob) || !isValidTarget(mob)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.suggestion.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (isTooStrong(mob)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.suggestion.boss",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob)) {
            return;
        }
        // The look was paid for a moment ago; the mob can have died or wandered out of reach since.
        if (!isValidTarget(mob) || isTooStrong(mob)) {
            return;
        }

        mob.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + FORGET_TICKS);
        mob.getPersistentData().putString(SOURCE_TAG, player.getUUID().toString());

        // The creature loses the caster and everything that reminded it of the caster. Any target is
        // dropped, and so is the memory of the last body that hurt it, or the next hurt goal would
        // hand the caster straight back to it.
        mob.setTarget(null);
        mob.setLastHurtByMob(null);

        level.sendParticles(ParticleTypes.WITCH, mob.getX(),
                mob.getY() + mob.getBbHeight() + 0.3d, mob.getZ(),
                12, 0.35d, 0.25d, 0.35d, 0.01d);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    /** A suggestion needs a living mind that is still in the fight, not a corpse or an onlooker. */
    private boolean isValidTarget(Mob mob) {
        return mob.isAlive() && !mob.isSpectator() && !mob.isRemoved();
    }

    /** @return whether the mob's body is too strong for a small thought to land. */
    private boolean isTooStrong(Mob mob) {
        return mob.getMaxHealth() >= BOSS_HEALTH;
    }
}
