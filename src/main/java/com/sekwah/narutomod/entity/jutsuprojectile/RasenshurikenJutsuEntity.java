package com.sekwah.narutomod.entity.jutsuprojectile;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.projectile.AbstractNonGlowingHurtingProjectile;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wind disc of the rasenshuriken: a blade of chakra that is thrown rather than driven in.
 *
 * <p>Where the {@code RasenganJutsuAbility} ends at the caster's arm, this carries on: once it is
 * let go it flies straight until it buries itself in something. Landing on a wall it simply comes
 * apart, but landing on a living target is what the technique is for - the disc does not burst on
 * them, it <b>scoops them up</b>, holds them in the air and then goes off over their head in a storm
 * of blasts. Whoever it caught is left hanging in the sky when it is done.</p>
 *
 * <p>The disc is not a model: the renderer blows up the mod's shuriken item and spins it, and the
 * wind is particles. The two sides of the game both drive the hold on their own, following the
 * caught entity through the id carried in {@link #DATA_CARRIED_ID}, so the lift looks the same
 * wherever it is seen from.</p>
 */
public class RasenshurikenJutsuEntity extends AbstractNonGlowingHurtingProjectile {

    /**
     * This is roughly the max speed a projectile can have before going sideways or weird, if you go
     * crazily faster it fixes again but that's too fast.
     */
    private static final double MULTIPLIER = 0.78D;

    /** How long the disc is allowed to live before it loses shape (ticks). */
    private static final int MAX_LIFE = 80;

    /** Damage a full power disc does to whatever it lands on directly. */
    private static final float DIRECT_DAMAGE = 14.0f;

    /** Damage the burst does at its centre, falling off to nothing at the edge. */
    private static final float BURST_DAMAGE = 9.0f;

    /** How far the final burst reaches. */
    private static final double BURST_RADIUS = 4.5d;

    /** How hard the final burst shoves what it catches. */
    private static final double BURST_PUSH = 1.3d;

    /** How long the disc holds a caught target up in the air before it starts blowing up. */
    private static final int CARRY_TICKS = 24;

    /** How far the hold lifts its catch every tick. */
    private static final double CARRY_RISE = 0.4d;

    /** How far the hold carries its catch forward every tick, along the disc's flight. */
    private static final double CARRY_FORWARD = 0.45d;

    /** How long the storm of blasts lasts once the disc stops rising. */
    private static final int DETONATION_TICKS = 30;

    /** Ticks between the blasts in that storm. */
    private static final int BARRAGE_INTERVAL = 5;

    /** How wide each blast in the storm is. */
    private static final float BARRAGE_RADIUS = 1.6f;

    /** How far from the caught target those blasts land. */
    private static final double BARRAGE_SPREAD = 2.0d;

    /** The last one, which takes the disc with it. */
    private static final float FINAL_RADIUS = 3.0f;

    /**
     * The entity the disc has hold of, as a network id so that both sides can follow it (0 = nobody).
     */
    private static final EntityDataAccessor<Integer> DATA_CARRIED_ID =
            SynchedEntityData.defineId(RasenshurikenJutsuEntity.class, EntityDataSerializers.INT);

    private int lifeSpan = MAX_LIFE;

    /**
     * -1 while the disc is still flying. From the moment it catches someone, how many ticks it has
     * held them for. Only the server counts it, the client just follows the entity.
     */
    private int carryTicks = -1;

    /** How far into the spin the disc is, advanced on both sides so it can be drawn spinning. */
    private int spinTicks;
    private int prevSpinTicks;

    /**
     * The direction the disc was flying when it caught someone, flattened to the ground plane. The
     * hold keeps carrying the catch that way while lifting it, instead of leaving it to rise on the
     * spot - the technique is a throw, and the victim is supposed to be taken where it was going.
     */
    private double carryDirX;
    private double carryDirZ;

    /**
     * The charge the caster put in, 0.7 for a barely held disc up to 1.0 for a full one. Only ever
     * read on the server, so it is not carried over the network: the shot is decided the moment it
     * is thrown.
     */
    private float power = 1.0f;

    public RasenshurikenJutsuEntity(EntityType<RasenshurikenJutsuEntity> entityType, Level level) {
        super(entityType, level);
    }

    public RasenshurikenJutsuEntity(EntityType<? extends AbstractNonGlowingHurtingProjectile> entityType,
                                    double posX, double posY, double posZ,
                                    double velX, double velY, double velZ, Level level) {
        super(entityType, level);
        this.moveTo(posX, posY, posZ, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        double d0 = Math.sqrt(velX * velX + velY * velY + velZ * velZ);
        if (d0 != 0.0D) {
            this.xPower = velX / d0 * MULTIPLIER;
            this.yPower = velY / d0 * MULTIPLIER;
            this.zPower = velZ / d0 * MULTIPLIER;
        }
    }

    /**
     * @param caster the ninja throwing it
     * @param speed  how fast the disc leaves the hand, scaled by how long it was held
     */
    public RasenshurikenJutsuEntity(LivingEntity caster, double speed) {
        this(NarutoEntities.RASENSHURIKEN_JUTSU.get(), caster.getX(), caster.getEyeY() - 0.2d, caster.getZ(),
                caster.getLookAngle().x * speed, caster.getLookAngle().y * speed, caster.getLookAngle().z * speed,
                caster.level());
        this.setOwner(caster);
        this.setRot(caster.getYRot(), caster.getXRot());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CARRIED_ID, 0);
    }

    public void setPower(float power) {
        this.power = power;
    }

    /** Where the renderer is in the spin, and where it was last frame, so it can be interpolated. */
    public int getSpinTicks() {
        return this.spinTicks;
    }

    public int getPrevSpinTicks() {
        return this.prevSpinTicks;
    }

    /**
     * Whoever the disc has hold of, on whichever side is asking. Both sides find them by the id that
     * the caster's hit wrote into the synched data, so the hold looks the same to everyone.
     */
    private Entity getCarried() {
        int id = this.entityData.get(DATA_CARRIED_ID);
        return id == 0 ? null : this.level().getEntity(id);
    }

    @Override
    public void tick() {
        this.prevSpinTicks = this.spinTicks++;
        Entity carried = this.getCarried();

        // Holding someone: the disc is not flying any more, it is hanging over their head.
        if (carried != null || this.carryTicks >= 0) {
            if (carried != null && carried.isAlive()) {
                this.tickCarry(carried);
            } else if (this.level() instanceof ServerLevel level) {
                // They died or left before the storm was over: set the rest off where we are.
                this.detonate(level, carried);
            }
            return;
        }

        super.tick();

        if (this.level() instanceof ServerLevel level && this.spinTicks % 2 == 0) {
            // A ring of air being dragged round with the blades.
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(),
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }

        if (--this.lifeSpan <= 0) {
            this.burst();
        }
    }

    /**
     * The hold: carry the catch forward and up, then blow up. Both sides put the disc over the
     * target every tick, but only the server moves and explodes - the client is following along a
     * step behind it.
     */
    private void tickCarry(Entity carried) {
        this.setDeltaMovement(Vec3.ZERO);
        this.xPower = 0.0D;
        this.yPower = 0.0D;
        this.zPower = 0.0D;

        if (!(this.level() instanceof ServerLevel level)) {
            this.follow(carried);
            return;
        }
        this.carryTicks++;
        double midY = carried.getY() + carried.getBbHeight() * 0.5d;

        if (this.carryTicks <= CARRY_TICKS) {
            // Carrying them onward and up. Teleporting rather than pushing, because a player's own
            // client would otherwise just walk back out of it.
            carried.teleportTo(carried.getX() + this.carryDirX * CARRY_FORWARD,
                    carried.getY() + CARRY_RISE,
                    carried.getZ() + this.carryDirZ * CARRY_FORWARD);
            carried.fallDistance = 0.0f;
            level.sendParticles(ParticleTypes.CLOUD, carried.getX(), midY, carried.getZ(),
                    6, 0.8d, 0.6d, 0.8d, 0.02d);
            if (this.carryTicks % 4 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, carried.getX(), midY, carried.getZ(),
                        2, 1.0d, 0.5d, 1.0d, 0.0d);
            }
            this.follow(carried);
            return;
        }

        // Up there now: the storm.
        level.sendParticles(ParticleTypes.CRIT, carried.getX(), midY, carried.getZ(),
                12, 1.0d, 1.0d, 1.0d, 0.4d);
        if (this.carryTicks % BARRAGE_INTERVAL == 0) {
            this.fireBarrage(level, carried);
        }
        if (this.carryTicks >= CARRY_TICKS + DETONATION_TICKS) {
            this.detonate(level, carried);
            return;
        }
        this.follow(carried);
    }

    /** Keeps the disc hovering over whatever it is carrying, on both sides. */
    private void follow(Entity carried) {
        this.setPos(carried.getX(), carried.getY() + carried.getBbHeight() * 0.5d, carried.getZ());
    }

    /** One volley of the storm: a couple of small blasts scattered around the catch. */
    private void fireBarrage(ServerLevel level, Entity carried) {
        RandomSource random = level.random;
        int shots = 1 + random.nextInt(2);
        for (int i = 0; i < shots; i++) {
            double x = carried.getX() + (random.nextDouble() - 0.5d) * 2.0d * BARRAGE_SPREAD;
            double y = carried.getY() + carried.getBbHeight() * 0.5d
                    + (random.nextDouble() - 0.5d) * 2.0d * BARRAGE_SPREAD;
            double z = carried.getZ() + (random.nextDouble() - 0.5d) * 2.0d * BARRAGE_SPREAD;
            level.explode(this, x, y, z, BARRAGE_RADIUS, Level.ExplosionInteraction.NONE);
        }
    }

    /**
     * The end of it: one last blast where the disc is and the wind burst with it. The caster and
     * their side are kept out of the blasts by {@code ExplosionEvents}.
     */
    private void detonate(ServerLevel level, Entity carried) {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        if (carried != null) {
            x = carried.getX();
            y = carried.getY() + carried.getBbHeight() * 0.5d;
            z = carried.getZ();
            this.setPos(x, y, z);
        }
        level.explode(this, x, y, z, FINAL_RADIUS, Level.ExplosionInteraction.NONE);
        this.burst();
    }

    /** Whether the disc should scoop this hit up rather than simply coming apart on it. */
    private boolean canCarry(LivingEntity target) {
        Entity owner = this.getOwner();
        if (target == owner || (owner != null && target.isAlliedTo(owner))
                || Friends.areFriends(owner, target)) {
            return false;
        }
        return !target.isSpectator() && !(target instanceof Player player && player.isCreative());
    }

    /** Take hold of a target: stop flying, hang on and start the count up. */
    private void capture(LivingEntity target) {
        this.carryTicks = 0;
        this.entityData.set(DATA_CARRIED_ID, target.getId());
        // Remember where the disc was heading before its motion is zeroed: the hold carries the
        // catch on along that line rather than letting it rise straight up on the spot.
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4d) {
            motion = new Vec3(this.xPower, this.yPower, this.zPower);
        }
        Vec3 flat = new Vec3(motion.x, 0.0d, motion.z);
        if (flat.lengthSqr() > 1.0E-4d) {
            flat = flat.normalize();
            this.carryDirX = flat.x;
            this.carryDirZ = flat.z;
        }
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level() instanceof ServerLevel level) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.6f);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    8, 0.8d, 0.8d, 0.8d, 0.0d);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = hitResult.getEntity();
        entity.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.WIND_RELEASE, this, this.getOwner()),
                DIRECT_DAMAGE * this.power);
        if (this.getOwner() instanceof LivingEntity owner) {
            this.doEnchantDamageEffects(owner, entity);
        }

        if (entity instanceof LivingEntity living && this.canCarry(living)) {
            this.capture(living);
        } else {
            this.burst();
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        // Landing on a block (or on someone the disc cannot hold) just comes apart. A catch is
        // handled in onHitEntity and stays alive to be carried.
        if (hitResult.getType() == HitResult.Type.BLOCK && this.carryTicks < 0) {
            this.burst();
        }
    }

    /**
     * Coming apart: everything standing around the impact is cut and thrown, whether the disc buried
     * itself in a wall or finished with whoever it was holding. The caster and their allies are left
     * out of it - it is their own technique sitting in the middle of the blast.
     */
    private void burst() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(),
                60, BURST_RADIUS * 0.45d, BURST_RADIUS * 0.3d, BURST_RADIUS * 0.45d, 0.12d);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(),
                30, BURST_RADIUS * 0.4d, BURST_RADIUS * 0.25d, BURST_RADIUS * 0.4d, 0.4d);

        Entity owner = this.getOwner();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(BURST_RADIUS), this::canBurstHit)) {
            float damage = (float) (BURST_DAMAGE * this.power
                    * Math.max(0.0d, 1.0d - Math.sqrt(target.distanceToSqr(this)) / BURST_RADIUS));
            if (damage <= 0.0f) {
                continue;
            }
            target.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.WIND_RELEASE, this, owner), damage);
            Vec3 push = target.position().subtract(this.position());
            if (push.lengthSqr() < 0.0001d) {
                push = new Vec3(0.0d, 0.0d, 1.0d);
            }
            push = push.normalize().scale(BURST_PUSH);
            target.push(push.x, 0.4d, push.z);
            target.hurtMarked = true;
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0f, 0.7f);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.7f);
        this.discard();
    }

    private boolean canBurstHit(Entity entity) {
        Entity owner = this.getOwner();
        if (entity == owner || entity == this) {
            return false;
        }
        return owner == null || (!entity.isAlliedTo(owner) && !Friends.areFriends(owner, entity));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.CLOUD;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        // To counteract weird tracking delay
        if (this.getOwner() != null) {
            this.setYRot(this.getOwner().getYRot() - 180);
            this.yRotO = this.getYRot();
        }
        double d0 = packet.getXa();
        double d1 = packet.getYa();
        double d2 = packet.getZa();
        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
        if (d3 != 0.0D) {
            this.xPower = d0 / d3 * MULTIPLIER;
            this.yPower = d1 / d3 * MULTIPLIER;
            this.zPower = d2 / d3 * MULTIPLIER;
        }
    }
}
