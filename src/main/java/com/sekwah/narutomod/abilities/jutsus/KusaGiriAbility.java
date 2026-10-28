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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
public class KusaGiriAbility extends Ability implements Ability.Cooldown, Ability.Channeled {

    /** A sweep through a field is heard across a yard, but not across a village. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 1 3 2 2 2 3 - the kenjutsu seals and the two keys of the scythe stroke. */
    public static final long COMBO = 212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kusa_giri");

    /** What the stroke costs, and how long the arm needs before the next one. */
    private static final float COST = 10.0f;
    private static final float HOLD_COST = 1.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 80;

    /** Base reach of a tap, and the furthest a fully trained caster can reach. */
    private static final int BASE_RANGE = 3;
    private static final int MAX_RANGE = 30;

    /** Ticks of holding needed to extend the slash by one block. */
    private static final int TICKS_PER_BLOCK = 2;

    /** How tall the slash stands. */
    private static final int HEIGHT = 2;

    /** How many motes are drawn along the stroke, whatever it found. */
    private static final int SWEEP_PARTICLES = 8;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
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
        if (chargeAmount < 0) {
            float cost = this.masteryCost(ninjaData, COST);
            if (ninjaData.getStamina() < cost) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
                return false;
            }
            ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
            return true;
        }
        // Holding the slash open costs a little every tick.
        float cost = this.masteryCost(ninjaData, HOLD_COST);
        if (ninjaData.getStamina() < cost) {
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
            Direction facing = player.getDirection();
            stepX = facing.getStepX();
            stepZ = facing.getStepZ();
        }

        int maxRange = BASE_RANGE + Math.round(this.masteryLevel(ninjaData) * (MAX_RANGE - BASE_RANGE));
        int range;
        if (ticksActive < 0) {
            range = BASE_RANGE;
        } else {
            range = Math.min(maxRange, BASE_RANGE + ticksActive / TICKS_PER_BLOCK);
        }
        range = Math.max(BASE_RANGE, range);

        int cut = 0;
        for (int d = 1; d <= range; d++) {
            BlockPos pos = player.blockPosition().offset(stepX * d, 0, stepZ * d);
            for (int up = 0; up < HEIGHT; up++) {
                BlockPos p = pos.above(up);
                if (harvestable(level.getBlockState(p)) && level.mayInteract(player, p)) {
                    level.destroyBlock(p, true, player);
                    cut++;
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
        // Grasses and ferns are not always BushBlocks depending on the version, so name them
        // explicitly - this is the whole point of a grass-cutting sweep.
        if (state.is(Blocks.GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH)) {
            return true;
        }
        return state.getBlock() instanceof BushBlock || state.getBlock() instanceof LeavesBlock;
    }
}
