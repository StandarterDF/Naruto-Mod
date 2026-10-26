package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The grass-cutting sweep: a scythe stroke that takes a field in one pass.
 *
 * <p>A sword is a poor harvest tool and a ninja is often a farmer. This is the compromise the scythe
 * teaches: the blade is swung low and flat, and the stroke is not meant for a body at all but for
 * everything growing in a small box in front of the caster - ripe crops, grass and ferns, and the
 * leaves hanging at head height. It cuts what a hand could pick, drops it as the hand would, and
 * leaves the soil, the stone and the trunks where they were.</p>
 *
 * <p>What it refuses to touch matters as much as what it takes: only crops, bushes and leaves are
 * cut, so a swing in a store room does not open a chest, and a swing at a wall does not mine it.</p>
 */
public class KusaGiriAbility extends Ability implements Ability.Cooldown {

    /** A sweep through a field is heard across a yard, but not across a village. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 1 3 2 2 2 3 - the kenjutsu seals and the two keys of the scythe stroke. */
    public static final long COMBO = 132223L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kusa_giri");

    /** What the stroke costs, and how long the arm needs before the next one. */
    private static final float COST = 10.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 80;

    /** How far in front of the caster the box starts, in blocks. */
    private static final int FORWARD = 2;

    /** Half-width and half-depth of the box, and how tall it stands. */
    private static final int REACH = 1;
    private static final int HEIGHT = 2;

    /** How many motes are drawn along the stroke, whatever it found. */
    private static final int SWEEP_PARTICLES = 8;

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
        Vec3 look = player.getViewVector(1.0f);
        int stepX = Math.abs(look.x) >= Math.abs(look.z) ? (int) Math.signum(look.x) : 0;
        int stepZ = stepX == 0 ? (int) Math.signum(look.z) : 0;
        if (stepX == 0 && stepZ == 0) {
            // Looking straight down a hole still cuts something: the stroke follows the body.
            Direction facing = player.getDirection();
            stepX = facing.getStepX();
            stepZ = facing.getStepZ();
        }
        // The box is laid out along the stroke and across it, so a diagonal swing covers the ground
        // in front of the caster rather than a square of the compass.
        int sideX = stepZ;
        int sideZ = stepX;

        BlockPos centre = player.blockPosition().offset(stepX * FORWARD, 0, stepZ * FORWARD);
        int cut = 0;
        for (int depth = -REACH; depth <= REACH; depth++) {
            for (int side = -REACH; side <= REACH; side++) {
                BlockPos column = centre.offset(stepX * depth + sideX * side, 0, stepZ * depth + sideZ * side);
                for (int up = 0; up < HEIGHT; up++) {
                    BlockPos pos = column.above(up);
                    if (harvestable(level.getBlockState(pos)) && level.mayInteract(player, pos)) {
                        // Broken with the caster as the breaker, so the drops are the caster's and a
                        // hoe's or a hand's fortune applies exactly as it would have by hand.
                        level.destroyBlock(pos, true, player);
                        cut++;
                    }
                }
            }
        }

        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, hand.x, hand.y - 0.4d, hand.z,
                SWEEP_PARTICLES, 0.8d, 0.1d, 0.8d, 0.0d);
        if (cut > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.4f);
        }
    }

    /** @return whether the block is something a hand could take: a ripe crop, a bush or leaves. */
    private static boolean harvestable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        // Crops come first: they are bushes too, and only a ripe one is worth cutting.
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.getBlock() instanceof BushBlock || state.getBlock() instanceof LeavesBlock;
    }
}
