package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The upkeep of genjutsu: what a mob does while its mind is being held.
 *
 * <p>A victim keeps the illusion until its timer runs out. While it lasts the mob is not allowed to
 * see players at all - every attempt to target one is refused - and instead of standing around
 * confused it is pointed at the nearest other mob and set loose, which is the "your enemies are my
 * enemies" reading of the technique. When the timer expires the mob is handed back to vanilla
 * behaviour with no memory of it.</p>
 *
 * <p>The affected flag lives in the entity's persistent data rather than a map kept in memory, so a
 * mob that is unloaded and loaded again (or saved and reloaded with the world) keeps the illusion for
 * exactly as long as it should.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GenjutsuEvents {

    /** Game time at which the illusion drops off the mob. */
    private static final String END_TAG = "narutomod:genjutsu_until";

    /** Who cast it, kept mostly for debugging and for future caster flavoured behaviour. */
    private static final String CASTER_TAG = "narutomod:genjutsu_caster";

    /** How far a victim looks for something else to fight. */
    private static final double SEARCH_RANGE = 16.0d;

    /** How often a victim that has lost its target is given a new one, in ticks. */
    private static final int RETARGET_INTERVAL = 20;

    /**
     * Puts a mob under the illusion. Server side only, callers are expected to have checked that the
     * mob is a sensible victim.
     *
     * @param caster   the ninja who cast it.
     * @param victim   the mob that is now seeing things.
     * @param duration how long the illusion lasts, in ticks.
     */
    public static void apply(Player caster, Mob victim, int duration) {
        CompoundTag data = victim.getPersistentData();
        data.putLong(END_TAG, victim.level().getGameTime() + duration);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        // Whatever it was about to hit, it is no longer its enemy.
        victim.setTarget(null);
    }

    /** Drops the illusion early. */
    public static void clear(Mob victim) {
        CompoundTag data = victim.getPersistentData();
        data.remove(END_TAG);
        data.remove(CASTER_TAG);
        victim.setTarget(null);
    }

    /** @return whether the mob is currently under the illusion. */
    public static boolean isUnderGenjutsu(Mob mob) {
        return mob.getPersistentData().contains(END_TAG);
    }

    /** @return the illusion's end time, or 0 when the mob is not affected. */
    public static long getEnd(Mob mob) {
        return mob.getPersistentData().getLong(END_TAG);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (!isUnderGenjutsu(mob)) {
            return;
        }
        if (mob.level().getGameTime() >= getEnd(mob)) {
            clear(mob);
            return;
        }

        // A player that somehow became the target (a goal setting it directly, or an order from a
        // plugin) is dropped immediately rather than only when the goal next re-evaluates.
        LivingEntity target = mob.getTarget();
        if (target != null && !isValidVictim(mob, target)) {
            mob.setTarget(null);
            target = null;
        }

        if (target == null && mob.tickCount % RETARGET_INTERVAL == 0) {
            LivingEntity next = findVictim(mob);
            if (next != null) {
                mob.setTarget(next);
            }
        }

        if (mob.tickCount % 10 == 0 && mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WITCH, mob.getX(),
                    mob.getY() + mob.getBbHeight() + 0.3d, mob.getZ(), 3, 0.3d, 0.2d, 0.3d, 0.0d);
        }
    }

    /**
     * Refuses targeting changes that would break the illusion, which is what actually keeps a victim
     * off the caster: the vanilla targeting goals fire this every time they pick a player.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (!isUnderGenjutsu(mob)) {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        // Clearing the target must stay possible, or the victim could never be let go.
        if (newTarget == null) {
            return;
        }
        if (!isValidVictim(mob, newTarget)) {
            event.setCanceled(true);
        }
    }

    /**
     * @return whether the mob is allowed to see the given entity as an enemy while under the illusion.
     *         Players never are, nor are other victims, nor anything the mob is already allied with.
     */
    private static boolean isValidVictim(Mob mob, LivingEntity candidate) {
        if (candidate == mob || !candidate.isAlive()) {
            return false;
        }
        // The whole point: a victim cannot see ninja at all.
        if (candidate instanceof Player) {
            return false;
        }
        if (candidate instanceof com.sekwah.narutomod.entity.ShadowCloneEntity) {
            return false;
        }
        if (candidate instanceof Mob other && isUnderGenjutsu(other)) {
            return false;
        }
        return !mob.isAlliedTo(candidate);
    }

    /** @return the nearest mob the victim is allowed to attack, or null when there is none around. */
    private static LivingEntity findVictim(Mob mob) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(SEARCH_RANGE))) {
            if (!isValidVictim(mob, other)) {
                continue;
            }
            double distance = mob.distanceToSqr(other);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }
}
