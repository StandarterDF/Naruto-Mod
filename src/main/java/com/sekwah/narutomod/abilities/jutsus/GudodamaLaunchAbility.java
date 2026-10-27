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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A Truth-Seeking Orb thrown as a bolt.
 *
 * <p>The orbs are the mode's ammunition, so this is the plainest way to spend one: the black sphere
 * goes out along the caster's look, stops at the first wall or body in the way, and comes apart there.
 * Unlike a fireball it does not care what the ground is made of - it leaves no crater and no fire, it
 * simply erases what it touches. Everything within four blocks of the impact takes the same blow,
 * allies excluded.</p>
 *
 * <p>A thrown orb is gone. The count on the caster goes down by one, and when it reaches zero the
 * mode itself closes; see {@link SixPathsModeAbility}.</p>
 */
public class GudodamaLaunchAbility extends Ability implements Ability.Cooldown {

    /** The black sphere is quiet until it lands, and then the whole field knows. */
    @Override
    public int noiseRadius() {
        return 20;
    }

    /** C C C C B C - the orb seal. */
    public static final long COMBO = 2112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gudodama_launch");

    /** How far the bolt flies before it fizzles out. */
    private static final double RANGE = 24.0d;

    /** What one orb costs to throw, and how long before another can follow. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 20;

    /** The blow the impact deals to everything caught in it. */
    private static final float DAMAGE = 20.0f;

    /** The impact bubble. */
    private static final double RADIUS = 4.0d;

    /** Points along the flight the trail is drawn on. */
    private static final int TRAIL_POINTS = 20;

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
        if (SixPathsModeAbility.orbs(player) <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gudodama.no_orbs",
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
        // The cost was paid against a check a moment ago; the orb is taken only if there is one, so a
        // double packet or a stale count cannot turn into a free throw.
        if (SixPathsModeAbility.orbs(player) <= 0) {
            return;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 impact = findImpact(level, player, eye, look);

        drawTrail(level, eye, impact);
        level.sendParticles(ParticleTypes.SQUID_INK, impact.x, impact.y, impact.z,
                40, RADIUS * 0.5d, RADIUS * 0.5d, RADIUS * 0.5d, 0.05d);
        level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z,
                16, RADIUS * 0.4d, RADIUS * 0.4d, RADIUS * 0.4d, 0.08d);
        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.2f, 0.8f);

        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(RADIUS), target -> isValidTarget(player, target));
        for (LivingEntity target : caught) {
            target.hurt(level.damageSources().indirectMagic(player, player), DAMAGE);
        }

        SixPathsModeAbility.spendOrb(player);
    }

    /**
     * Where the bolt stops: the first body under the look, or the first block, whichever comes first.
     * Worked out from the eye along the exact view vector so the impact lands where the caster aimed.
     */
    private static Vec3 findImpact(ServerLevel level, Player caster, Vec3 eye, Vec3 look) {
        Vec3 reach = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, reach,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        double limit = block.getType() == HitResult.Type.BLOCK
                ? eye.distanceTo(block.getLocation())
                : RANGE;
        Vec3 end = eye.add(look.scale(limit));
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(limit)).inflate(1.0d);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(caster, eye, end, searchBox,
                entity -> entity != caster && !entity.isSpectator() && entity.isPickable(),
                limit * limit);
        if (entityHit != null) {
            return entityHit.getLocation();
        }
        return block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
    }

    /** The black streak the orb leaves behind it, so the throw is legible in flight. */
    private static void drawTrail(ServerLevel level, Vec3 from, Vec3 to) {
        for (int i = 1; i <= TRAIL_POINTS; i++) {
            double t = (double) i / TRAIL_POINTS;
            level.sendParticles(ParticleTypes.SQUID_INK,
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    private static boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && other.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
