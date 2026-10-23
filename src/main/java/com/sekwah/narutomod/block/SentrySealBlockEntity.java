package com.sekwah.narutomod.block;

import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The brain of the sentry seal: a small scan loop and an alarm.
 *
 * <p>Scanning every tick would be wasteful for a block that only cares about a perimeter, so the
 * scan runs once a second, staggered by position so a field full of seals does not all scan on the
 * same tick. The alarm itself has a cooldown - a mob standing in the radius should not turn the
 * base into a car alarm.</p>
 *
 * <p>Owner is stored mostly for the tracker and for future per-ninja filtering; the seal sounds
 * for anyone nearby, which is what a perimeter ward is for.</p>
 */
public class SentrySealBlockEntity extends BlockEntity {

    /** How often the seal looks for intruders, in ticks. */
    private static final int SCAN_INTERVAL = 20;

    /** The pause between two chimes once something hostile is inside. */
    private static final int ALARM_COOLDOWN = 60;

    private static final String OWNER_TAG = "Owner";

    private int alarmCooldown;

    @Nullable
    private UUID owner;

    public SentrySealBlockEntity(BlockPos pos, BlockState state) {
        super(NarutoBlocks.SENTRY_SEAL_ENTITY.get(), pos, state);
    }

    public void setOwner(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living) {
            this.owner = living.getUUID();
            this.setChanged();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            SentrySealTracker.register(serverLevel, this.worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            SentrySealTracker.unregister(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.owner != null) {
            tag.putUUID(OWNER_TAG, this.owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
    }

    /** The per-tick work of a placed seal. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SentrySealBlockEntity seal) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (seal.alarmCooldown > 0) {
            seal.alarmCooldown--;
        }
        // Stagger the scan by the block's own position, so a row of seals does not spike together.
        if ((level.getGameTime() + pos.asLong()) % SCAN_INTERVAL != 0L) {
            return;
        }
        double radius = NarutoConfig.sentrySealRadius;
        List<Mob> intruders = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(radius),
                mob -> mob.isAlive() && mob instanceof Enemy);
        if (intruders.isEmpty()) {
            return;
        }
        if (seal.alarmCooldown > 0) {
            return;
        }
        seal.alarmCooldown = ALARM_COOLDOWN;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.9f, 1.7f);
        serverLevel.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5d, pos.getY() + 0.3d, pos.getZ() + 0.5d,
                8, 0.35d, 0.15d, 0.35d, 0.01d);
    }
}
