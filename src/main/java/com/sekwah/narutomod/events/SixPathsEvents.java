package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.GudodamaFormAbility;
import com.sekwah.narutomod.abilities.jutsus.GudodamaFormAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * What it means for a body to be in the Six Paths mode.
 *
 * <p>None of this belongs to a cast. The mode is a minute of the caster not being an ordinary body,
 * and the consequences - floating a step off the ground, closing wounds as they open, shrugging off
 * half of what a technique does, refusing to be blinded - have to be reasserted every tick for as
 * long as the mark on the caster says the mode is up. {@link SixPathsModeAbility} only opens and
 * closes it; everything that happens in between is here, in the same way {@code TechniqueMarkEvents}
 * enforces the marks the other techniques leave behind.</p>
 *
 * <p>The mark is the source of truth rather than the toggle set, because a body can lose its stance
 * in ways the ability never hears about - a relog, a death, a stale set - and a mark with a deadline
 * is something the server can always settle on its own. Gravity and the speed bonus are the only
 * things the mode changes outside the caster's data, and both are put back exactly as they were
 * found when the mark runs out or is lifted by hand.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class SixPathsEvents {

    /** Flight state saved so the stance can put it back the way it found it. */
    public static final String FLIGHT_TAG = "narutomod:six_paths_flight";
    private static final String MAYFLY_TAG = "mayfly";
    private static final String FLYING_TAG = "flying";

    /** A stable id, so the speed bonus is added and taken off rather than stacking. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("b3e5d9a2-4c17-4f6b-8a90-2d7e1c6f5b48");

    /** A sage on the move is a third again as fast as the body they were born in. */
    private static final double SPEED_BONUS = 0.3d;

    /** How much of a technique's damage the mode swallows. */
    private static final float TECHNIQUE_DAMAGE_FACTOR = 0.5f;

    /** How much of any damage a shaped Truth-Seeking Orb swallows. */
    private static final float FORM_DAMAGE_FACTOR = 0.8f;

    /**
     * Health the mode gives back on every regeneration beat. The beat is short, so the bar climbs
     * steadily rather than jumping a fistful of hearts once a second; at this rate the mode mends
     * fifteen points a second, fast enough to out-pace most of what a fight can take off.
     */
    private static final float REGEN_PER_BEAT = 3.0f;

    /** How often the mode gives back {@link #REGEN_PER_BEAT} health, in ticks. */
    private static final int REGEN_INTERVAL = 4;

    /** How often the body is also washed clean of everything that is eating it, in ticks. */
    private static final int CLEANSE_INTERVAL = 20;

    private SixPathsEvents() {
    }

    /**
     * Keeps the mode real: its clock, its orbs, its flight and its healing. Everything is read from
     * the caster's own data, so a caster who lost the stance without the ability noticing is cleaned
     * up here on the next tick instead of being left with a minute of free power.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SixPathsModeAbility.UNTIL_TAG)) {
            release(player, data);
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null) {
            return;
        }

        long now = player.level().getGameTime();
        // The orb count is kept honest here rather than trusted from the techniques that spend it.
        int maxOrbs = SixPathsModeAbility.maxOrbs(ninjaData);
        if (GudodamaFormAbility.isActive(ninjaData)) {
            maxOrbs = Math.max(0, maxOrbs - 2);
        }
        int orbs = Mth.clamp(data.getInt(SixPathsModeAbility.ORBS_TAG), 0, maxOrbs);
        data.putInt(SixPathsModeAbility.ORBS_TAG, orbs);
        ninjaData.setSixPathsOrbs(orbs);

        boolean stillRunning = ninjaData.getToggleAbilityData().getAbilitiesHashSet()
                .contains(SixPathsModeAbility.ID);
        if (stillRunning) {
            player.getActiveEffects().stream()
                    .filter(e -> !e.getEffect().isBeneficial())
                    .map(net.minecraft.world.effect.MobEffectInstance::getEffect)
                    .toList()
                    .forEach(player::removeEffect);
            // While the Six Paths mode is up the body is fed on its own: refresh Saturation III
            // every 5 seconds so it stays on for 6 seconds at a time.
            if (player.tickCount % 100 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 120, 2, true, true));
            }
            if (player.tickCount % 20 == 0) {
                ninjaData.addStamina(4.0f);
            }
        }
        // The mode no longer expires on its own; it only ends when the chakra runs out or the stance
        // is dropped. Orbs regenerate back to the maximum over time.
        if (!stillRunning || ninjaData.getChakra() <= 0.0f) {
            end(player, ninjaData);
            release(player, data);
            return;
        }
        long totemUntil = data.getLong("narutomod:orb_totem_until");
        if (totemUntil > 0 && now >= totemUntil) {
            data.remove("narutomod:orb_totem_until");
            totemUntil = 0;
        }
        if (totemUntil == 0 && orbs < maxOrbs && player.tickCount % 200 == 0) {
            orbs = Math.min(maxOrbs, orbs + 1);
            data.putInt(SixPathsModeAbility.ORBS_TAG, orbs);
            ninjaData.setSixPathsOrbs(orbs);
        }

        // The mode lends wings like creative: mayfly is kept on, and the client toggles flying on a
        // double jump on its own. Sneaking lands again.
        if (player.isShiftKeyDown()) {
            releaseFlight(player, data);
        } else {
            grantFlight(player, data);
        }
        applySpeed(player);

        if (player.tickCount % REGEN_INTERVAL == 0) {
            regenerate(player);
        }
        if (player.tickCount % CLEANSE_INTERVAL == 0) {
            cleanse(player);
        }
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.getPersistentData().getBoolean("narutomod:orb_launch_kill")) {
            float mastery = dead.getPersistentData().getFloat("narutomod:orb_launch_mastery");
            int mult = 2 + (int)(mastery * 3.0f); // 2..5
            event.setDroppedExperience(event.getDroppedExperience() * mult);
            dead.getPersistentData().remove("narutomod:orb_launch_kill");
            dead.getPersistentData().remove("narutomod:orb_launch_mastery");
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !ninjaData.isGudodamaForm()) {
            return;
        }
        float speed = event.getNewSpeed();
        if (speed < 1.0f) {
            speed = 8.0f;
        }
        event.setNewSpeed(speed * 10.0f);
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        INinjaData ninjaData = event.getEntity().getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData != null && ninjaData.isGudodamaForm()) {
            // The shaped hand counts as the right tool, so blocks drop normally.
            event.setCanHarvest(true);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || event.getPlayer().level().isClientSide) {
            return;
        }
        Player player = event.getPlayer();
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !GudodamaFormAbility.isActive(ninjaData)) {
            return;
        }
        float cost = 1.0f;
        if (ninjaData.getChakra() >= cost) {
            ninjaData.useChakra(cost, 0);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!SixPathsModeAbility.isActive(player)) {
            return;
        }
        int orbs = SixPathsModeAbility.orbs(player);
        if (orbs <= 0) {
            return;
        }
        // Totem-like protection: cancel death, restore HP, clear negatives, small chakra, reduce orbs by 1.
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(nd -> {
            nd.addChakra(20.0f);
            SixPathsModeAbility.setOrbs(player, orbs - 1);
            player.getPersistentData().putLong("narutomod:orb_totem_until", player.level().getGameTime() + 20 * 60);
        });
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0d, player.getZ(),
                    30, 0.5d, 0.8d, 0.5d, 0.1d);
        }
    }

    /** Closes the stance through its own cleanup, or clears a mark that has no stance left behind it. */
    private static void end(Player player, INinjaData ninjaData) {
        Ability mode = NarutoRegistries.ABILITIES.getValue(SixPathsModeAbility.ID);
        if (mode != null && ninjaData.getToggleAbilityData().getAbilitiesHashSet()
                .contains(SixPathsModeAbility.ID)) {
            ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, mode);
        } else {
            SixPathsModeAbility.clear(player, ninjaData);
        }
    }

    /** Puts the body back: the wings if this mod lent them, and the speed of an ordinary ninja. */
    private static void release(Player player, CompoundTag data) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        releaseFlight(player, data);
    }

    /** Hands the caster their wings, remembering what they had before so the stance can give it back. */
    private static void grantFlight(Player player, CompoundTag data) {
        if (!data.contains(FLIGHT_TAG)) {
            CompoundTag saved = new CompoundTag();
            saved.putBoolean(MAYFLY_TAG, player.getAbilities().mayfly);
            saved.putBoolean(FLYING_TAG, player.getAbilities().flying);
            data.put(FLIGHT_TAG, saved);
        }
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    /** Puts the flight state back exactly as it was before the stance. Safe when nothing was lent. */
    private static void releaseFlight(Player player, CompoundTag data) {
        if (!data.contains(FLIGHT_TAG)) {
            return;
        }
        CompoundTag saved = data.getCompound(FLIGHT_TAG);
        data.remove(FLIGHT_TAG);
        player.getAbilities().mayfly = saved.getBoolean(MAYFLY_TAG);
        player.getAbilities().flying = saved.getBoolean(FLYING_TAG);
        player.onUpdateAbilities();
    }

    /** Re-applied every tick so a modifier lost to a reload or a respawn comes back with the mode. */
    private static void applySpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
                    "narutomod:six_paths_mode", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /**
     * A sage's body closes on its own, a few points on a short beat. Healing in one lump each second
     * is what makes a health bar jump; spread over quarter-second beats it creeps up instead, which
     * is how the mode is meant to read.
     */
    private static void regenerate(Player player) {
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(REGEN_PER_BEAT);
        }
    }

    /**
     * The slow, the poison, the withering and the fire are all just damage that has not finished
     * arriving, so the mode refuses to let them finish; a chill is the same idea in the mod's own
     * elemental state, and is warmed away rather than left to slow the caster. Darkness and
     * blindness are cleared here as well, which covers anything that was already on the body when
     * the mode opened. Once a second is enough for any of it.
     */
    private static void cleanse(Player player) {
        player.removeEffect(MobEffects.POISON);
        player.removeEffect(MobEffects.WITHER);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.DARKNESS);
        player.clearFire();
        if (ElementalStates.isChilled(player)) {
            ElementalStates.warm(player);
        }
    }

    /**
     * The mode turns a technique aside. A weapon in a hand still lands honestly - the sage is hard to
     * kill with chakra, not invulnerable - so only damage that comes from something other than the
     * striking body is halved.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null) {
            return;
        }
        if (SixPathsModeAbility.isActive(player)) {
            if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LAVA)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                    || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.DROWN)) {
                event.setCanceled(true);
                return;
            }
        }
        float amount = event.getAmount();
        if (SixPathsModeAbility.isActive(player) && isTechniqueDamage(event.getSource())) {
            amount *= TECHNIQUE_DAMAGE_FACTOR;
        }
        if (GudodamaFormAbility.isActive(ninjaData)) {
            amount *= FORM_DAMAGE_FACTOR;
            // Consume chakra on hit.
            float cost = 2.0f;
            if (ninjaData.getChakra() >= cost) {
                ninjaData.useChakra(cost, 0);
            }
            if (player.level() instanceof ServerLevel level) {
                LivingEntity target = (LivingEntity) event.getEntity();
                level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    /**
     * @return whether the blow came from a technique rather than a body. A bare hand, a blade or a
     *         mob's claws all resolve as a direct entity that is also the attacker; everything else -
     *         magic with no body behind it, a thrown shot, a blast - is a technique.
     */
    private static boolean isTechniqueDamage(DamageSource source) {
        if (source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity && source.getEntity() == direct) {
            return false;
        }
        return source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.SONIC_BOOM)
                || source.is(DamageTypes.DRAGON_BREATH)
                || direct != null;
    }

    /**
     * The third eye does not see the dark, so the dark is not allowed to settle on it. Refusing the
     * effect before it is applied is better than clearing it afterwards: a warden's shriek would
     * otherwise still play and flash the screen for the split second it took to remove.
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!SixPathsModeAbility.isActive(player)) {
            return;
        }
        if (event.getEffectInstance().getEffect() == MobEffects.BLINDNESS
                || event.getEffectInstance().getEffect() == MobEffects.DARKNESS) {
            event.setResult(Event.Result.DENY);
        }
    }
}
