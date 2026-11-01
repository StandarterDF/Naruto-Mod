package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.entity.jutsuprojectile.TravellingArcEntity;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The grass-cutting sweep: a scythe stroke that clears a wide fan of growth in one pass.
 *
 * <p>A sword is a poor harvest tool and a ninja is often a farmer. This is the compromise the scythe
 * teaches: the blade is swung low and flat in an eighty-degree arc, reaching well beyond arm's length,
 * and the stroke is not meant for a body at all but for everything growing in front of the caster -
 * ripe crops, grass and ferns, flowers and leaves, and the bushes and vines at head height. It cuts
 * what a hand could pick, drops it as the hand would, and leaves the soil, the stone and the trunks
 * where they were.</p>
 *
 * <p>What it refuses to touch matters as much as what it takes: only crops, bushes, flowers and
 * leaves are cut, so a swing in a store room does not open a chest, and a swing at a wall does not
 * mine it.</p>
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
    private static final float COST = 12.0f;
    private static final float HOLD_COST = 1.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 100;

    /** Base reach of a tap, and the furthest a fully trained caster can reach. */
    private static final int BASE_RANGE = 6;
    private static final int MAX_RANGE = 30;

    /** Ticks of holding needed to extend the slash by one block. */
    private static final int TICKS_PER_BLOCK = 2;

    /** How tall the slash stands. */
    private static final int HEIGHT = 3;

    /** Half of the 80 degree fan the blade covers. */
    private static final double HALF_ANGLE_DEGREES = 40.0d;

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
        double horizontal = Math.hypot(look.x, look.z);
        double dirX;
        double dirZ;
        if (horizontal < 1.0e-4d) {
            Direction facing = player.getDirection();
            dirX = facing.getStepX();
            dirZ = facing.getStepZ();
        } else {
            dirX = look.x / horizontal;
            dirZ = look.z / horizontal;
        }

        int maxRange = BASE_RANGE + Math.round(this.masteryLevel(ninjaData) * (MAX_RANGE - BASE_RANGE));
        int range;
        if (ticksActive < 0) {
            range = BASE_RANGE;
        } else {
            range = Math.min(maxRange, BASE_RANGE + ticksActive / TICKS_PER_BLOCK);
        }
        range = Math.max(BASE_RANGE, range);

        double minDot = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));
        BlockPos origin = player.blockPosition();
        int cut = 0;
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq == 0 || distanceSq > range * range) {
                    continue;
                }
                // Outside the fan: the blade never reaches there.
                if ((dx * dirX + dz * dirZ) / Math.sqrt(distanceSq) < minDot) {
                    continue;
                }
                for (int up = 0; up < HEIGHT; up++) {
                    BlockPos pos = origin.offset(dx, up, dz);
                    if (harvestable(level.getBlockState(pos)) && level.mayInteract(player, pos)) {
                        cut += cutPlant(level, pos, player);
                    }
                }
            }
        }

        TravellingArcEntity wave = new TravellingArcEntity(level, player, range,
                Math.cos(Math.toRadians(HALF_ANGLE_DEGREES)), 0.0f,
                TravellingArcEntity.KIND_VISUAL, false, 0, 0.0f);
        level.addFreshEntity(wave);

        if (cut > 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.4f);
        }
    }

    private static int cutPlant(ServerLevel level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!harvestable(state)) {
            return 0;
        }
        Block block = state.getBlock();
        int destroyed = 0;
        if (block instanceof DoublePlantBlock) {
            DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
            BlockPos other = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(other);
            boolean otherHarvestable = harvestable(otherState) && !otherState.isAir();
            // Avoid re-destroying a half the loop will reach on its own.
            if (otherHarvestable && !level.getBlockState(other).isAir()) {
                if (level.destroyBlock(other, true, player)) {
                    destroyed++;
                }
            }
        }
        // Destroy the targeted block itself last so the second half check above stays valid.
        if (!level.getBlockState(pos).isAir()) {
            if (level.destroyBlock(pos, true, player)) {
                destroyed++;
            }
        }
        return destroyed;
    }

    /** @return whether the block is something a hand could take: a ripe crop, a bush or leaves. */
    private static boolean harvestable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        Block block = state.getBlock();
        // Crops come first: they are bushes too, and only a ripe one is worth cutting.
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        // Every flower, including the two block tall ones.
        if (block instanceof FlowerBlock || block instanceof DoublePlantBlock) {
            return true;
        }
        if (state.is(Blocks.GRASS) || state.is(Blocks.FERN)
                || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.DEAD_BUSH) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BAMBOO) || state.is(Blocks.BAMBOO_SAPLING)
                || state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN)
                || state.is(Blocks.LILY_PAD) || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.CRIMSON_ROOTS) || state.is(Blocks.WARPED_ROOTS)
                || state.is(Blocks.NETHER_SPROUTS) || state.is(Blocks.HANGING_ROOTS)) {
            return true;
        }
        return block instanceof BushBlock || block instanceof LeavesBlock;
    }
}
