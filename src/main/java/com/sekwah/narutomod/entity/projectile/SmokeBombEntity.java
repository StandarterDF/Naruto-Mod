package com.sekwah.narutomod.entity.projectile;

import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.SmokeCloudEntity;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * A smoke bomb in flight: the thrown counterpart of the smoke screen technique.
 *
 * <p>The bomb is deliberately not a second kind of smoke. When it lands it simply spawns an
 * ordinary {@link SmokeCloudEntity} with a smaller radius and a shorter life than the technique's,
 * so everything the cloud does - hiding everyone inside, dropping mob targets, darkening eyes - is
 * the same code. A ninja who has not learned the combo can still carry a few of these; that is the
 * point of the item.</p>
 */
public class SmokeBombEntity extends ThrowableItemProjectile {

    /** The cloud a thrown bomb leaves behind - smaller than the technique's, and it goes sooner. */
    public static final double CLOUD_RADIUS = 3.0d;

    /** Life of the thrown cloud, in ticks. Five seconds is enough to break a chase. */
    public static final int CLOUD_LIFETIME = 20 * 5;

    public SmokeBombEntity(EntityType<? extends SmokeBombEntity> type, Level level) {
        super(type, level);
    }

    public SmokeBombEntity(Level level, LivingEntity shooter) {
        super(NarutoEntities.SMOKE_BOMB.get(), shooter, level);
    }

    public SmokeBombEntity(Level level, double x, double y, double z) {
        super(NarutoEntities.SMOKE_BOMB.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return NarutoItems.SMOKE_BOMB.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        this.popCloud();
        this.discard();
    }

    /** Puts the cloud down where the bomb came to rest, owned by whoever threw it. */
    private void popCloud() {
        Level level = this.level();
        SmokeCloudEntity cloud = new SmokeCloudEntity(NarutoEntities.SMOKE_CLOUD.get(), level);
        cloud.setCloudSize(CLOUD_RADIUS, CLOUD_LIFETIME);
        cloud.setPos(this.getX(), this.getY() + 0.4d, this.getZ());
        if (this.getOwner() instanceof Player player) {
            cloud.setOwner(player);
        }
        level.addFreshEntity(cloud);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 0.5f, 0.8f);
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    this.getX() + (this.random.nextDouble() - 0.5d) * CLOUD_RADIUS,
                    this.getY() + this.random.nextDouble() * 1.2d,
                    this.getZ() + (this.random.nextDouble() - 0.5d) * CLOUD_RADIUS,
                    0.0d, 0.01d, 0.0d);
        }
    }
}
