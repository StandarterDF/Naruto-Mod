package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.entity.projectile.ShurikenEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns each shadow shuriken of a cast into a shadow clone when it lands: two blades, two clones.
 *
 * <p>Each blade is an ordinary shuriken with a mark on it. The moment it meets a body or a
 * block, the damage is applied here, the blade is taken out of the world, and a one-health combat
 * clone is left standing where it struck. The clone is built by the same constructor as a normal
 * shadow clone, so it copies whatever the caster is holding at that moment - the shuriken is only
 * the delivery, the copy comes out armed like its owner.</p>
 *
 * <p>The blades fly almost the same line, so the second one may catch up with the first one's
 * clone still standing where it landed. That clone is the caster's own and is never a target: the
 * blade passes through the hit (leaving the clone alive) and still leaves its own clone behind.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShadowShurikenEvents {

    /** Marks a blade of a cast as allowed to leave a clone behind. Both blades carry it. */
    public static final String CLONE_TAG = "narutomod:shadow_shuriken_clone";

    /** The caster, kept as a UUID so the impact can find the body even after a reload. */
    public static final String OWNER_TAG = "narutomod:shadow_shuriken_owner";

    /** How long the clone stays, in ticks. */
    private static final int CLONE_LIFETIME = 20 * 20;

    private ShadowShurikenEvents() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ShurikenEntity shuriken) || shuriken.level().isClientSide) {
            return;
        }
        if (!shuriken.getPersistentData().getBoolean(CLONE_TAG)) {
            return;
        }
        if (!(shuriken.level() instanceof ServerLevel level)) {
            return;
        }
        // Take the mark off before anything else, so a second event from the same blade cannot
        // produce a second clone.
        shuriken.getPersistentData().remove(CLONE_TAG);

        Player caster = casterOf(shuriken);
        if (caster == null) {
            return;
        }

        // Vanilla would handle the hit after this event; the blade is resolved here instead so it
        // can be removed cleanly and leave a clone in exactly the place it landed.
        Vec3 impact = event.getRayTraceResult().getLocation();
        LivingEntity hitTarget = null;
        if (event.getRayTraceResult() instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity target
                && isFairTarget(caster, target)) {
            target.hurt(shuriken.damageSources().thrown(shuriken, caster), (float) shuriken.getBaseDamage());
            hitTarget = target;
        }
        event.setCanceled(true);
        spawnClone(level, caster, impact, hitTarget);
        shuriken.discard();
    }

    /**
     * @return whether the blade may hurt this body. Anything on the caster's side is out - above
     *     all the caster's own clones, which an {@code areFriends} check alone does not catch (a
     *     clone answers to the caster's own id, and same-id pairs are never "friends"). Without
     *     this the second blade of a cast executes the first blade's clone on the way in, and two
     *     blades leave one clone instead of two.
     */
    private static boolean isFairTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive()) {
            return false;
        }
        if (caster.getUUID().equals(Friends.playerId(target))) {
            return false;
        }
        return !Friends.isOwnSide(caster, target);
    }

    /** @return the player who threw the blade, or null once they have left the world. */    private static Player casterOf(ShurikenEntity shuriken) {
        Entity owner = shuriken.getOwner();
        if (owner instanceof Player player) {
            return player;
        }
        String uuid = shuriken.getPersistentData().getString(OWNER_TAG);
        if (uuid.isEmpty()) {
            return null;
        }
        for (Player player : shuriken.level().players()) {
            if (player.getStringUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    /** Leaves a one-health fighting copy where the blade struck. */
    private static void spawnClone(ServerLevel level, Player caster, Vec3 impact, LivingEntity target) {
        ShadowCloneEntity clone = new ShadowCloneEntity(level, caster, CLONE_LIFETIME, false, false);
        clone.setWorker(false);
        clone.setHealth(1.0f);
        if (target != null) {
            clone.setTarget(target);
        }

        // The blade can land on a face or inside a block; a clone is put in the first open spot
        // above the impact rather than suffocating where the shuriken stopped.
        for (int dy = 0; dy <= 3; dy++) {
            clone.moveTo(impact.x, impact.y + dy, impact.z, caster.getYRot(), 0.0f);
            if (level.noCollision(clone, clone.getBoundingBox())) {
                break;
            }
        }
        level.addFreshEntity(clone);
        level.sendParticles(ParticleTypes.CLOUD, clone.getX(),
                clone.getY() + clone.getBbHeight() * 0.5d, clone.getZ(),
                30, 0.45d, 0.55d, 0.45d, 0.02d);
        level.playSound(null, clone.getX(), clone.getY(), clone.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.1f);
    }
}
