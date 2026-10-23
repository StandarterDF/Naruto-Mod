package com.sekwah.narutomod.entity.jutsuprojectile;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.NarutoEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The ball of rock Chibaku Tensei makes: the caster marks a patch of ground, the ground comes up,
 * and everything standing on it comes up with it.
 *
 * <p>The entity itself is invisible. It is only the centre of the technique - the thing that keeps
 * the storm running after the caster has let go of the keys, and that still works if the caster
 * wanders off or dies. The picture is made of real {@link FallingBlockEntity debris}: blocks are
 * lifted out of the ground around the point and then held on a turning shell around the core, so
 * the ball is genuine terrain and breaks nothing that is not already in it.</p>
 *
 * <p>It runs in four movements. The core climbs out of the ground; creatures inside reach are
 * dragged off their feet and held up; the shell is filled out while the whole thing shakes; and
 * then it comes down in one final blast. Removal of the debris is part of the ending, so nothing is
 * left hanging in the sky.</p>
 */
public class ChibakuTenseiEntity extends Entity {

    /** How far above the ground the core settles, in blocks. */
    private static final double RISE_HEIGHT = 10.0d;

    /** How long the core takes to climb to that height. */
    private static final int RISE_TICKS = 20;

    /** When the pull stops and the shaking storm begins, and when the ball comes down. */
    private static final int PULL_END = 80;
    private static final int STORM_END = 120;

    /** How far the pull reaches for creatures, and how hard it holds them. */
    private static final double PULL_RADIUS = 14.0d;
    private static final double PULL_BASE_SPEED = 0.25d;
    private static final double PULL_SPEED_PER_BLOCK = 0.03d;
    private static final double PULL_MAX_SPEED = 0.9d;

    /** How many blocks of ground the ball is made of, and how quickly they are torn loose. */
    private static final int MAX_DEBRIS = 48;
    private static final int DEBRIS_INTERVAL = 2;
    private static final int DEBRIS_PER_WAVE = 2;
    private static final int DEBRIS_SEARCH_RADIUS = 10;

    /** The radius of the shell the debris is arranged on. */
    private static final double DEBRIS_RADIUS = 3.0d;

    /** The final drop, and the area it crushes. */
    private static final float FINAL_RADIUS = 5.0f;
    private static final float BURST_RADIUS = 7.0f;
    private static final float BURST_DAMAGE = 16.0f;

    /** How often the storm throws a small blast out while the ball is still in the air. */
    private static final int STORM_BLAST_INTERVAL = 8;
    private static final float STORM_BLAST_RADIUS = 1.3f;

    /** Marks debris as belonging to this ball, so a second cast never steals the first one's rocks. */
    private static final String DEBRIS_TAG = "narutomod:chibakuDebris";

    /** Where the core started, so it knows how far it has to climb; saved with the entity. */
    private double startY;

    /** The charge the caster put in, 0.7 for a barely held one up to 1.0 for a full one. */
    private float power = 1.0f;

    /** Who cast it, and therefore who is spared by the storm. */
    @Nullable
    private UUID owner;

    /** The debris this ball has torn loose and is holding up, server side only. */
    private final List<UUID> debris = new ArrayList<>();

    public ChibakuTenseiEntity(EntityType<? extends ChibakuTenseiEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * @param caster the ninja who cast it
     * @param x      the point on the ground the ball is built over
     * @param y      the first free block above that ground
     * @param z      the same, on the other axis
     */
    public ChibakuTenseiEntity(LivingEntity caster, double x, double y, double z) {
        this(NarutoEntities.CHIBAKU_TENSEI.get(), caster.level());
        this.setPos(x, y, z);
        this.startY = y;
        this.owner = caster.getUUID();
    }

    @Override
    protected void defineSynchedData() {
    }

    public void setPower(float power) {
        this.power = power;
    }

    @Nullable
    public Entity getOwner() {
        return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.drawParticles();
            return;
        }
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }

        int age = this.tickCount;
        if (age <= RISE_TICKS) {
            // The core climbing out of the ground, carrying the first rocks with it.
            double progress = age / (double) RISE_TICKS;
            this.setPos(this.getX(), Mth.lerp(progress, this.startY, this.startY + RISE_HEIGHT), this.getZ());
            this.hasImpulse = true;
            if (age == 1) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.6f);
            }
            return;
        }

        if (age < PULL_END) {
            this.pullCreatures(level);
            if (age % DEBRIS_INTERVAL == 0) {
                this.tearLooseDebris(level);
            }
        } else if (age < STORM_END) {
            if (age % STORM_BLAST_INTERVAL == 0) {
                this.stormBlast(level);
            }
        } else {
            this.burst(level);
            return;
        }

        this.holdDebris(level);
    }

    /** Drags everything that is not on the caster's side off its feet and towards the core. */
    private void pullCreatures(net.minecraft.server.level.ServerLevel level) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(PULL_RADIUS * 2.0d), this::canAffect)) {
            double distanceSqr = target.distanceToSqr(this);
            if (distanceSqr > PULL_RADIUS * PULL_RADIUS || distanceSqr < 1.0d) {
                continue;
            }
            Vec3 towards = this.position().subtract(target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d));
            double distance = towards.length();
            if (distance < 0.001d) {
                continue;
            }
            double speed = Math.min(PULL_MAX_SPEED, PULL_BASE_SPEED + distance * PULL_SPEED_PER_BLOCK);
            Vec3 impulse = towards.normalize().scale(speed);
            target.setDeltaMovement(impulse.x, Math.max(impulse.y, 0.0d), impulse.z);
            target.hurtMarked = true;
            target.fallDistance = 0.0f;
        }
    }

    /**
     * Pulls the ground itself apart: a block near the core is taken out of the world without a drop
     * and turned into a falling block that the shell then catches.
     */
    private void tearLooseDebris(net.minecraft.server.level.ServerLevel level) {
        if (this.debris.size() >= MAX_DEBRIS) {
            return;
        }
        RandomSource random = level.random;
        for (int attempt = 0; attempt < DEBRIS_PER_WAVE * 4 && this.debris.size() < MAX_DEBRIS; attempt++) {
            int dx = random.nextInt(DEBRIS_SEARCH_RADIUS * 2 + 1) - DEBRIS_SEARCH_RADIUS;
            int dz = random.nextInt(DEBRIS_SEARCH_RADIUS * 2 + 1) - DEBRIS_SEARCH_RADIUS;
            int bx = Mth.floor(this.getX()) + dx;
            int bz = Mth.floor(this.getZ()) + dz;
            int by = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
            BlockPos pos = new BlockPos(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            if (!isGoodDebris(level, pos, state)) {
                continue;
            }

            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.setNoGravity(true);
            block.disableDrop();
            block.getPersistentData().putString(DEBRIS_TAG, this.getUUID().toString());
            this.debris.add(block.getUUID());
        }
    }

    /** Whether a block is worth tearing loose: solid, cheap to move and not part of a structure. */
    private static boolean isGoodDebris(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0.1f && hardness <= 30.0f && state.isSolidRender(level, pos);
    }

    /** Puts every loose rock on a slowly turning shell around the core. */
    private void holdDebris(net.minecraft.server.level.ServerLevel level) {
        Iterator<UUID> iterator = this.debris.iterator();
        int index = 0;
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = level.getEntity(id);
            if (!(entity instanceof FallingBlockEntity block) || !block.isAlive()) {
                iterator.remove();
                continue;
            }
            double angle = index * 2.399963d + this.tickCount * 0.05d;
            double height = ((index % 5) - 2) * 0.6d;
            double radius = DEBRIS_RADIUS - Math.abs(height) * 0.12d;
            double x = this.getX() + Math.cos(angle) * radius;
            double y = this.getY() + height + Math.sin(this.tickCount * 0.1d + index) * 0.15d;
            double z = this.getZ() + Math.sin(angle) * radius;
            block.setPos(x, y, z);
            block.setDeltaMovement(Vec3.ZERO);
            block.fallDistance = 0.0f;
            // Falling blocks are tracked slowly by default, so nudge the tracker to send the shell's
            // new position this tick instead of making the ball snap around once a second.
            block.hasImpulse = true;
            index++;
        }
    }

    /** One of the small blasts that shake the ball apart before it drops. */
    private void stormBlast(net.minecraft.server.level.ServerLevel level) {
        RandomSource random = level.random;
        double x = this.getX() + (random.nextDouble() - 0.5d) * 4.0d;
        double y = this.getY() + (random.nextDouble() - 0.5d) * 3.0d;
        double z = this.getZ() + (random.nextDouble() - 0.5d) * 4.0d;
        level.explode(this, x, y, z, STORM_BLAST_RADIUS * this.power, Level.ExplosionInteraction.NONE);
    }

    /** The drop: one last blast and the earth it was made of falls back down. */
    private void burst(net.minecraft.server.level.ServerLevel level) {
        level.explode(this, this.getX(), this.getY(), this.getZ(),
                FINAL_RADIUS * this.power, Level.ExplosionInteraction.NONE);

        Entity caster = this.getOwner();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(BURST_RADIUS), this::canAffect)) {
            double distance = Math.sqrt(target.distanceToSqr(this));
            float damage = (float) (BURST_DAMAGE * this.power
                    * Math.max(0.0d, 1.0d - distance / BURST_RADIUS));
            if (damage <= 0.0f) {
                continue;
            }
            target.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.CHIBAKU_TENSEI, this, caster),
                    damage);
            Vec3 push = target.position().subtract(this.position());
            if (push.lengthSqr() < 0.0001d) {
                push = new Vec3(0.0d, 0.0d, 1.0d);
            }
            push = push.normalize().scale(0.7d);
            target.push(push.x, 0.5d, push.z);
            target.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(),
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
                80, BURST_RADIUS * 0.4d, BURST_RADIUS * 0.25d, BURST_RADIUS * 0.4d, 0.12d);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.5f);

        this.releaseDebris(level);
        this.discard();
    }

    /** Drops every rock the ball was holding, without letting any of them turn back into blocks. */
    private void releaseDebris(net.minecraft.server.level.ServerLevel level) {
        for (UUID id : this.debris) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        this.debris.clear();
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private boolean canAffect(LivingEntity target) {
        if (target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && player.isCreative()) {
            return false;
        }
        Entity caster = this.getOwner();
        if (caster == null) {
            return true;
        }
        return target != caster && !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** The core is invisible, so all the client has to do is throw dust around it. */
    private void drawParticles() {
        RandomSource random = this.random;
        for (int i = 0; i < 3; i++) {
            double x = this.getX() + (random.nextDouble() - 0.5d) * DEBRIS_RADIUS * 2.0d;
            double y = this.getY() + (random.nextDouble() - 0.5d) * DEBRIS_RADIUS * 1.5d;
            double z = this.getZ() + (random.nextDouble() - 0.5d) * DEBRIS_RADIUS * 2.0d;
            this.level().addParticle(ParticleTypes.CLOUD, x, y, z, 0.0d, 0.0d, 0.0d);
        }
        if (this.tickCount % 5 == 0) {
            this.level().addParticle(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(),
                    0.0d, 0.0d, 0.0d);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        this.startY = tag.getDouble("StartY");
        this.power = tag.getFloat("Power");
        this.debris.clear();
        if (tag.contains("Debris", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("Debris", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                try {
                    this.debris.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // A malformed id is simply one less rock to hold; never worth a crash.
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        tag.putDouble("StartY", this.startY);
        tag.putFloat("Power", this.power);
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (UUID id : this.debris) {
            list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        }
        tag.put("Debris", list);
    }

    /**
     * Drops the shell when the ball is really gone, but not when it is merely being written out with
     * its chunk: on an unload the debris is saved and reloaded with the ball, so throwing it away
     * there would leave a bare core hanging over a crater after a world reload.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)
                && this.level() instanceof net.minecraft.server.level.ServerLevel level) {
            this.releaseDebris(level);
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0d * 64.0d;
    }
}
