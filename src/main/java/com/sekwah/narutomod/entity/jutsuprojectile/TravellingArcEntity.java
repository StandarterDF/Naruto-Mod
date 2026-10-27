package com.sekwah.narutomod.entity.jutsuprojectile;

import com.sekwah.narutomod.abilities.jutsus.CamelliaDanceAbility;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * A crescent of wind travelling away from the caster: the shared flight of the projectile-cutting
 * scythe and the sword-drawn vacuum blade.
 *
 * <p>Both techniques used to land instantly with a static fan of motes at the far end, which read
 * as if they only ever happened over there. The wave instead leaves the caster and crosses the
 * ground in front of them, widening a little as it goes, and cuts whatever the front passes
 * through - arrows and thrown things come apart where they hang, bodies are opened as the edge
 * reaches them. Each body is struck once no matter how long the front lingers on it.</p>
 *
 * <p>The entity itself never moves: it stands where it was drawn and the front is a function of
 * its age, so the flight is smooth on both sides with no packets. It is never saved - a wave lives
 * about a second - and it draws nothing itself; the arc is server-sent particles.</p>
 */
public class TravellingArcEntity extends Entity {

    /** The scythe that cuts what flies. */
    public static final int KIND_CUT = 0;

    /** The crescent thrown off a sword's edge. */
    public static final int KIND_VACUUM = 1;

    /** How fast the front advances, in blocks a tick: a smooth visible flight, not a snap. */
    private static final double SPEED = 0.5d;

    /** The front cuts a little behind and a little ahead of where it stands this tick. */
    private static final double SLICE_BEHIND = 1.0d;
    private static final double SLICE_AHEAD = 0.5d;

    private static final EntityDataAccessor<Float> DATA_RANGE =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ARC_COS =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CUT =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BLEED =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MAGIC =
            SynchedEntityData.defineId(TravellingArcEntity.class, EntityDataSerializers.FLOAT);

    /** Bodies already struck on the way through, so a slow front never bills twice. */
    private final Set<Integer> hitIds = new HashSet<>();

    /** Who drew the wave. Server side only; the wave lives about a second, so no sync needed. */
    private LivingEntity caster;

    /** Ticks since the draw, advanced on both sides so the flight needs no packets. */
    private int age;

    public TravellingArcEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public TravellingArcEntity(Level level, LivingEntity caster, double range, double arcCos,
                               float damage, int kind, boolean cutProjectiles, int bleedTicks,
                               float magicShare) {
        this(NarutoEntities.TRAVELLING_ARC.get(), level);
        Vec3 eye = caster.getEyePosition();
        this.moveTo(eye.x, eye.y, eye.z, caster.getYRot(), caster.getXRot());
        this.caster = caster;
        this.entityData.set(DATA_RANGE, (float) range);
        this.entityData.set(DATA_ARC_COS, (float) arcCos);
        this.entityData.set(DATA_DAMAGE, damage);
        this.entityData.set(DATA_KIND, kind);
        this.entityData.set(DATA_CUT, cutProjectiles);
        this.entityData.set(DATA_BLEED, bleedTicks);
        this.entityData.set(DATA_MAGIC, magicShare);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RANGE, 6.0f);
        this.entityData.define(DATA_ARC_COS, 0.5f);
        this.entityData.define(DATA_DAMAGE, 3.0f);
        this.entityData.define(DATA_KIND, KIND_CUT);
        this.entityData.define(DATA_CUT, false);
        this.entityData.define(DATA_BLEED, 0);
        this.entityData.define(DATA_MAGIC, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.level().isClientSide) {
            return;
        }
        float range = this.entityData.get(DATA_RANGE);
        double traveled = this.age * SPEED;
        if (traveled > range + SLICE_AHEAD) {
            this.discard();
            return;
        }
        this.cutSlice(Math.min(traveled, range));
        this.drawFront(Math.min(traveled, range));
    }

    /**
     * Cuts the slice the front stands in: flying things come apart, bodies are opened. Everything
     * is measured from the draw point along the draw direction, so the wave works the ground as it
     * crosses it rather than judging the whole cone at once.
     */
    private void cutSlice(double traveled) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity owner = this.caster;
        if (!(owner instanceof LivingEntity caster)) {
            this.discard();
            return;
        }
        float range = this.entityData.get(DATA_RANGE);
        float arcCos = this.entityData.get(DATA_ARC_COS);
        double tanHalf = Math.sqrt(Math.max(0.0d, 1.0d - arcCos * arcCos)) / Math.max(0.05d, arcCos);
        Vec3 origin = this.position();
        Vec3 direction = this.getViewVector(1.0f).normalize();

        for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(range + 2.0d))) {
            if (entity == this || entity == owner || this.hitIds.contains(entity.getId())) {
                continue;
            }
            Vec3 to = centreOf(entity).subtract(origin);
            double distance = to.dot(direction);
            if (distance < 0.5d || distance > range
                    || distance < traveled - SLICE_BEHIND || distance > traveled + SLICE_AHEAD) {
                continue;
            }
            double lateral = to.subtract(direction.scale(distance)).length();
            if (lateral > distance * tanHalf + 0.4d) {
                continue;
            }
            if (this.entityData.get(DATA_CUT) && isCuttable(entity)) {
                level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY(), entity.getZ(),
                        6, 0.15d, 0.15d, 0.15d, 0.05d);
                entity.discard();
                this.hitIds.add(entity.getId());
                continue;
            }
            if (entity instanceof LivingEntity living && canHit(caster, living)) {
                this.hitIds.add(entity.getId());
                this.cutBody(level, caster, living);
            }
        }
    }

    /** One body under the front: the edge for this kind, and the wound it leaves. */
    private void cutBody(ServerLevel level, LivingEntity caster, LivingEntity target) {
        float damage = this.entityData.get(DATA_DAMAGE);
        if (this.entityData.get(DATA_KIND) == KIND_VACUUM) {
            float physical = damage * (1.0f - this.entityData.get(DATA_MAGIC));
            float magic = damage * this.entityData.get(DATA_MAGIC);
            target.hurt(strikeSource(caster), physical);
            if (!target.isAlive()) {
                return;
            }
            // The vacuum is a separate, unblockable hit taken in the same instant. A body still
            // inside the invulnerability window of the edge would shrug it off, so the window is
            // cleared rather than letting the second half of the cut go missing.
            target.invulnerableTime = 0;
            target.hurt(caster.damageSources().magic(), magic);
            int bleed = this.entityData.get(DATA_BLEED);
            if (bleed > 0) {
                target.getPersistentData().putLong(CamelliaDanceAbility.BLEED_TAG,
                        level.getGameTime() + bleed);
            }
            level.sendParticles(ParticleTypes.CRIT, target.getX(),
                    target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                    6, 0.2d, 0.3d, 0.2d, 0.05d);
        } else {
            // A cut, not a blow: the game's own damage path applies knockback as part of the hit,
            // so the momentum is taken before the blow and put back afterwards.
            Vec3 momentum = target.getDeltaMovement();
            target.hurt(strikeSource(caster), damage);
            target.setDeltaMovement(momentum);
        }
    }

    /** The edge's own blow: a player's strike when drawn by one, a mob's otherwise. */
    private static net.minecraft.world.damagesource.DamageSource strikeSource(LivingEntity caster) {
        return caster instanceof Player player
                ? caster.damageSources().playerAttack(player)
                : caster.damageSources().mobAttack(caster);
    }

    /**
     * The front itself: a fan of sweep motes across the arc at this tick's reach. The fan widens
     * with the distance, a little, so the wave reads as spreading while it travels.
     */
    private void drawFront(double traveled) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        float arcCos = this.entityData.get(DATA_ARC_COS);
        double halfAngle = Math.acos(Mth.clamp(arcCos, -1.0f, 1.0f));
        Vec3 direction = this.getViewVector(1.0f);
        Vec3 flat = new Vec3(direction.x, 0.0d, direction.z);
        if (flat.lengthSqr() < 1.0E-4d) {
            return;
        }
        flat = flat.normalize();
        int points = this.entityData.get(DATA_KIND) == KIND_VACUUM ? 7 : 9;
        for (int i = 0; i < points; i++) {
            double share = points == 1 ? 0.5d : i / (double) (points - 1);
            double angle = (share - 0.5d) * 2.0d * halfAngle;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3 across = new Vec3(flat.x * cos - flat.z * sin, 0.0d, flat.x * sin + flat.z * cos);
            Vec3 at = this.position().add(across.scale(traveled)).add(0.0d, -0.4d, 0.0d);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        if (this.entityData.get(DATA_KIND) == KIND_VACUUM && this.age % 2 == 0) {
            Vec3 at = this.position().add(flat.scale(traveled)).add(0.0d, -0.4d, 0.0d);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 2, 0.2d, 0.2d, 0.2d, 0.02d);
        }
    }

    /** Whether the thing is one of the flying kinds the scythe is meant to answer. */
    private static boolean isCuttable(Entity entity) {
        return entity instanceof AbstractArrow
                || entity instanceof AbstractHurtingProjectile
                || entity instanceof ThrowableItemProjectile;
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canHit(LivingEntity caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    private static Vec3 centreOf(Entity entity) {
        return entity.position().add(0.0d, entity.getBbHeight() * 0.5d, 0.0d);
    }

    /**
     * Never saved. A wave lives about a second, and an entity reloaded from disk would cut the
     * ground twice.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** Purely a carrier for the front: it must not be pickable or block arrows. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Never saved, see shouldBeSaved.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Never saved, see shouldBeSaved.
    }
}
