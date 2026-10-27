package com.sekwah.narutomod.entity.jutsuprojectile;

import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.projectile.AbstractNonGlowingHurtingProjectile;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown Tailed Beast Bomb: a Truth-Seeking-black sphere of packed beast chakra that flies
 * where the caster threw it and comes apart in a real explosion.
 *
 * <p>It looks like what it is made of: the renderer draws the shared Truth-Seeking Orb sphere, at
 * a size decided by the charge the caster put in. A barely held bomb leaves the hand small and
 * lands small; a full three seconds leaves it the size of a barrel and the blast to match.</p>
 *
 * <p>The blast is a genuine explosion - it breaks blocks and throws bodies - so the caster's own
 * side is spared by {@code ExplosionEvents}, the same way the rasenshuriken's storm spares them.</p>
 */
public class TailedBallEntity extends AbstractNonGlowingHurtingProjectile {

    /**
     * Acceleration along the flight each tick. Terminal velocity settles around two blocks a tick,
     * slow enough to watch fly and fast enough to cross a field.
     */
    private static final double MULTIPLIER = 0.12D;

    /** How long the bomb may fly before it goes off where it is. */
    private static final int MAX_LIFE = 60;

    /** Direct hit damage at no charge and at full charge. A high art hits like one. */
    private static final float MIN_DIRECT = 20.0f;
    private static final float MAX_DIRECT = 60.0f;

    /** Blast radius at no charge and at full charge. */
    private static final float MIN_BLAST = 4.0f;
    private static final float MAX_BLAST = 8.0f;

    /** Rendered sphere radius at no charge and at full charge. */
    private static final float MIN_SPHERE = 0.35f;
    private static final float MAX_SPHERE = 1.0f;

    /** The charge the caster put in, 0 for a tapped throw up to 1 for a full one. Synced so the
     * renderer draws the right size on both sides. */
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(TailedBallEntity.class, EntityDataSerializers.FLOAT);

    private int life = MAX_LIFE;

    public TailedBallEntity(EntityType<TailedBallEntity> entityType, Level level) {
        super(entityType, level);
    }

    public TailedBallEntity(EntityType<? extends AbstractNonGlowingHurtingProjectile> entityType,
                            double posX, double posY, double posZ,
                            double velX, double velY, double velZ, Level level, float power) {
        super(entityType, level);
        this.moveTo(posX, posY, posZ, this.getYRot(), this.getXRot());
        this.reapplyPosition();
        double d0 = Math.sqrt(velX * velX + velY * velY + velZ * velZ);
        if (d0 != 0.0D) {
            this.xPower = velX / d0 * MULTIPLIER;
            this.yPower = velY / d0 * MULTIPLIER;
            this.zPower = velZ / d0 * MULTIPLIER;
        }
        this.entityData.set(DATA_POWER, Mth.clamp(power, 0.0f, 1.0f));
    }

    /**
     * @param caster the ninja throwing it
     * @param speed  how fast the bomb leaves the hand
     * @param power  the charge behind it, 0 to 1
     */
    public TailedBallEntity(LivingEntity caster, double speed, float power) {
        this(NarutoEntities.TAILED_BALL.get(), caster.getX(), caster.getEyeY() - 0.2d, caster.getZ(),
                caster.getLookAngle().x * speed, caster.getLookAngle().y * speed, caster.getLookAngle().z * speed,
                caster.level(), power);
        this.setOwner(caster);
        this.setRot(caster.getYRot(), caster.getXRot());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_POWER, 1.0f);
    }

    /** @return the charge behind this bomb, 0 to 1. */
    public float getPower() {
        return this.entityData.get(DATA_POWER);
    }

    /** @return the black sphere's radius for the current charge, with a quick swell on the throw. */
    public float getRenderRadius(float partialTicks) {
        float grown = Math.min(1.0f, (this.tickCount + partialTicks) / 5.0f);
        float eased = grown * grown * (3.0f - 2.0f * grown);
        return Mth.lerp(this.getPower(), MIN_SPHERE, MAX_SPHERE) * (0.4f + 0.6f * eased);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level() instanceof ServerLevel level) {
            float radius = Mth.lerp(this.getPower(), MIN_SPHERE, MAX_SPHERE);
            level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(),
                    3, radius * 0.6d, radius * 0.6d, radius * 0.6d, 0.01d);
            if (this.tickCount % 2 == 0) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(),
                        2, radius * 0.8d, radius * 0.8d, radius * 0.8d, 0.02d);
            }
            if (--this.life <= 0) {
                this.explode(level);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = hitResult.getEntity();
        Entity owner = this.getOwner();
        if (target != owner && (owner == null || !Friends.isOwnSide(owner, target))) {
            float damage = Mth.lerp(this.getPower(), MIN_DIRECT, MAX_DIRECT);
            target.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.TAILED_BALL, this, owner), damage);
            if (owner instanceof LivingEntity living) {
                this.doEnchantDamageEffects(living, target);
            }
        }
        this.explode(level);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (hitResult.getType() == HitResult.Type.BLOCK && this.level() instanceof ServerLevel level) {
            this.explode(level);
        }
    }

    /**
     * Coming apart: a genuine explosion that throws bodies, sized by the charge. Blocks break only
     * when the server allows it - off by default, flipped with '/naruto debug bomb'. The caster's
     * own side is spared by {@code ExplosionEvents}; everyone else takes the vanilla blast on top
     * of the direct hit above.
     */
    private void explode(ServerLevel level) {
        float radius = Mth.lerp(this.getPower(), MIN_BLAST, MAX_BLAST);
        level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY(), this.getZ(),
                80, radius * 0.4d, radius * 0.4d, radius * 0.4d, 0.06d);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(),
                50, radius * 0.4d, radius * 0.4d, radius * 0.4d, 0.04d);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 4.0f, 0.7f);
        Level.ExplosionInteraction interaction = com.sekwah.narutomod.config.NarutoConfig.tailedBallBlockDamage
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(this, this.getX(), this.getY(), this.getZ(), radius, interaction);
        this.discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.SQUID_INK;
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
