package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shibuki: the blast sword, a paper bomb on a handle.
 *
 * <p>Where the other great blades cut, this one detonates. The scroll wrapped into the blade is a
 * sealed charge, and the swing that brings it down sets it off in the air in front of the caster:
 * everything within two paces of the point is thrown, and nothing is cut at all. The sword is a
 * delivery, not an edge.</p>
 *
 * <p>Turned downward instead, the same charge is a miner's tool: a body pressed into the ground and
 * the blade swung into the stone opens a short tunnel without bringing a pickaxe. The stone is
 * broken out with its drops, because a tunnel that leaves nothing behind is a hole, not a passage.</p>
 */
public class ShibukiAbility extends Ability implements Ability.Cooldown {

    /** A detonation is heard across a village. */
    @Override
    public int noiseRadius() {
        return 20;
    }

    /** 1 3 2 3 3 2 - the kenjutsu seals and the two keys of the blast swing. */
    public static final long COMBO = 132332L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "shibuki");

    /** How far in front of the caster the charge is set off, and how far down the look a block is read. */
    private static final double RANGE = 4.0d;
    private static final double FORWARD = 2.0d;

    /** What the swing costs, and how long before the scroll is armed again. */
    private static final float COST = 30.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 300;

    /** The burst: its radius and what it does to a body inside it. */
    private static final double BURST_RADIUS = 2.5d;
    private static final float DAMAGE = 8.0f;

    /** How hard the burst throws what it does not kill. */
    private static final double KNOCKBACK = 0.6d;

    /** The tunnel: how deep it goes, and how tall it stands. */
    private static final int TUNNEL_DEPTH = 4;
    private static final int TUNNEL_HEIGHT = 2;

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
        if (player.isShiftKeyDown()) {
            dig(level, player);
        } else {
            burst(level, player);
        }
    }

    /** The charge set off in front of the caster: bodies thrown, the world left as it was. */
    private static void burst(ServerLevel level, Player player) {
        Vec3 centre = burstCentre(player);
        AABB box = new AABB(centre.x - BURST_RADIUS, centre.y - BURST_RADIUS, centre.z - BURST_RADIUS,
                centre.x + BURST_RADIUS, centre.y + BURST_RADIUS, centre.z + BURST_RADIUS);
        for (Entity entity : level.getEntities(player, box)) {
            if (!(entity instanceof LivingEntity target) || !canHit(player, target)) {
                continue;
            }
            if (target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d).distanceToSqr(centre)
                    > BURST_RADIUS * BURST_RADIUS) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), DAMAGE);
            Vec3 away = target.position().subtract(centre);
            if (away.lengthSqr() < 1.0E-4d) {
                away = player.getViewVector(1.0f);
            }
            away = away.normalize().scale(KNOCKBACK);
            target.setDeltaMovement(away.x, Math.max(0.35d, target.getDeltaMovement().y + 0.35d), away.z);
            target.hurtMarked = true;
        }

        // The explosion is drawn rather than made: nothing in the world is touched, so no explosion
        // entity is spawned and no griefing rule has to be consulted.
        level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z,
                3, 0.4d, 0.4d, 0.4d, 0.0d);
        level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y, centre.z,
                18, 0.6d, 0.6d, 0.6d, 0.02d);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.1f);
    }

    /** Where the charge goes off: the face the caster is looking at, or two paces ahead of the eyes. */
    private static Vec3 burstCentre(Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        Vec3 eye = player.getEyePosition();
        if (hit != null && hit.getLocation().distanceToSqr(eye) > 0.25d) {
            return hit.getLocation();
        }
        return eye.add(player.getViewVector(1.0f).scale(FORWARD));
    }

    /** The charge swung into the ground: a short passage through the stone, drops left in the world. */
    private static void dig(ServerLevel level, Player player) {
        Vec3 look = player.getViewVector(1.0f);
        int stepX = Math.abs(look.x) >= Math.abs(look.z) ? (int) Math.signum(look.x) : 0;
        int stepZ = stepX == 0 ? (int) Math.signum(look.z) : 0;
        if (stepX == 0 && stepZ == 0) {
            Direction facing = player.getDirection();
            stepX = facing.getStepX();
            stepZ = facing.getStepZ();
        }

        BlockPos cursor = player.blockPosition();
        int dug = 0;
        for (int depth = 0; depth < TUNNEL_DEPTH; depth++) {
            cursor = cursor.offset(stepX, 0, stepZ);
            for (int up = 0; up < TUNNEL_HEIGHT; up++) {
                BlockPos pos = cursor.above(up);
                BlockState state = level.getBlockState(pos);
                if (!breakable(state) || !level.mayInteract(player, pos)) {
                    continue;
                }
                level.destroyBlock(pos, true, player);
                dug++;
                level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5d, pos.getY() + 0.5d,
                        pos.getZ() + 0.5d, 1, 0.2d, 0.2d, 0.2d, 0.0d);
            }
        }
        if (dug > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.8f);
        }
    }

    /** @return whether the block is stone or ore, the two things this charge is meant for. */
    private static boolean breakable(BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.getBlock() instanceof DropExperienceBlock;
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canHit(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
