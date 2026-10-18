package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.mixin.common.AbstractFurnaceAccessor;
import com.sekwah.narutomod.mixin.common.CampfireAccessor;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A jet of burning chakra breathed out in front of the caster: a cone of flame held open for as long
 * as the keys stay down.
 *
 * <p>It is a channelled technique, and the whole thing is the channel: every tick it costs chakra,
 * sprays flame and scorches whatever is caught in the cone. Holding it longer reaches further and
 * burns hotter, up to a point, and it pays for its mastery by the time it is actually lit rather than
 * by the act of starting it.</p>
 *
 * <p>It is a breath of hot chakra, not fire: it hurts, but it <b>never</b> sets a target or a block
 * alight and never spreads. What it does do to the world is dry a wet sponge it is aimed at and fan a
 * furnace or a campfire, driving the cook along without fuel - see
 * {@link #applyBlockHeat(ServerLevel, Player)}.</p>
 */
public class FireBreathJutsuAbility extends Ability implements Ability.Channeled {

    /** C V B C */
    public static final long COMBO = 1231L;

    /** Chakra burned for every tick the flame is held. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    /** A tap that never held costs a flat puff instead of a tick. */
    private static final float TAP_COST = 10.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far the breath reaches, and the cosine of half the cone it fills; both grow with mastery. */
    private static final double RANGE_MIN = 4.0d;
    private static final double RANGE_MAX = 7.0d;
    private static final double CONE_COS = 0.55d;

    /** Damage every {@link #DAMAGE_INTERVAL} ticks to anything caught in the cone, by mastery. */
    private static final float DAMAGE_MIN = 3.0f;
    private static final float DAMAGE_MAX = 6.0f;
    private static final int DAMAGE_INTERVAL = 20;

    /** How hard a breath tick drives a furnace along, and how long it keeps it lit. */
    private static final int FURNACE_COOK_BOOST = 2;
    private static final int FURNACE_LIT_BOOST = 40;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = chargeAmount < 0 ? TAP_COST : this.masteryCost(ninjaData, CHAKRA_PER_TICK);
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
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        float mastery = this.masteryLevel(ninjaData);
        if (ticksChanneled == 0) {
            level.playSound(null, player, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.0f);
        }
        emitFlame(level, player, mastery);
        applyBlockHeat(level, player);
        if (ticksChanneled % DAMAGE_INTERVAL == 0) {
            scorch(level, player, mastery);
        }
        // Fire breath is lived in, not cast: its mastery comes from the time the flame is actually on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // On release there is nothing left to do; the flame has already done its work tick by tick.
        // A tap that never held the keys is a short puff instead, enough to dry or fan a block.
        if (ticksActive < 0 && player.level() instanceof ServerLevel level) {
            float mastery = this.masteryLevel(ninjaData);
            emitFlame(level, player, mastery);
            applyBlockHeat(level, player);
            scorch(level, player, mastery);
        }
    }

    /** The cone of flame itself, purely for looks - it lights nothing. */
    private static void emitFlame(ServerLevel level, Player player, float mastery) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        for (int step = 1; step <= 6; step++) {
            double dist = range * step / 6.0d;
            double spread = 0.08d * step;
            Vec3 point = eye.add(look.scale(dist));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z,
                    3, spread, spread, spread, 0.01d);
            if (step % 2 == 0) {
                level.sendParticles(ParticleTypes.SMOKE, point.x, point.y, point.z,
                        1, spread, spread, spread, 0.0d);
            }
        }
    }

    /** Burns everything alive caught in the cone. Nothing is ever set on fire by it. */
    private static void scorch(ServerLevel level, Player player, float mastery) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double range = Mth.lerp(mastery, RANGE_MIN, RANGE_MAX);
        float damage = Mth.lerp(mastery, DAMAGE_MIN, DAMAGE_MAX);
        Vec3 centre = eye.add(look.scale(range * 0.5d));
        AABB area = new AABB(centre, centre).inflate(range);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !entity.isAlliedTo(player))) {
            Vec3 toTarget = target.getEyePosition().subtract(eye);
            if (toTarget.lengthSqr() > range * range) {
                continue;
            }
            if (toTarget.normalize().dot(look) < CONE_COS) {
                continue;
            }
            target.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.FIRE_BREATH, player),
                    damage);
        }
    }

    /**
     * What the breath does to the block in front of it: a wet sponge is dried back into a sponge, and
     * a furnace or a campfire is driven along as if the flame itself were stoking it - the cook is
     * nudged forward and a furnace is kept lit, so a furnace with no fuel can still be worked with
     * nothing but the breath. A campfire already burns on its own, so it only needs the nudge.
     */
    private static void applyBlockHeat(ServerLevel level, Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE_MAX);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (level.getBlockState(pos).is(Blocks.WET_SPONGE)) {
            level.setBlockAndUpdate(pos, Blocks.SPONGE.defaultBlockState());
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.CLOUD,
                    pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d,
                    8, 0.4d, 0.2d, 0.4d, 0.02d);
            return;
        }
        if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
            AbstractFurnaceAccessor accessor = (AbstractFurnaceAccessor) furnace;
            int total = accessor.naruto$cookingTotalTime();
            // Nudge the current cook along without overshooting: the furnace only completes a cook
            // when its own tick lands exactly on cookingTotalTime, so we stop one short of it.
            if (total > 1 && !furnace.getItem(0).isEmpty()) {
                accessor.naruto$setCookingProgress(Math.min(total - 1,
                        accessor.naruto$cookingProgress() + FURNACE_COOK_BOOST));
            }
            int lit = Math.max(accessor.naruto$litTime(), FURNACE_LIT_BOOST);
            accessor.naruto$setLitTime(lit);
            accessor.naruto$setLitDuration(Math.max(accessor.naruto$litDuration(), lit));
            furnace.setChanged();
        }
        if (level.getBlockEntity(pos) instanceof CampfireBlockEntity campfire) {
            CampfireAccessor accessor = (CampfireAccessor) campfire;
            int[] progress = accessor.naruto$cookingProgress();
            int[] time = accessor.naruto$cookingTime();
            boolean changed = false;
            for (int slot = 0; slot < progress.length && slot < time.length; slot++) {
                // A slot with nothing cooking has a total of zero, and the campfire only completes
                // when its own tick reaches the total, so we stop one short of it.
                if (time[slot] > 1 && progress[slot] < time[slot]) {
                    progress[slot] = Math.min(time[slot] - 1, progress[slot] + FURNACE_COOK_BOOST);
                    changed = true;
                }
            }
            if (changed) {
                campfire.setChanged();
            }
        }
    }
}
