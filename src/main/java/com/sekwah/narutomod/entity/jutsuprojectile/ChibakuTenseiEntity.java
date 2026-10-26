package com.sekwah.narutomod.entity.jutsuprojectile;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;

/**
 * The ball of rock Chibaku Tensei makes: the ground under the crosshair gathers into a sphere in
 * the sky, everything standing on it comes up with it, and then the whole thing is dropped.
 *
 * <p>The entity is only the centre of the technique. What it looks like is drawn by
 * {@code ChibakuTenseiRenderer} - a shell of the local ground, assembled block by block out of the
 * terrain around the cast - so the ball is made of nothing at all. It lifts no block out of the
 * world and breaks none: a cast leaves the landscape exactly as it found it, which is the one thing
 * this version of the technique is not allowed to do.</p>
 *
 * <p>It runs in four movements. The core climbs out of the ground while its shell condenses; the
 * pull drags everything not on the caster's side off its feet and holds it on the shell; then the
 * ball is let go and falls; and at the bottom it bursts, throwing whatever it carried and crushing
 * the ground it landed on. The size of the ball, the reach of the pull and the weight of the final
 * blow all come from the charge the caster put in, carried in {@link #DATA_POWER}.</p>
 */
public class ChibakuTenseiEntity extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Logs where the pull is taking things from and to; useful while tuning the technique. */
    private static final boolean DEBUG_PULL = true;

    /** Age at which the core stops climbing and starts to hold the battlefield. */
    public static final int RISE_END = 140;

    /** Age at which the pull lets go and the ball starts to come down. */
    public static final int HOLD_END = 410;

    /** Age at which the ball hits the ground. */
    public static final int IMPACT_AGE = 460;

    /** Age at which the rubble has scattered and the entity can go. */
    public static final int BURST_END = IMPACT_AGE + 30;

    /** Radius of the smallest ball, in blocks. A full one is {@code NarutoConfig.chibakuTenseiRadius}. */
    public static final float MIN_RADIUS = 5.0f;

    /** How far above the ground the core settles at the smallest and the fullest charge. */
    private static final double MIN_RISE = 8.0d;
    private static final double MAX_RISE = 26.0d;

    /** How far the pull reaches, as a multiple of the ball's own radius, and its floor. */
    private static final double PULL_REACH = 2.6d;
    private static final double MIN_PULL_REACH = 20.0d;

    /** The biggest step the pull moves anything in a tick, in blocks. */
    private static final double PULL_MAX_STEP = 0.7d;

    /** Ticks between the bites the ball takes out of what it drags, and between core blasts. */
    private static final int PULL_DAMAGE_INTERVAL = 20;
    private static final int CORE_BLAST_INTERVAL = 4;

    /** Damage of the landing at the centre of the smallest and of the fullest ball. */
    private static final float MIN_IMPACT_DAMAGE = 20.0f;
    private static final float MAX_IMPACT_DAMAGE = 55.0f;

    /** The charge the caster put in, 0.2 for a tap up to 1.0 for a full one. Synched: the height
     *  of the ball is derived from it, so the client must not guess. */
    private static final EntityDataAccessor<Float> DATA_POWER =
            SynchedEntityData.defineId(ChibakuTenseiEntity.class, EntityDataSerializers.FLOAT);

    /**
     * The radius the charge makes, synched so the client draws exactly the ball the server is
     * working with even if the two config files disagree.
     */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ChibakuTenseiEntity.class, EntityDataSerializers.FLOAT);

    /** How far into the technique the ball is. Synched so the client draws the same movement. */
    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(ChibakuTenseiEntity.class, EntityDataSerializers.INT);

    /**
     * The ground the ball was cast on. Synched because the whole climb is computed from it on both
     * sides - without it the client would draw the ball at world height zero.
     */
    private static final EntityDataAccessor<Float> DATA_START_Y =
            SynchedEntityData.defineId(ChibakuTenseiEntity.class, EntityDataSerializers.FLOAT);

    /** Who cast it, and therefore who is spared by the storm. */
    @Nullable
    private UUID owner;

    /**
     * The clock the ball is drawn on. It is advanced one tick per client tick instead of being read
     * straight off the synced age, so a packet that lands halfway between two frames can never make
     * the ball jump: the drawing always moves in even, continuous steps.
     */
    private float visualAge;
    private float prevVisualAge;

    public ChibakuTenseiEntity(EntityType<? extends ChibakuTenseiEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /**
     * @param caster the ninja who cast it
     * @param x      the point on the ground the ball is built over
     * @param y      that point's height
     * @param z      the same, on the other axis
     */
    public ChibakuTenseiEntity(Player caster, double x, double y, double z) {
        this(NarutoEntities.CHIBAKU_TENSEI.get(), caster.level());
        this.setPos(x, y, z);
        this.entityData.set(DATA_START_Y, (float) y);
        this.owner = caster.getUUID();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_POWER, 1.0f);
        this.entityData.define(DATA_RADIUS, MIN_RADIUS);
        this.entityData.define(DATA_AGE, 0);
        this.entityData.define(DATA_START_Y, 0.0f);
    }

    /** The ground the ball was cast on; the whole climb is measured from here. */
    public double getStartY() {
        return this.entityData.get(DATA_START_Y);
    }

    /** The radius of the ball a given charge makes, before the shell has finished condensing. */
    public static float radiusFor(float power) {
        return MIN_RADIUS + (maxRadius() - MIN_RADIUS) * Mth.clamp(power, 0.0f, 1.0f);
    }

    /**
     * The biggest the ball can get. Read from the config every time rather than cached, so a server
     * owner changing the number sees it on the next cast without a restart.
     */
    public static float maxRadius() {
        return Math.max(MIN_RADIUS + 1.0f, NarutoConfig.chibakuTenseiRadius);
    }

    public void setPower(float power) {
        float clamped = Mth.clamp(power, 0.0f, 1.0f);
        this.entityData.set(DATA_POWER, clamped);
        this.entityData.set(DATA_RADIUS, radiusFor(clamped));
    }

    /** Synched, because the climb is derived from it and both sides have to agree on the height. */
    public float getPower() {
        return this.entityData.get(DATA_POWER);
    }

    /** The radius the charge is worth. Synched, so both sides agree on the size of the ball. */
    public float getFullRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getAge() {
        return this.entityData.get(DATA_AGE);
    }

    private void setAge(int age) {
        this.entityData.set(DATA_AGE, age);
    }

    /**
     * How big the ball is. The blocks that build it are drawn flying in from the ground, so the
     * size is fixed from the first tick and the shell simply starts empty and fills up.
     */
    public float ballRadius() {
        return this.getFullRadius();
    }

    /** The highest the core climbs, measured from where it starts. */
    private double peakY() {
        return baseY() + MIN_RISE + (MAX_RISE - MIN_RISE) * this.getPower();
    }

    /**
     * Where the ball sits before it lifts: its underside exactly on the ground. Starting the centre
     * on the ground would have put the whole lower half of the shell underground for the first
     * seconds, and the blocks flying into it would have disappeared into the dirt.
     */
    private double baseY() {
        return this.getStartY() + this.getFullRadius() + 1.0d;
    }

    @Nullable
    public Entity getOwner() {
        return this.owner == null ? null : this.level().getPlayerByUUID(this.owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.tickVisualAge();
            this.drawParticles();
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        int age = this.getAge();
        this.setAge(age + 1);
        if (age == IMPACT_AGE) {
            this.impact(level);
        }
        if (age >= BURST_END) {
            this.discard();
            return;
        }
        if (age >= IMPACT_AGE) {
            // The landing has happened; all that is left is the rubble coming down, which the
            // renderer draws on its own until BURST_END.
            return;
        }

        if (age == 1) {
            level.playSound(null, this.getX(), this.ballCenterY(0.0f), this.getZ(),
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4.0f, 0.55f);
        }
        this.storm(level, age);
        this.pull(level, age);
    }

    /**
     * Where the ball's centre is at this moment.
     *
     * <p>The entity itself never moves: it stays where it was cast. The climb is a function of the
     * age and nothing else, so both sides work out the same position, and the client can evaluate it
     * for any fraction between two ticks. Moving the entity itself would have meant a position
     * packet a tick, and a plain {@code Entity} snaps to each one instead of sliding between them -
     * which is exactly what made the ball stutter its way into the sky.</p>
     */
    public double ballCenterY(float partialTicks) {
        return this.ballCenterYAt(this.renderAge(partialTicks));
    }

    /** The same climb, evaluated at any age. */
    private double ballCenterYAt(double age) {
        double base = this.baseY();
        double peak = this.peakY();
        if (age <= RISE_END) {
            double t = Mth.clamp(age / (double) RISE_END, 0.0d, 1.0d);
            return Mth.lerp(t * t * (3.0d - 2.0d * t), base, peak);
        }
        if (age <= HOLD_END) {
            // A slow swell while it hangs, so the ball breathes instead of being a frozen picture.
            return peak + Math.sin(age * 0.05d) * 0.4d;
        }
        double t = Mth.clamp((age - HOLD_END) / (double) (IMPACT_AGE - HOLD_END), 0.0d, 1.0d);
        // It comes back down onto the very spot it was lifted from, underside on the ground, and
        // breaks apart there.
        return Mth.lerp(t * t, peak, base);
    }

    /**
     * The clock the renderer should use, in ticks. On the client that is the smoothed one; on the
     * server there is nothing to draw, so the synced age is exact enough.
     */
    public float renderAge(float partialTicks) {
        if (!this.level().isClientSide) {
            return this.getAge() + partialTicks;
        }
        return Mth.lerp(partialTicks, this.prevVisualAge, this.visualAge);
    }

    /**
     * Moves the drawing clock on by exactly one tick, and snaps it to the synced age only when the
     * two have drifted apart (a chunk reload, or a very long stall).
     */
    private void tickVisualAge() {
        this.prevVisualAge = this.visualAge;
        this.visualAge += 1.0f;
        double gap = this.getAge() - this.visualAge;
        if (gap > 4.0d || gap < -10.0d) {
            this.visualAge = this.getAge();
        }
    }

    /** The sound and dust of the ball turning over the battlefield. */
    private void storm(ServerLevel level, int age) {
        double centerY = this.ballCenterY(0.0f);
        if (age % 60 == 0 && age > RISE_END / 2) {
            level.playSound(null, this.getX(), centerY, this.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.32f);
        }
        if (age < RISE_END) {
            // Dust torn out of the ground under the core while it climbs.
            double progress = age / (double) RISE_END;
            double spread = this.ballRadius() * 1.15d;
            level.sendParticles(ParticleTypes.CLOUD, this.getX(), this.getStartY() + 0.5d, this.getZ(),
                    6, spread, 0.4d, spread, 0.06d);
            if (progress > 0.4d) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getStartY() + 1.0d, this.getZ(),
                        3, spread * 0.8d, 0.3d, spread * 0.8d, 0.02d);
            }
        }
        if (age > RISE_END && age < HOLD_END && age % 10 == 0) {
            double spread = this.ballRadius() * 1.05d;
            level.sendParticles(ParticleTypes.PORTAL, this.getX(), centerY, this.getZ(),
                    8, spread, spread, spread, 0.35d);
            level.sendParticles(ParticleTypes.SQUID_INK, this.getX(), centerY, this.getZ(),
                    3, spread * 0.35d, spread * 0.35d, spread * 0.35d, 0.02d);
        }
    }

    /**
     * Pulls everything that is not on the caster's side smoothly into the core of the ball.
     *
     * <p>There is no teleporting and no parking on the shell any more: a creature is eased towards
     * the centre a little faster the further away it is, so it hangs on the pull instead of being
     * snapped around. Whatever reaches the heart of the ball is inside the blast furnace that the
     * core has become.</p>
     *
     * <p>The reach is an ellipsoid rather than a sphere: as the ball climbs, the pull keeps reaching
     * all the way down to the ground it came from, so a caster does not make a big ball that then
     * leaves the battlefield standing under it untouched.</p>
     */
    private void pull(ServerLevel level, int age) {
        double centerY = this.ballCenterY(0.0f);
        // A cylinder, not a sphere: from the ground it has to reach as wide as the ball's own
        // gathering ring, or the ball pulls blocks from further out than it pulls creatures and the
        // field around it is left untouched. The floor keeps even a tapped ball from collecting
        // only what is right under it.
        double reach = Mth.clamp(this.ballRadius() * PULL_REACH, MIN_PULL_REACH, 34.0d);
        AABB area = new AABB(this.getX() - reach, this.getStartY() - 4.0d, this.getZ() - reach,
                this.getX() + reach, centerY + reach, this.getZ() + reach);
        List<Entity> nearby = level.getEntities(this, area, this::canAffect);

        Vec3 center = this.ballCenter(0.0f);
        for (Entity target : nearby) {
            Vec3 middle = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d);
            double dx = middle.x - center.x;
            double dz = middle.z - center.z;
            if (dx * dx + dz * dz > reach * reach || middle.y > center.y + reach) {
                continue;
            }

            Vec3 towards = center.subtract(middle);
            double distance = towards.length();
            if (distance < 0.001d) {
                continue;
            }
            // Moved by hand, a small step at a time, straight at the centre. Velocity alone let the
            // mob's own movement AI fight the pull and made it look like things were being dragged
            // along the ground under the ball; a step of at most PULL_MAX_STEP can neither be fought
            // nor overshoot, and the client interpolates the result into a smooth slide.
            double step = Math.min(PULL_MAX_STEP, 0.10d + distance * 0.035d);
            Vec3 next = middle.add(towards.scale(step / distance));
            target.teleportTo(next.x, next.y - target.getBbHeight() * 0.5d, next.z);
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            target.fallDistance = 0.0f;

            if (age % PULL_DAMAGE_INTERVAL == 0 && target instanceof LivingEntity living) {
                this.bite(living, 1.0f + this.getPower());
            }
            if (age % 6 == 0) {
                trailParticles(target);
            }
        }

        if (age % CORE_BLAST_INTERVAL == 0) {
            this.coreBlast(level, center, age);
        }

        if (DEBUG_PULL && age % 20 == 0) {
            Entity first = nearby.isEmpty() ? null : nearby.get(0);
            LOGGER.info("Chibaku pull: age={} center=({}, {}, {}) startY={} radius={} inRange={} first={}",
                    age, String.format("%.1f", center.x), String.format("%.1f", center.y),
                    String.format("%.1f", center.z), String.format("%.1f", this.getStartY()),
                    String.format("%.1f", this.getFullRadius()), nearby.size(),
                    first == null ? "-"
                            : String.format("%s at (%.1f, %.1f, %.1f) dist %.1f",
                            first.getType().toShortString(), first.getX(), first.getY(), first.getZ(),
                            first.position().distanceTo(center)));
        }
    }

    /**
     * The heart of the ball: a running series of blasts that eats whatever was dragged into it.
     *
     * <p>The blasts are particles and damage rather than real explosions, so they never shove the
     * catch back out of the core: the point is that everything pulled in stays in and is ground
     * down, a little at a time but several times a second. Bursts are also thrown out on the shell,
     * because from outside the ball is a solid mass of rock and the only place the violence shows
     * is its surface.</p>
     */
    private void coreBlast(ServerLevel level, Vec3 center, int age) {
        float power = this.getPower();
        double reach = 2.0d + 3.0d * power;
        AABB area = new AABB(center.x - reach, center.y - reach, center.z - reach,
                center.x + reach, center.y + reach, center.z + reach);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, this::canAffect)) {
            if (target.distanceToSqr(center) <= reach * reach) {
                this.bite(target, 1.0f + 1.0f * power);
            }
        }

        float radius = this.ballRadius();
        RandomSource random = level.random;
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                1, radius * 0.2d, radius * 0.2d, radius * 0.2d, 0.0d);
        // A burst on the shell itself so the player sees the ball churning.
        double theta = random.nextDouble() * Math.PI * 2.0d;
        double phi = Math.acos(2.0d * random.nextDouble() - 1.0d);
        double x = center.x + Math.sin(phi) * Math.cos(theta) * radius;
        double y = center.y + Math.cos(phi) * radius;
        double z = center.z + Math.sin(phi) * Math.sin(theta) * radius;
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0.2d, 0.2d, 0.2d, 0.0d);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.2d, 0.2d, 0.2d, 0.02d);

        // Every other burst also gets the bigger effects, so it roars without turning into noise.
        if (age % (CORE_BLAST_INTERVAL * 2) == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z,
                    1, radius * 0.2d, radius * 0.2d, radius * 0.2d, 0.0d);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                    0.5f + radius * 0.03f, 0.9f + random.nextFloat() * 0.3f);
        }
    }

    /** The centre of the ball right now, for anything that needs to measure from it. */
    public Vec3 ballCenter(float partialTicks) {
        return new Vec3(this.getX(), this.ballCenterY(partialTicks), this.getZ());
    }

    private void trailParticles(Entity target) {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL, target.getX(),
                    target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    2, 0.3d, 0.3d, 0.3d, 0.05d);
        }
    }

    private void bite(LivingEntity target, float damage) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Entity caster = this.getOwner();
        // The core bites several times a second; the vanilla hurt cooldown would swallow most of
        // those hits, so it is cleared before every one. The hit is dealt from the centre of the
        // ball, which also keeps the knockback pointing out of the core instead of sideways from
        // the point on the ground the entity sits on.
        target.invulnerableTime = 0;
        target.hurt(NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.CHIBAKU_TENSEI,
                this, caster, this.ballCenter(0.0f)), damage);
    }

    /**
     * The landing: one crushing blow at ground level. The ball itself breaks apart over the next
     * second and a half, which the renderer animates, so this does not discard the entity.
     */
    private void impact(ServerLevel level) {
        float radius = this.ballRadius();
        double reach = radius * 1.9d;
        double x = this.getX();
        double y = this.getStartY() + 1.0d;
        double z = this.getZ();

        Entity caster = this.getOwner();
        DamageSource source = NarutoDamageTypes.getDamageSource(level,
                NarutoDamageTypes.CHIBAKU_TENSEI, this, caster);
        AABB area = new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, this::canAffect)) {
            double distance = Math.sqrt(target.distanceToSqr(x, y, z));
            double falloff = Math.max(0.0d, 1.0d - distance / reach);
            float damage = (float) (Mth.lerp(this.getPower(), MIN_IMPACT_DAMAGE, MAX_IMPACT_DAMAGE)
                    * (0.25d + 0.75d * falloff));
            target.hurt(source, damage);

            Vec3 push = target.position().subtract(x, y, z);
            if (push.lengthSqr() < 0.0001d) {
                push = new Vec3(0.0d, 0.0d, 1.0d);
            }
            push = push.normalize().scale(1.1d + 1.6d * falloff);
            target.push(push.x, 0.8d + 0.7d * falloff, push.z);
            target.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z,
                6, radius * 0.35d, radius * 0.15d, radius * 0.35d, 0.0d);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z,
                40, radius * 0.55d, radius * 0.2d, radius * 0.55d, 0.0d);
        level.sendParticles(ParticleTypes.FLASH, x, y, z,
                4, radius * 0.3d, radius * 0.1d, radius * 0.3d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, x, y, z,
                260, radius * 0.95d, radius * 0.35d, radius * 0.95d, 0.65d);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z,
                120, radius * 0.8d, radius * 0.25d, radius * 0.8d, 0.12d);
        level.sendParticles(ParticleTypes.SQUID_INK, x, y, z,
                70, radius * 0.45d, radius * 0.2d, radius * 0.45d, 0.12d);
        // Real ground thrown out of the crater that is never actually made: the debris is a
        // particle of what is still lying under the ball, not a block taken out of it.
        BlockState ground = level.getBlockState(BlockPos.containing(x, this.getStartY() - 1.0d, z));
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            ground = Blocks.STONE.defaultBlockState();
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y, z,
                300, radius * 0.85d, radius * 0.3d, radius * 0.85d, 0.45d);

        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 8.0f, 0.35f);
        level.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 5.0f, 0.6f);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0f, 0.45f);
        level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 6.0f, 0.3f);
        level.gameEvent(this, NarutoGameEvents.JUTSU_BOOM.get(), new Vec3(x, y, z));
    }

    /** Anything but the caster, their allies, another ball, and the untouchable. */
    private boolean canAffect(Entity target) {
        if (target == this || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        // Two balls cast near each other must not start dragging one another around.
        if (target instanceof ChibakuTenseiEntity) {
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

    /** Chakra dust and ink around the core, drawn by whoever can see the ball. */
    private void drawParticles() {
        float radius = this.ballRadius();
        if (radius < 0.5f) {
            return;
        }
        double centerY = this.ballCenterY(0.0f);
        RandomSource random = this.random;
        for (int i = 0; i < 4; i++) {
            double theta = random.nextDouble() * Math.PI * 2.0d;
            double phi = Math.acos(2.0d * random.nextDouble() - 1.0d);
            double dx = Math.sin(phi) * Math.cos(theta);
            double dy = Math.cos(phi);
            double dz = Math.sin(phi) * Math.sin(theta);
            double distance = radius * (1.12d + random.nextDouble() * 0.2d);
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX() + dx * distance, centerY + dy * distance, this.getZ() + dz * distance,
                    -dx * 0.16d, -dy * 0.16d, -dz * 0.16d);
        }
        if (this.tickCount % 2 == 0) {
            double spread = radius * 0.45d;
            this.level().addParticle(ParticleTypes.SQUID_INK,
                    this.getX() + (random.nextDouble() - 0.5d) * spread,
                    centerY + (random.nextDouble() - 0.5d) * spread,
                    this.getZ() + (random.nextDouble() - 0.5d) * spread,
                    0.0d, 0.0d, 0.0d);
        }
        if (this.tickCount % 3 == 0) {
            double theta = random.nextDouble() * Math.PI * 2.0d;
            this.level().addParticle(ParticleTypes.END_ROD,
                    this.getX() + Math.cos(theta) * radius * 0.9d,
                    centerY + (random.nextDouble() - 0.35d) * radius,
                    this.getZ() + Math.sin(theta) * radius * 0.9d,
                    0.0d, 0.01d, 0.0d);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
        this.entityData.set(DATA_START_Y, tag.getFloat("StartY"));
        this.setPower(tag.getFloat("Power"));
        this.setAge(tag.getInt("Age"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
        tag.putFloat("StartY", (float) this.getStartY());
        tag.putFloat("Power", this.getPower());
        tag.putInt("Age", this.getAge());
    }

    /**
     * The ball is far bigger than its box, so the culling box has to be told: without this the ball
     * vanishes as soon as its centre leaves the screen while its edge is still in view. The box
     * stretches from the ground, where the blocks launch, up past the ball itself, so neither half
     * of the picture disappears when only one of them is in frame.
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        double half = this.getFullRadius() * 2.5d + 6.0d;
        double bottom = this.getStartY() - 2.0d;
        double top = Math.max(this.ballCenterY(0.0f) + half, bottom + 2.0d * half);
        return new AABB(this.getX() - half, bottom, this.getZ() - half,
                this.getX() + half, top, this.getZ() + half);
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
        double drawDistance = Math.max(128.0d, this.getFullRadius() * 24.0d);
        return distance < drawDistance * drawDistance;
    }
}
