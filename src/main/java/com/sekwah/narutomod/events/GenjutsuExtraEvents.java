package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.JubakuSatsuAbility;
import com.sekwah.narutomod.abilities.jutsus.NarakumiAbility;
import com.sekwah.narutomod.abilities.jutsus.SleepTouchAbility;
import com.sekwah.narutomod.abilities.jutsus.TsukuyomiAbility;
import com.sekwah.narutomod.abilities.jutsus.UtakataAbility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The upkeep of the three body-illusions that hold a victim without a mob AI of their own.
 *
 * <p>Narakumi, Jubaku Satsu and Utakata all write a timestamp into the victim's persistent data and
 * expect somebody to keep the consequences alive for as long as the stamp is in the future: a
 * frightened creature that will not look at the caster and backs away, a rooted body that stays
 * rooted, a stunned body that does nothing. That somebody is this class. It never casts anything -
 * the techniques stay self-contained and only write the mark - and it only runs on the server.</p>
 *
 * <p>Keeping the state in tags rather than in fields means a victim that is unloaded and loaded
 * again keeps the illusion for exactly as long as it should, the same way the older genjutsu and
 * the sleep touch do it.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GenjutsuExtraEvents {

    /** How often a frightened creature is pushed away from the caster, in ticks. */
    private static final int FLEE_INTERVAL = 10;

    /** How hard that push is: a run, not a shove into the sky. */
    private static final double FLEE_SPEED = 0.35d;

    /** How often the root reapplies its slowness, in ticks. */
    private static final int ROOT_REFRESH_INTERVAL = 10;

    /** A little longer than the refresh interval, so the body never walks between two refreshes. */
    private static final int ROOT_SLOW_TICKS = 20;

    /** The same weight the cast lays down: the body is held, not merely slowed. */
    private static final int ROOT_AMPLIFIER = 6;

    /** A body that believes it cannot brace takes the first blow badly. */
    private static final float ROOT_HIT_MULTIPLIER = 1.2f;

    /**
     * Other marks that hold a body's AI off. Restoring it when one hold expires while another is
     * still running would free a victim nobody meant to free, so every restore checks them all.
     */
    private static final String SLEEP = SleepTouchAbility.SLEEP_TAG;
    private static final String TSUKUYOMI = TsukuyomiAbility.STUN_TAG;

    private GenjutsuExtraEvents() {
    }

    /** Runs every mark, and drops each one as soon as its time is up. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        long now = entity.level().getGameTime();

        if (data.contains(NarakumiAbility.UNTIL_TAG)) {
            if (now >= data.getLong(NarakumiAbility.UNTIL_TAG)) {
                data.remove(NarakumiAbility.UNTIL_TAG);
                data.remove(NarakumiAbility.SOURCE_TAG);
            } else if (entity instanceof Mob mob) {
                flee(mob, data, now);
            }
        }

        if (data.contains(JubakuSatsuAbility.UNTIL_TAG)) {
            if (now >= data.getLong(JubakuSatsuAbility.UNTIL_TAG)) {
                endRoot(entity, data);
            } else if (entity instanceof Mob mob) {
                // The victim's own goals are off, so the hold has to be re-stamped by hand: a
                // slowness that simply expired would let the body drift out of the snare.
                if (now % ROOT_REFRESH_INTERVAL == 0L) {
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_SLOW_TICKS,
                            ROOT_AMPLIFIER, false, true));
                }
                mob.setNoAi(true);
            }
        }

        if (data.contains(UtakataAbility.UNTIL_TAG)) {
            if (now >= data.getLong(UtakataAbility.UNTIL_TAG)) {
                data.remove(UtakataAbility.UNTIL_TAG);
                data.remove(UtakataAbility.SOURCE_TAG);
                restoreAiIfFree(entity, data, now);
            } else if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }
    }

    /**
     * The first blow on a rooted body lands harder and breaks the illusion - the same bargain the
     * sleep touch makes: a technique that holds a mind is ended by something that hurts enough.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        CompoundTag data = victim.getPersistentData();
        if (data.getLong(JubakuSatsuAbility.UNTIL_TAG) <= victim.level().getGameTime()) {
            return;
        }
        event.setAmount(event.getAmount() * ROOT_HIT_MULTIPLIER);
        endRoot(victim, data);
    }

    /**
     * A frightened creature cannot take the caster as a target, and neither can a stunned one turn
     * on the ninja who stopped it. Clearing a target must stay possible, or neither mark could ever
     * be let go of, so only a real choice is refused.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        LivingEntity chosen = event.getNewTarget();
        if (chosen == null) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        long now = mob.level().getGameTime();

        if (data.getLong(NarakumiAbility.UNTIL_TAG) > now
                && data.getString(NarakumiAbility.SOURCE_TAG).equals(chosen.getStringUUID())) {
            event.setNewTarget(null);
            return;
        }
        if (data.getLong(UtakataAbility.UNTIL_TAG) > now
                && data.getString(UtakataAbility.SOURCE_TAG).equals(chosen.getStringUUID())) {
            event.setNewTarget(null);
        }
    }

    /**
     * A creature that has lost its nerve drops whoever it was fighting and backs away from the
     * caster. The push is only a nudge every half second: the legs should look like they are
     * running, not like the body is being thrown.
     */
    private static void flee(Mob mob, CompoundTag data, long now) {
        Player caster = casterOf(mob, data.getString(NarakumiAbility.SOURCE_TAG));
        if (caster == null) {
            return;
        }
        if (mob.getTarget() == caster) {
            mob.setTarget(null);
        }
        // A creature that has found somebody else to fight is already out of the caster's way.
        if (mob.getTarget() != null || now % FLEE_INTERVAL != 0L) {
            return;
        }
        mob.setTarget(null);
        Vec3 away = mob.position().subtract(caster.position());
        away = new Vec3(away.x, 0.0d, away.z);
        if (away.lengthSqr() < 1.0e-4d) {
            return;
        }
        away = away.normalize().scale(FLEE_SPEED);
        mob.setDeltaMovement(away.x, mob.getDeltaMovement().y, away.z);
        mob.hurtMarked = true;
    }

    /** Ends a root early: the mark, the weight on the legs and the mind's hold on the body. */
    private static void endRoot(LivingEntity entity, CompoundTag data) {
        data.remove(JubakuSatsuAbility.UNTIL_TAG);
        MobEffectInstance slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null && slow.getAmplifier() >= ROOT_AMPLIFIER) {
            entity.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        restoreAiIfFree(entity, data, entity.level().getGameTime());
    }

    /** Gives a mob its AI back, unless another mark is still holding it. */
    private static void restoreAiIfFree(LivingEntity entity, CompoundTag data, long now) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (data.getLong(JubakuSatsuAbility.UNTIL_TAG) > now
                || data.getLong(UtakataAbility.UNTIL_TAG) > now
                || data.getLong(SLEEP) > now
                || data.getLong(TSUKUYOMI) > now) {
            return;
        }
        mob.setNoAi(false);
    }

    /** @return the caster if they are still in the world, or null once they have logged out. */
    private static Player casterOf(LivingEntity entity, String uuid) {
        if (uuid.isEmpty()) {
            return null;
        }
        for (Player player : entity.level().players()) {
            if (player.getStringUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }
}
