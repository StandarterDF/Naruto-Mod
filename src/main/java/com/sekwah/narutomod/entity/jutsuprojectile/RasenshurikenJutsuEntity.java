package com.sekwah.narutomod.entity.jutsuprojectile;

import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.projectile.AbstractNonGlowingHurtingProjectile;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The wind disc of the rasenshuriken: a blade of chakra that is thrown rather than driven in.
 *
 * <p>Where the {@code RasenganJutsuAbility} ends at the caster's arm, this carries on: once it is
 * let go it flies straight until it buries itself in something, and then it comes apart as a burst of
 * wind that cuts everything standing around the point of impact. The charge the caster put in is
 * carried in {@link #setPower(float)} and scales both the cut and the burst.</p>
 *
 * <p>The disc itself is made of the trail of particles it drags behind it and the spinning item the
 * renderer draws, in the same way the rasengan is made of particles at the caster's hand - there is
 * no model of its own.</p>
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

    /** How far the burst reaches. */
    private static final double BURST_RADIUS = 4.5d;

    /** How hard the burst shoves what it catches. */
    private static final double BURST_PUSH = 1.3d;

    private int lifeSpan = MAX_LIFE;

    /** How far into the spin the disc is, advanced on both sides so it can be drawn spinning. */
    private int spinTicks;
    private int prevSpinTicks;

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

    @Override
    public void tick() {
        this.prevSpinTicks = this.spinTicks;
        this.spinTicks++;
        super.tick();

        if (this.level() instanceof ServerLevel level) {
            // A ring of air being dragged round with the blades.
            if (this.spinTicks % 2 == 0) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(),
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }

        if (--this.lifeSpan <= 0) {
            this.burst();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level().isClientSide) {
            return;
        }
        Entity entity = hitResult.getEntity();
        entity.hurt(NarutoDamageTypes.getDamageSource(this.level(), NarutoDamageTypes.WIND_RELEASE, this, this.getOwner()),
                DIRECT_DAMAGE * this.power);
        if (this.getOwner() instanceof LivingEntity owner) {
            this.doEnchantDamageEffects(owner, entity);
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        this.burst();
    }

    /**
     * Coming apart: everything standing around the impact is cut and thrown, whether the disc buried
     * itself in a wall or in somebody. The caster and their allies are left out of it - it is their
     * own technique sitting in the middle of the blast.
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
        return owner == null || !entity.isAlliedTo(owner);
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
