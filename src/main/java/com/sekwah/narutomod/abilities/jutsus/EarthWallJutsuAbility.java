package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import com.sekwah.narutomod.world.EarthWallSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Earth Wall: a slab of the ground itself pulled up as cover.
 *
 * <p>The wall is made of real blocks, so it stops arrows and mobs like any other wall - and because
 * it is borrowed ground rather than created stone, it sinks back into the earth after a while. What
 * was there before is remembered (see {@link EarthWallSavedData}) and put back, but only if the spot
 * still holds the block the wall put there: a wall that has been mined, replaced or built over is
 * left alone.</p>
 *
 * <p>Holding the technique grows the wall from knee height to a full five-block barrier, and only
 * the blocks actually placed are remembered, so aiming at a cliff does not create floating earth.</p>
 */
public class EarthWallJutsuAbility extends Ability implements Ability.Cooldown, Ability.Channeled {

    /** B C V. */
    public static final long COMBO = 312L;

    /** How wide the wall is, in blocks. */
    private static final int WIDTH = 5;

    /** How tall the wall gets when the technique is held to the end. */
    private static final int MAX_HEIGHT = 5;

    /** Ticks of holding needed for the full height. */
    private static final int FULL_CHARGE_TICKS = 40;

    /** How far the caster can raise the wall. */
    private static final double RANGE = 40.0d;

    /** Chakra per tick of holding. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    /** Chakra for a tap: a low wall, but not free. */
    private static final float TAP_COST = 5.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the wall stands before the ground takes it back. */
    private static final int LIFETIME_TICKS = 20 * 15;

    /** Ticks between two neighbouring blocks coming up, so the wall grows instead of popping. */
    private static final int RISE_TICKS = 2;

    /** Ticks between two neighbouring blocks sinking back (the crest goes first). */
    private static final int DECAY_TICKS = 2;

    /** How far up or down the wall base may search for solid ground. */
    private static final int MAX_BASE_SEARCH = 5;

    private static final int COOLDOWN = 3 * 20;

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
        // The cooldown is checked when the cast starts only: the tap and the first tick of a hold are
        // the two ways in, and a channel that is already running must not be interrupted by it.
        if (chargeAmount <= 0
                && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        if (Targeting.lookedAtBlock(player, RANGE) == null) {
            this.fail(player, ninjaData, "jutsu.fail.earthwall.notarget");
            return false;
        }
        float cost = chargeAmount < 0 ? TAP_COST : CHAKRA_PER_TICK;
        cost = this.masteryCost(ninjaData, cost);
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
        if (!(player.level() instanceof ServerLevel level) || ticksChanneled % 4 != 0) {
            return;
        }
        // A little dust at the foot of the wall, so the caster can see the ground working.
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        level.sendParticles(ParticleTypes.CLOUD,
                pos.getX() + 0.5d, pos.getY() + 0.2d, pos.getZ() + 0.5d,
                4, 0.8d, 0.15d, 0.8d, 0.01d);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int height = heightFor(ticksActive);
        List<BlockPlacement> placements = calculateWall(level, player, height);
        if (placements.isEmpty()) {
            return;
        }
        EarthWallSavedData walls = EarthWallSavedData.get(level);
        long now = level.getGameTime();
        // Each block waits its turn: columns rise from the middle outwards and every column grows
        // from the ground up. On the way out the crest goes first, so the wall crumbles downwards.
        for (BlockPlacement placement : placements) {
            long riseAt = now + (long) placement.level() * RISE_TICKS + placement.swing();
            long decay = (long) (height - 1 - placement.level()) * DECAY_TICKS;
            walls.schedule(placement.pos(), placement.state(), riseAt,
                    now + LIFETIME_TICKS + decay);
        }
        Vec3 center = new Vec3(placements.get(0).pos().getX() + 0.5d,
                placements.get(0).pos().getY() + 0.5d,
                placements.get(0).pos().getZ() + 0.5d);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        // A wall that actually rose is what the cooldown is for; an aim that produced nothing costs
        // only the chakra that was spent holding the technique.
        this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
    }

    /** Tap raises a low ridge; holding grows it to the full barrier. */
    private static int heightFor(int ticksActive) {
        if (ticksActive < 0) {
            return 1;
        }
        int ticks = Math.min(ticksActive, FULL_CHARGE_TICKS);
        return 1 + Math.round(ticks / (float) FULL_CHARGE_TICKS * (MAX_HEIGHT - 1));
    }

    private void fail(Player player, INinjaData ninjaData, String key) {
        player.displayClientMessage(Component.translatable(key,
                Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Works out the blocks of the wall: five columns across the caster's line of sight, each rising
     * from the ground under it. Every column is independent, so uneven ground makes an even crest
     * rather than a floating rectangle.
     */
    private static List<BlockPlacement> calculateWall(ServerLevel level, Player player, int height) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return List.of();
        }
        WallDir dir = directionFor(player.getYHeadRot());
        BlockPos target = hit.getBlockPos();
        List<BlockPlacement> placements = new ArrayList<>();
        for (int offset = -(WIDTH - 1) / 2; offset <= (WIDTH - 1) / 2; offset++) {
            BlockPos column = target.offset(dir.x() * offset, 0, dir.z() * offset);
            BlockPos base = findBase(level, column);
            if (base == null) {
                continue;
            }
            BlockState material = materialFor(level, base);
            int swing = Math.abs(offset);
            for (int i = 0; i < height; i++) {
                BlockPos pos = base.above(i);
                if (!level.getBlockState(pos).canBeReplaced() || isOccupied(level, pos)) {
                    continue;
                }
                placements.add(new BlockPlacement(pos, material, i, swing));
            }
        }
        return placements;
    }

    /** The first solid footing at or near the aimed column, or null if that column cannot stand. */
    @Nullable
    private static BlockPos findBase(Level level, BlockPos column) {
        BlockPos.MutableBlockPos cursor = column.mutable();
        // The aimed block itself may be the ground, so start just above it when it is solid.
        if (!level.getBlockState(cursor).canBeReplaced()) {
            cursor.move(0, 1, 0);
        }
        for (int i = 0; i < MAX_BASE_SEARCH && !level.getBlockState(cursor).canBeReplaced(); i++) {
            cursor.move(0, 1, 0);
        }
        if (!level.getBlockState(cursor).canBeReplaced()) {
            return null;
        }
        // Then sink to the ground, so the wall does not hang in the air over a dip.
        for (int i = 0; i < MAX_BASE_SEARCH && level.getBlockState(cursor.below()).canBeReplaced(); i++) {
            cursor.move(0, -1, 0);
        }
        return cursor.immutable();
    }

    /** Earth makes an earth wall: whatever the ground under the column is made of, or plain dirt. */
    private static BlockState materialFor(Level level, BlockPos base) {
        BlockState ground = level.getBlockState(base.below());
        if (ground.is(Blocks.GRASS_BLOCK)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.BASE_STONE_OVERWORLD)
                || ground.is(BlockTags.SAND)) {
            return ground;
        }
        return Blocks.DIRT.defaultBlockState();
    }

    /** Whether a creature is standing in the block, so the wall never traps or suffocates anyone. */
    private static boolean isOccupied(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty();
    }

    /**
     * The wall runs across the caster's view, snapped to one of four directions like the original
     * version did, so it is easy to raise a wall that covers the way you are facing.
     */
    private static WallDir directionFor(float yaw) {
        int dir = (int) (Math.floor(((yaw + 180.0d) * 8.0d) / 360.0d + 0.5d)) % 4;
        return switch (dir) {
            case 0 -> new WallDir(1, 0);
            case 1 -> new WallDir(1, 1);
            case 2 -> new WallDir(0, 1);
            default -> new WallDir(1, -1);
        };
    }

    private record BlockPlacement(BlockPos pos, BlockState state, int level, int swing) {
    }

    private record WallDir(int x, int z) {
    }
}
