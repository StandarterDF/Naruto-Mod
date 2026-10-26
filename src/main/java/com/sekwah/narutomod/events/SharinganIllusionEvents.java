package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.IzanagiAbility;
import com.sekwah.narutomod.abilities.jutsus.IzanamiAbility;
import com.sekwah.narutomod.abilities.jutsus.KotoamatsukamiAbility;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The three Sharingan techniques that write their effect on a body instead of finishing it there.
 *
 * <p>Izanagi rewrites the caster's own death, Izanami waits for a mind to repeat itself, and
 * Kotoamatsukami plants a belief about who is a friend. None of them can be a single moment's work:
 * the first has to answer a blow that has not been thrown yet, the second has to count blows that
 * may come seconds or minutes apart, and the third has to survive the creature walking out of the
 * loaded chunks and back. So each cast writes only a timestamp - and, where it matters, the author's
 * UUID - into the persistent data of the body it affects.</p>
 *
 * <p>That is deliberately not a field holding an {@link Entity} or a {@link Player}: a reference
 * would be stale the moment the target relogs, changes dimension or dies, would keep a chunk of the
 * world in memory for as long as the mark lasts, and would not exist at all on the other side of a
 * server restart. A mark in persistent data is just two numbers in data the target already carries,
 * so it survives everything the body survives and dies with it. Every consequence is enforced here,
 * on the server; the abilities never act after the cast, they only sign the mark and show it once.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class SharinganIllusionEvents {

    /** Game time at which a mind caught in the Izanami loop is handed back its own behaviour. */
    private static final String LOOP_TAG = "narutomod:izanami_loop_until";

    /** Whether the loop was what switched this body's AI off, so the end of the loop only undoes its own work. */
    private static final String LOOP_HELD_AI_TAG = "narutomod:izanami_ai_held";

    /** Blows from the marked hand it takes to close the loop. */
    private static final int HITS_TO_CLOSE = 3;

    /** Four seconds frozen: long enough for the victim to lose its place in the fight. */
    private static final int LOOP_TICKS = 80;

    /** The longest the burnt-out eye can be held shut, whatever the mark says. */
    private static final int MAX_BLIND_TICKS = 900;

    /** What is left of the caster after Izanagi refuses a killing blow. */
    private static final float IZANAGI_REMAINS = 0.4f;

    private SharinganIllusionEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        CompoundTag data = victim.getPersistentData();
        long now = victim.level().getGameTime();

        // A refused blow is not a blow at all: nothing after this point should treat it as one.
        if (refuseDeath(event, victim, data, now)) {
            return;
        }
        countLoopBlow(event, victim, data, now);
        breakBelief(event, data, now);
    }

    /**
     * Izanagi: a blow that would have finished the caster is not allowed to have happened.
     *
     * <p>The mark is spent in the same breath it is read, so one cast can only refuse one death and
     * a body cannot be kept alive by a grace that has already done its work. The blindness that
     * follows is read from the second mark rather than recomputed, because the cast - not this
     * hook - is what decided how long the eye should pay for the rewrite.</p>
     *
     * @return whether the blow was refused, in which case the rest of the hurt handling is moot.
     */
    private static boolean refuseDeath(LivingHurtEvent event, LivingEntity victim, CompoundTag data, long now) {
        long graceUntil = data.getLong(IzanagiAbility.GRACE_TAG);
        if (graceUntil <= now) {
            return false;
        }
        if (event.getAmount() < victim.getHealth()) {
            return false;
        }

        event.setCanceled(true);
        data.remove(IzanagiAbility.GRACE_TAG);
        victim.setHealth(Math.min(victim.getMaxHealth(), victim.getMaxHealth() * IZANAGI_REMAINS));

        long blindTicks = Math.min(MAX_BLIND_TICKS, data.getLong(IzanagiAbility.BLIND_TAG) - now);
        if (blindTicks > 0L) {
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, (int) blindTicks, 0, false, true));
        }
        return true;
    }

    /**
     * Izanami: the same hand striking the same body three times closes the loop.
     *
     * <p>Only blows from the hand that landed the first one count. The author is written on that
     * first hit rather than at cast time, so a mark that was paid for and then ignored by the
     * caster cannot be handed on to whoever else happens to be nearby.</p>
     */
    private static void countLoopBlow(LivingHurtEvent event, LivingEntity victim, CompoundTag data, long now) {
        if (!data.contains(IzanamiAbility.UNTIL_TAG)) {
            return;
        }
        if (data.getLong(IzanamiAbility.UNTIL_TAG) <= now) {
            // The window closed without the loop ever closing; let the stale marks go quietly.
            data.remove(IzanamiAbility.UNTIL_TAG);
            data.remove(IzanamiAbility.HITS_TAG);
            data.remove(IzanamiAbility.SOURCE_TAG);
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == victim) {
            return;
        }
        String author = data.getString(IzanamiAbility.SOURCE_TAG);
        if (author.isEmpty()) {
            data.putString(IzanamiAbility.SOURCE_TAG, attacker.getStringUUID());
        } else if (!author.equals(attacker.getStringUUID())) {
            return;
        }

        int hits = data.getInt(IzanamiAbility.HITS_TAG) + 1;
        if (hits < HITS_TO_CLOSE) {
            data.putInt(IzanamiAbility.HITS_TAG, hits);
            return;
        }

        data.remove(IzanamiAbility.UNTIL_TAG);
        data.remove(IzanamiAbility.HITS_TAG);
        data.remove(IzanamiAbility.SOURCE_TAG);
        data.putLong(LOOP_TAG, now + LOOP_TICKS);
        // Only a body with behaviour can lose it; a player is left with the weakness and nothing
        // else, because freeze-by-illusion on players is not this technique's promise.
        if (victim instanceof Mob mob) {
            mob.setNoAi(true);
            data.putBoolean(LOOP_HELD_AI_TAG, true);
        }
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LOOP_TICKS, 1, false, true));
    }

    /**
     * Kotoamatsukami: the belief ends the moment the caster proves it wrong with a blow.
     *
     * <p>Only the named caster breaks it. Anyone else may hit the creature all day and the belief
     * that the caster is an ally stands, which is what makes the technique usable in a group fight
     * rather than only in a corridor.</p>
     */
    private static void breakBelief(LivingHurtEvent event, CompoundTag data, long now) {
        if (!data.contains(KotoamatsukamiAbility.UNTIL_TAG)) {
            return;
        }
        if (data.getLong(KotoamatsukamiAbility.UNTIL_TAG) <= now) {
            data.remove(KotoamatsukamiAbility.UNTIL_TAG);
            data.remove(KotoamatsukamiAbility.SOURCE_TAG);
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }
        if (data.getString(KotoamatsukamiAbility.SOURCE_TAG).equals(attacker.getStringUUID())) {
            data.remove(KotoamatsukamiAbility.UNTIL_TAG);
            data.remove(KotoamatsukamiAbility.SOURCE_TAG);
        }
    }

    /**
     * The creature refuses to take the caster for an enemy while the belief lasts.
     *
     * <p>The target is cleared to nothing rather than replaced: this is not a decoy and the mob is
     * not being sent after somebody else, it simply does not see the caster as worth attacking. Its
     * other targets are left alone, so a creature that was already fighting somebody keeps fighting
     * them - the illusion is about one person only.</p>
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
        if (data.getLong(KotoamatsukamiAbility.UNTIL_TAG) <= mob.level().getGameTime()) {
            return;
        }
        if (data.getString(KotoamatsukamiAbility.SOURCE_TAG).equals(chosen.getStringUUID())) {
            event.setNewTarget(null);
        }
    }

    /** The loop's four seconds end: the body gets its own behaviour back, but only what was taken. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(LOOP_TAG) || entity.level().getGameTime() < data.getLong(LOOP_TAG)) {
            return;
        }
        data.remove(LOOP_TAG);
        if (!data.getBoolean(LOOP_HELD_AI_TAG)) {
            return;
        }
        data.remove(LOOP_HELD_AI_TAG);
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
    }
}
