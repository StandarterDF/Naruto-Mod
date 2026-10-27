package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Eight Trigrams: Mountain Collapse, the whole corridor of palms at once.
 *
 * <p>The gentle fist closes one body's channels at a time, which is exactly as slow as it sounds
 * against a line of them. This is the technique that stops being polite about it: eight paces of
 * palms are walked in a single motion, and everything standing in the lane they cut through is
 * struck, thrown off its feet and left moving like a body under water. It is the Hyuga answer to a
 * corridor, a doorway or a rank that has decided to come at the caster together.</p>
 *
 * <p>The lane is the caster's own gaze, eight blocks deep and three wide, worked out in block steps
 * so it follows the aim rather than snapping to a grid. It breaks no blocks and starts no fire - the
 * damage is chakra through the channels, not a blast - and it leaves the caster's allies standing,
 * because a technique that cannot be used next to a teammate is a technique that never gets used.</p>
 *
 * <p>The eye has to be open, see {@link ByakuganAbility}: without the reading there is no way to
 * strike a dozen channels and be sure of any of them.</p>
 */
public class MountainCollapseAbility extends Ability implements Ability.Cooldown {

    /** Eight palms landing in a row carry a long way. */
    @Override
    public int noiseRadius() {
        return 18;
    }

    /** V B V V B B - the seal of the soft fist, doubled at the end. */
    public static final long COMBO = 1322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "eight_trigrams_collapse");

    /** How deep the lane runs, in block steps taken along the look vector. */
    private static final int STEPS = 8;

    /**
     * How far each step reaches sideways and up. One and a half blocks each way is a three-wide lane,
     * which is what a corridor of bodies actually occupies.
     */
    private static final double HALF_SIZE = 1.5d;

    /** What the technique costs and how long the eye needs before the lane can be walked again. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 360;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** The strike: the damage, the throw into the air and the weight left in the limbs. */
    private static final float DAMAGE = 8.0f;
    private static final double LIFT = 0.55d;
    private static final int SLOW_TICKS = 80;
    private static final int SLOW_AMPLIFIER = 1;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                    true);
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
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 origin = player.position().add(0.0d, player.getBbHeight() * 0.5d, 0.0d);

        // One pass per step rather than one large box: the lane follows the gaze, so a caster looking
        // slightly down still catches the bodies on the floor in front of them.
        Set<LivingEntity> struck = new HashSet<>();
        for (int step = 1; step <= STEPS; step++) {
            Vec3 at = origin.add(look.scale(step));
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z,
                    5, HALF_SIZE * 0.5d, 0.2d, HALF_SIZE * 0.5d, 0.01d);
            AABB cell = new AABB(
                    at.x - HALF_SIZE, at.y - HALF_SIZE, at.z - HALF_SIZE,
                    at.x + HALF_SIZE, at.y + HALF_SIZE, at.z + HALF_SIZE);
            // A body can stand in more than one cell; the set is what keeps it from being hit twice.
            struck.addAll(level.getEntitiesOfClass(LivingEntity.class, cell,
                    entity -> canStrike(player, entity)));
        }
        for (LivingEntity target : struck) {
            collapse(player, target);
        }

        // The end of the lane is where the last palm landed, so that is where the dust is thrown up.
        Vec3 far = origin.add(look.scale(STEPS));
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, far.x, far.y, far.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, far.x, far.y, far.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.4f, 1.3f);
    }

    /** One body caught in the lane: struck, thrown off its feet and left heavy. */
    private static void collapse(Player caster, LivingEntity target) {
        target.hurt(caster.damageSources().magic(), DAMAGE);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                SLOW_TICKS, SLOW_AMPLIFIER, false, true));
        // Thrown straight up rather than away: the lane is narrow, so a body leaving it sideways
        // would be a body the technique did not need to reach.
        target.setDeltaMovement(target.getDeltaMovement().x, LIFT, target.getDeltaMovement().z);
        target.hurtMarked = true;
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
