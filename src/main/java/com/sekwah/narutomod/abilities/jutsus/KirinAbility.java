package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.ElementalStates;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kirin: the sky itself answers the caster, but only in a thunderstorm.
 *
 * <p>This is deliberately not a bolt shape drawn by the mod - a real {@link LightningBolt} is put
 * down where the caster looks, so vanilla does everything a lightning strike does: it burns, it turns
 * a nearby creeper charged, it pulses a lightning rod (see {@code events/LightningEvents}) and it
 * hurts whatever is standing there. The technique only decides where the bolt lands and how it
 * treats soaked targets.</p>
 *
 * <p>Water conducts. A wet creature struck by the bolt takes extra damage, and the charge runs on to
 * other wet creatures nearby, hop by hop, weakening as it goes. That makes the water breath and this
 * technique a real combination rather than two unrelated elements: soak a crowd, then call the
 * storm.</p>
 */
public class KirinAbility extends Ability implements Ability.Cooldown {

    /** B B V V. */
    public static final long COMBO = 111113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kirin");

    /** Chakra paid for one strike. */
    private static final float COST = 80.0f;

    /** Cooldown in ticks: half a minute. Mastery shortens it like any other technique. */
    private static final int COOLDOWN = 20 * 30;

    /** How far the caster can aim the strike. */
    private static final double RANGE = 48.0d;

    /** How far from the landing point the strike counts as a direct hit. */
    private static final double DIRECT_RADIUS = 3.0d;

    /** How far the charge runs from a soaked creature to the next soaked one. */
    private static final double CHAIN_RADIUS = 8.0d;

    /** Extra damage a soaked target takes on top of the vanilla bolt. */
    private static final float WET_BONUS_DAMAGE = 8.0f;

    /** Damage of each hop in the chain, and how many hops the charge may take. */
    private static final float CHAIN_DAMAGE = 4.0f;
    private static final int CHAIN_HOPS = 2;

    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (!player.level().isThundering()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kirin.nostorm",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level) || !level.isThundering()) {
            return;
        }
        Vec3 strike = aimPoint(player);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(strike.x, strike.y, strike.z);
        bolt.setCause(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? serverPlayer : null);
        level.addFreshEntity(bolt);
        conduct(level, player, strike);
    }

    /** Where the bolt lands: the block under the crosshair, or a point along the line of sight. */
    private static Vec3 aimPoint(Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            return new Vec3(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d);
        }
        return player.getEyePosition().add(player.getViewVector(1.0f).scale(RANGE));
    }

    /**
     * Applies the wet part of the strike: the direct hit is stronger on a soaked creature, and the
     * charge then runs to other soaked creatures nearby, weakening with every hop.
     */
    private static void conduct(ServerLevel level, Player caster, Vec3 strike) {
        List<LivingEntity> direct = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(strike, strike).inflate(DIRECT_RADIUS),
                target -> target.isAlive() && target != caster && !caster.isAlliedTo(target)
                        && !Friends.areFriends(caster, target));
        Set<Integer> visited = new HashSet<>();
        Deque<LivingEntity> frontier = new ArrayDeque<>();
        for (LivingEntity target : direct) {
            if (ElementalStates.isWet(target)) {
                target.hurt(level.damageSources().lightningBolt(), WET_BONUS_DAMAGE);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        16, 0.4d, 0.4d, 0.4d, 0.05d);
                visited.add(target.getId());
                frontier.add(target);
            }
        }
        float damage = CHAIN_DAMAGE;
        for (int hop = 0; hop < CHAIN_HOPS && !frontier.isEmpty(); hop++) {
            int count = frontier.size();
            for (int i = 0; i < count; i++) {
                LivingEntity source = frontier.poll();
                if (source == null) {
                    continue;
                }
                List<LivingEntity> next = level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(source.position(), source.position())
                                .inflate(CHAIN_RADIUS),
                        target -> target.isAlive() && target != caster && !caster.isAlliedTo(target)
                                && !Friends.areFriends(caster, target)
                                && !visited.contains(target.getId()) && ElementalStates.isWet(target));
                for (LivingEntity target : next) {
                    if (!visited.add(target.getId())) {
                        continue;
                    }
                    target.hurt(level.damageSources().lightningBolt(), damage);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                            target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                            10, 0.3d, 0.3d, 0.3d, 0.04d);
                    frontier.add(target);
                }
            }
            damage *= 0.5f;
        }
    }
}
