package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.jutsuprojectile.EarthWallEntity;
import com.sekwah.narutomod.util.Targeting;
import com.sekwah.narutomod.world.EarthWallSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Earth Wall: a slab of the ground itself pulled up as cover.
 *
 * <p>The wall is made of real blocks, so it stops arrows and mobs like any other wall - and because
 * it is borrowed ground rather than created stone, it sinks back into the earth after a while. What
 * was there before is remembered (see {@link EarthWallSavedData}) and put back, but only if the spot
 * still holds the block the wall put there: a wall that has been mined, replaced or built over is
 * left alone.</p>
 *
 * <p>The wall grows while the technique is held: the first channel tick raises knee-high earth and
 * every ten ticks or so another layer follows, so the caster can watch the barrier climb. A tap
 * raises a low ridge instead. Each block is first a {@link EarthWallEntity} that slides up out of
 * the ground, and only when it has arrived does it become a real block - vanilla blocks cannot move,
 * so the animation and the collision are split in two.</p>
 */
public class EarthWallJutsuAbility extends Ability implements Ability.Cooldown, Ability.Channeled {

    /** B C V. */
    public static final long COMBO = 312L;

    /** How wide the wall is, in blocks. */
    private static final int WIDTH = 5;

    /** Layers a tap raises, layers the first channel tick raises, and the ceiling of a full hold. */
    private static final int TAP_HEIGHT = 2;
    private static final int FIRST_HOLD_HEIGHT = 3;
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

    /** Ticks between the middle columns and the outer ones starting to climb. */
    private static final int RISE_WAVE_TICKS = 2;

    /** Ticks between two neighbouring blocks sinking back (the crest goes first). */
    private static final int DECAY_TICKS = 2;

    /** How far up or down the wall base may search for solid ground. */
    private static final int MAX_BASE_SEARCH = 5;

    private static final int COOLDOWN = 3 * 20;

    /**
     * The wall each caster is currently raising. The aim is frozen when the channel starts, so
     * turning around mid-hold does not drag a half-built wall across the field, and the column
     * bases are kept so that growing the wall adds layers on top of the same ground instead of
     * searching for the ground again (which, once the first layer is a real block, would find the
     * wall itself and start a second one above it).
     */
    private final Map<UUID, Wall> walls = new HashMap<>();

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
        // A server that has told mobs not to alter the world has told players the same thing: the
        // wall is raised blocks, and it would otherwise ignore the rule the rest of the world keeps.
        if (player.level() instanceof ServerLevel wallLevel
                && !wallLevel.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)) {
            this.fail(player, ninjaData, "jutsu.fail.gamerule");
            return false;
        }
        // The cooldown is checked when the cast starts only: the tap and the first tick of a hold are
        // the two ways in, and a channel that is already running must not be interrupted by it.
        if (chargeAmount <= 0
                && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        if (chargeAmount < 0 && Targeting.lookedAtBlock(player, RANGE) == null) {
            this.fail(player, ninjaData, "jutsu.fail.earthwall.notarget");
            return false;
        }
        if (chargeAmount == 0 && player.level() instanceof ServerLevel level) {
            this.pruneWalls(level);
            Wall wall = beginWall(level, player);
            if (wall == null) {
                this.fail(player, ninjaData, "jutsu.fail.earthwall.notarget");
                return false;
            }
            this.walls.put(player.getUUID(), wall);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Wall wall = this.walls.get(player.getUUID());
        if (wall == null || !wall.dimension.equals(level.dimension())) {
            // The channel outlived its wall (a reload, or a cast that started before the server knew
            // about it): fall back to the current aim rather than dropping the technique.
            wall = beginWall(level, player);
            if (wall == null) {
                return;
            }
            this.walls.put(player.getUUID(), wall);
        }
        this.raise(level, wall, heightFor(ticksChanneled));
        // Dust at the foot of every column, so the caster can see the ground working while it holds.
        if (ticksChanneled % 4 == 0) {
            for (BlockPos base : wall.bases) {
                if (base == null) {
                    continue;
                }
                level.sendParticles(ParticleTypes.CLOUD,
                        base.getX() + 0.5d, base.getY() + 0.15d, base.getZ() + 0.5d,
                        1, 0.35d, 0.05d, 0.35d, 0.01d);
            }
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Wall wall = this.walls.remove(player.getUUID());
        if (wall == null) {
            wall = beginWall(level, player);
        }
        if (wall == null) {
            return;
        }
        this.raise(level, wall, heightFor(ticksActive));
        // A wall that actually rose is what the cooldown is for; an aim that produced nothing costs
        // only the chakra that was spent holding the technique.
        if (!wall.scheduledAny) {
            return;
        }
        Vec3 center = new Vec3(wall.target.getX() + 0.5d, wall.target.getY() + 0.5d,
                wall.target.getZ() + 0.5d);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.STONE_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
        this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
    }

    /**
     * Adds every layer between what the wall already has and {@code upToHeight}, spawning a rising
     * block for each and scheduling the real block for the moment the animation ends.
     *
     * @return true when at least one block was scheduled
     */
    private boolean raise(ServerLevel level, Wall wall, int upToHeight) {
        if (upToHeight <= wall.height) {
            return false;
        }
        EarthWallSavedData walls = EarthWallSavedData.get(level);
        long now = level.getGameTime();
        int half = (WIDTH - 1) / 2;
        boolean scheduled = false;
        for (int index = 0; index < wall.bases.size(); index++) {
            BlockPos base = wall.bases.get(index);
            if (base == null) {
                continue;
            }
            int swing = Math.abs(index - half) * RISE_WAVE_TICKS;
            for (int layer = wall.height; layer < upToHeight; layer++) {
                BlockPos pos = base.above(layer);
                if (!level.getBlockState(pos).canBeReplaced() || isOccupied(level, pos)
                        || walls.isScheduled(pos)) {
                    continue;
                }
                long riseAt = now + swing + EarthWallEntity.RISE_TICKS;
                long decay = (long) (upToHeight - 1 - layer) * DECAY_TICKS;
                walls.schedule(pos, wall.materials.get(index), riseAt, wall.expiresAt + decay);
                spawnRisingBlock(level, pos, wall.materials.get(index), swing);
                scheduled = true;
            }
        }
        wall.height = upToHeight;
        wall.scheduledAny |= scheduled;
        return scheduled;
    }

    /** Spawns the short-lived visual that carries one block up out of the ground. */
    private static void spawnRisingBlock(ServerLevel level, BlockPos pos, BlockState state, int delay) {
        EarthWallEntity block = new EarthWallEntity(NarutoEntities.EARTH_WALL.get(), level);
        block.setBlockState(state);
        block.setDelay(delay);
        block.setPos(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d);
        level.addFreshEntity(block);
    }

    /** Blows a channel far enough in the past out of the map, in case one was never closed. */
    private void pruneWalls(ServerLevel level) {
        long now = level.getGameTime();
        this.walls.values().removeIf(wall -> wall.expiresAt + 100L < now);
    }

    /** Tap raises a low ridge; holding grows it to the full barrier. */
    private static int heightFor(int ticksActive) {
        if (ticksActive < 0) {
            return TAP_HEIGHT;
        }
        int ticks = Math.min(ticksActive, FULL_CHARGE_TICKS);
        return FIRST_HOLD_HEIGHT + Math.round(ticks / (float) FULL_CHARGE_TICKS
                * (MAX_HEIGHT - FIRST_HOLD_HEIGHT));
    }

    private void fail(Player player, INinjaData ninjaData, String key) {
        player.displayClientMessage(Component.translatable(key,
                Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Freezes the wall the caster is aiming at: five columns across the line of sight, each with the
     * first free block above the ground as its base and the ground's own material as its blocks.
     * Every column is independent, so uneven ground makes an even crest rather than a floating
     * rectangle.
     */
    @Nullable
    private static Wall beginWall(ServerLevel level, Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return null;
        }
        WallDir dir = directionFor(player.getYHeadRot());
        BlockPos target = hit.getBlockPos().immutable();
        List<BlockPos> bases = new ArrayList<>(WIDTH);
        List<BlockState> materials = new ArrayList<>(WIDTH);
        for (int offset = -(WIDTH - 1) / 2; offset <= (WIDTH - 1) / 2; offset++) {
            BlockPos column = target.offset(dir.x() * offset, 0, dir.z() * offset);
            BlockPos base = findBase(level, column);
            bases.add(base);
            materials.add(base == null ? Blocks.DIRT.defaultBlockState() : materialFor(level, base));
        }
        return new Wall(level.dimension(), target, level.getGameTime() + LIFETIME_TICKS, bases,
                materials);
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
        // Then sink to the ground, so the wall does not hang in the air over a dip. If the ground is
        // further down than the search goes, this column cannot stand and is dropped instead of
        // leaving a floating block over the void.
        for (int i = 0; i < MAX_BASE_SEARCH && level.getBlockState(cursor.below()).canBeReplaced(); i++) {
            cursor.move(0, -1, 0);
        }
        if (level.getBlockState(cursor.below()).canBeReplaced()) {
            return null;
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

    /** A wall being raised: its frozen aim, its five column bases and what has been scheduled so far. */
    private static final class Wall {
        private final ResourceKey<Level> dimension;
        private final BlockPos target;
        private final long expiresAt;
        private final List<BlockPos> bases;
        private final List<BlockState> materials;
        private int height;
        private boolean scheduledAny;

        private Wall(ResourceKey<Level> dimension, BlockPos target,
                     long expiresAt, List<BlockPos> bases, List<BlockState> materials) {
            this.dimension = dimension;
            this.target = target;
            this.expiresAt = expiresAt;
            this.bases = bases;
            this.materials = materials;
        }
    }

    private record WallDir(int x, int z) {
    }

    /** The element this technique belongs to, for the caster's innate affinity. */
    @Override
    public com.sekwah.narutomod.abilities.Nature nature() {
        return com.sekwah.narutomod.abilities.Nature.EARTH;
    }
}
