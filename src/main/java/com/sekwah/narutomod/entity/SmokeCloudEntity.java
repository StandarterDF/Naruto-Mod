package com.sekwah.narutomod.entity;

import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The bank of smoke left behind by the smoke screen technique.
 *
 * <p>It is a piece of the world rather than an effect on one creature: anything inside is hidden,
 * friend and foe alike. Mobs that wander in lose whatever they were chasing and are refused a new
 * target for as long as they stay - the actual refusal lives in {@code SmokeScreenEvents}, because
 * the targeting goals fire too often to leave it to the cloud's own tick. Players caught inside are
 * darkened instead, unless the server has turned that off, and never the caster, who is the one
 * supposed to be slipping away through it.</p>
 *
 * <p>The cloud is pure particles: it never touches blocks and never renders a model, so the client
 * only has to see the entity to draw the smoke. It lives for eight seconds and then is gone.</p>
 */
public class SmokeCloudEntity extends Entity {

    /** Life of a freshly cast cloud, in ticks. */
    private static final int LIFETIME = 20 * 8;

    /** How far from the centre the cloud hides and blinds, in blocks. */
    public static final double RADIUS = 4.5d;

    /** How far this particular cloud reaches; a thrown bomb makes a smaller one than the technique. */
    private double radius = RADIUS;

    /** How long this particular cloud lives, in ticks. */
    private int lifetime = LIFETIME;

    /** How often the creatures inside are reminded of where they are, in ticks. */
    private static final int AFFECT_INTERVAL = 5;

    /** How long the darkness lingers on a caught player, refreshed while they stay inside. */
    private static final int BLIND_DURATION = 60;

    /** Motes of smoke thrown out per client tick. */
    private static final int PARTICLES_PER_TICK = 5;

    /** Who is immune to their own smoke. */
    @Nullable
    private UUID owner;

    public SmokeCloudEntity(EntityType<? extends SmokeCloudEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        if (tag.contains("Radius")) {
            this.radius = Math.max(0.5d, tag.getDouble("Radius"));
        }
        if (tag.contains("Life")) {
            this.lifetime = Math.max(1, tag.getInt("Life"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        tag.putDouble("Radius", this.radius);
        tag.putInt("Life", this.lifetime);
    }

    public void setOwner(Player player) {
        this.owner = player.getUUID();
    }

    /**
     * Shrinks the cloud to a thrown bomb's size: the item is a pocket version of the technique, so
     * it is smaller and shorter lived while the behaviour inside it stays exactly the same.
     */
    public SmokeCloudEntity setCloudSize(double radius, int lifetime) {
        this.radius = Math.max(0.5d, radius);
        this.lifetime = Math.max(1, lifetime);
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            drawSmoke();
            return;
        }
        if (this.tickCount >= this.lifetime) {
            this.discard();
            return;
        }
        if (this.tickCount % AFFECT_INTERVAL == 0) {
            affectCreatures();
        }
    }

    /** Drops the target of every mob inside, and darkens every player but the caster. */
    private void affectCreatures() {
        double radiusSqr = this.radius * this.radius;
        List<Mob> mobs = this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(this.radius));
        for (Mob mob : mobs) {
            if (mob.distanceToSqr(this) <= radiusSqr) {
                mob.setTarget(null);
            }
        }
        if (!NarutoConfig.smokeBlindPlayers) {
            return;
        }
        List<Player> players = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(this.radius));
        for (Player player : players) {
            if (this.owner != null && this.owner.equals(player.getUUID())) {
                continue;
            }
            if (player.distanceToSqr(this) <= radiusSqr) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                        BLIND_DURATION, 0, true, false, false));
            }
        }
    }

    /** A drifting bank of billboard smoke, drawn only by the clients that can see the entity. */
    private void drawSmoke() {
        RandomSource random = this.random;
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            double x = this.getX() + (random.nextDouble() * 2.0d - 1.0d) * this.radius * 0.8d;
            double y = this.getY() + random.nextDouble() * 2.5d;
            double z = this.getZ() + (random.nextDouble() * 2.0d - 1.0d) * this.radius * 0.8d;
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0d, 0.02d, 0.0d);
        }
    }

    /** @return whether an entity is standing inside any smoke cloud. */
    public static boolean isHiddenBySmoke(Entity entity) {
        for (SmokeCloudEntity cloud : entity.level().getEntitiesOfClass(SmokeCloudEntity.class,
                entity.getBoundingBox().inflate(RADIUS))) {
            if (cloud.distanceToSqr(entity) <= cloud.radius * cloud.radius) {
                return true;
            }
        }
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
}
