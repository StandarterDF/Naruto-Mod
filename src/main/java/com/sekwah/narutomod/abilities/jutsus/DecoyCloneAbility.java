package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Decoy clone: two empty copies left standing where the caster was looked at.
 *
 * <p>This is the clone technique of a ninja who wants to be somewhere else. The copies carry no
 * equipment, hold no seal and never swing at anything - they are bait, and their whole worth is that
 * a mob picks them over the real body. One hit pops them, which is the point: a decoy that survives a
 * beating would be a second life, not a trick.</p>
 *
 * <p>The copies are tagged for the world hooks (see {@link #DECOY_KEY} and {@link #DECOY_UNTIL_KEY}),
 * which is what makes mobs prefer them and what takes them off the field once the trick has run its
 * course. Nothing in this class decides who is fooled - it only puts the bait down.</p>
 */
public class DecoyCloneAbility extends Ability implements Ability.Cooldown {

    /** B A A A - the cheap clone seal, three keys shorter than the shadow clone. */
    public static final long COMBO = 2111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clone_decoy");

    /**
     * Marks a clone as bait. The hooks that steer mob aggro and expire the decoys read this, so the
     * name is part of the contract rather than an implementation detail.
     */
    public static final String DECOY_KEY = "narutomod:decoy_clone";

    /** Game time at which the decoy stops being worth chasing. */
    public static final String DECOY_UNTIL_KEY = "narutomod:decoy_until";

    /** Two copies: one would just be a clone, three would be a crowd. */
    private static final int COPIES = 2;

    private static final float COST = 15.0f;
    private static final int COOLDOWN = 120;

    /** How long the bait stands, and the window the world hooks read off {@link #DECOY_UNTIL_KEY}. */
    private static final int DURATION = 160;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Close enough that the copies read as having stepped out of the caster. */
    private static final double SPAWN_RADIUS = 1.4d;

    /** A decoy is a small trick, not a battle cry. */
    private static final int NOISE_RADIUS = 12;

    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        long until = level.getGameTime() + DURATION;
        for (int i = 0; i < COPIES; i++) {
            level.addFreshEntity(this.spawnDecoy(level, player, until, i));
        }

        level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                40, 0.6d, 0.7d, 0.6d, 0.02d);
        level.playSound(null, player, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }

    /**
     * Builds one copy.
     *
     * <p>The bare entity constructor is used on purpose: the fighting-clone constructor copies the
     * caster's gear, arrows and paper bomb, and a decoy must have none of those - an exploding decoy
     * would turn a defensive trick into a bomb the caster cannot aim.</p>
     */
    private ShadowCloneEntity spawnDecoy(ServerLevel level, Player player, long until, int index) {
        ShadowCloneEntity decoy = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        decoy.setOwner(player);
        decoy.setHealth(1.0f);

        // Nothing to hold and nothing to swing: the copies only stand there.
        decoy.goalSelector.removeAllGoals(goal -> true);
        decoy.targetSelector.removeAllGoals(goal -> true);

        decoy.getPersistentData().putBoolean(DECOY_KEY, true);
        decoy.getPersistentData().putLong(DECOY_UNTIL_KEY, until);

        position(decoy, level, player, index);
        return decoy;
    }

    /** Puts the copies on either side of the caster, closing in if something is in the way. */
    private static void position(ShadowCloneEntity decoy, ServerLevel level, Player player, int index) {
        double angle = Math.PI * index;
        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            decoy.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            if (level.noCollision(decoy, decoy.getBoundingBox())) {
                break;
            }
        }
    }
}
