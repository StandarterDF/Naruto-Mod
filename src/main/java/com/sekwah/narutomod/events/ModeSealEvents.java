package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealOneAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealTwoAbility;
import com.sekwah.narutomod.abilities.jutsus.LightningModeAbility;
import com.sekwah.narutomod.abilities.jutsus.OrochimaruBodyAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The consequences of the mode marks: the price the seals take, the charge the lightning mode carries,
 * and the upkeep of the stances that write a mark and walk away.
 *
 * <p>The mode techniques themselves only write what they are doing - the mark, the marker, the
 * modifiers - because a technique cannot see somebody else's healing or a blow that has not landed
 * yet. Those are consequences of a mark, and they live here: a body under a curse seal mends at half
 * or a quarter rate, a charged body answers a touch with a shock, and standing in water grounds the
 * charge outright.</p>
 *
 * <p>The marks also have to be cleaned up. A stance that ends properly takes its own marks and
 * modifiers off in {@code handleAbilityEnded}, but a death, a relog or a mark that simply ran out
 * while the toggle set was being rebuilt can leave a stale one behind, so every player tick walks the
 * marks once and retires the ones that are past. The boons of the stances are given short durations
 * and refreshed by the techniques themselves, so this pass never needs to strip an effect it did not
 * hand out; it only removes the marks and the attribute modifiers it owns.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class ModeSealEvents {

    /** Damage of the charge's answer, how often it may fire, and how close the attacker must be. */
    private static final float ZAP_DAMAGE = 4.0f;
    private static final int ZAP_COOLDOWN = 20;
    private static final double ZAP_RANGE = 4.0d;

    /** The mark that keeps the answer from firing on every hit of a flurry. */
    private static final String ZAP_READY_TAG = "narutomod:lightning_mode_zap";

    private ModeSealEvents() {
    }

    /**
     * The curse seal's price is paid where healing happens: a body holding the first stage keeps half
     * of what it is given, and a body holding the second keeps a quarter. Healing that is ground down
     * to nothing is dropped altogether rather than left as a zero-amount event.
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();
        float factor;
        if (data.getLong(CurseSealTwoAbility.UNTIL_TAG) > now) {
            factor = CurseSealTwoAbility.HEALING_FACTOR;
        } else if (data.getLong(CurseSealOneAbility.UNTIL_TAG) > now) {
            factor = CurseSealOneAbility.HEALING_FACTOR;
        } else {
            return;
        }
        float healed = event.getAmount() * factor;
        if (healed <= 0.0f) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(healed);
    }

    /**
     * A charged body is a bad thing to touch. Anybody who lands a melee blow on the caster takes a
     * shock of its own, once a second: the mark is set before the answer lands so two casters of the
     * mode cannot bounce the same charge back and forth forever.
     *
     * <p>A thrown weapon does not count - the answer reaches a body close enough to have touched the
     * caster, not an archer across the field.</p>
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player caster) || caster.level().isClientSide) {
            return;
        }
        if (!LightningModeAbility.isActive(caster)) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        long now = caster.level().getGameTime();
        if (data.getLong(ZAP_READY_TAG) > now) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == caster) {
            return;
        }
        if (event.getSource().getDirectEntity() != attacker) {
            return;
        }
        if (attacker.distanceToSqr(caster) > ZAP_RANGE * ZAP_RANGE) {
            return;
        }

        data.putLong(ZAP_READY_TAG, now + ZAP_COOLDOWN);
        attacker.hurt(caster.damageSources().magic(), ZAP_DAMAGE);
        if (caster.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6d, attacker.getZ(),
                    20, 0.3d, 0.4d, 0.3d, 0.1d);
            level.playSound(null, attacker, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    /** The upkeep of the marks: the slow fall they carry, the wet check, and the stale ones. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        if (player.isAlive()) {
            keepSlowFall(player, data, now);
        }
        dropWetLightning(player, ninjaData);
        retireExpiredMarks(player, ninjaData, data, now);
    }

    /**
     * The second curse seal carries a slow fall, and it carries it through a mark rather than an
     * effect so that it stops the moment the seal does. It only lives a second at a time and is
     * renewed, which is the shortest hold that keeps the caster from falling between renewals.
     */
    private static void keepSlowFall(Player player, CompoundTag data, long now) {
        if (data.getLong(CurseSealTwoAbility.SLOW_FALL_TAG) <= now) {
            return;
        }
        if (player.tickCount % CurseSealTwoAbility.SLOW_FALL_RENEW_TICKS != 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                CurseSealTwoAbility.SLOW_FALL_DURATION, 0, true, false));
    }

    /**
     * Water grounds the lightning mode. Rain, a river and the soaked state a water technique leaves
     * behind all count, and the mode is switched off the same way the caster would have switched it
     * off, so its mark, its speed and its toggle all come away together.
     */
    private static void dropWetLightning(Player player, INinjaData ninjaData) {
        if (!LightningModeAbility.isActive(player)) {
            return;
        }
        if (!player.isInWaterOrRain() && !player.isInWater() && !ElementalStates.isWet(player)) {
            return;
        }
        Ability ability = NarutoRegistries.ABILITIES.getValue(LightningModeAbility.ID);
        if (ability == null) {
            // The technique is not registered in this instance: there is nothing to switch off, only
            // a mark to let go of.
            LightningModeAbility.removeModifiers(player);
            player.getPersistentData().remove(LightningModeAbility.UNTIL_TAG);
            return;
        }
        player.displayClientMessage(Component.translatable("jutsu.lightning_mode.wet")
                .withStyle(ChatFormatting.AQUA), true);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    25, 0.5d, 0.7d, 0.5d, 0.05d);
        }
        ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, ability);
    }

    /**
     * Walks the mode marks and retires the ones whose stance is no longer in the toggle set. The
     * attribute modifiers are removed by the ids the techniques own, so nothing else is disturbed;
     * the boons themselves are never stripped here because they are only ever handed out for a
     * second at a time and have run out long before a mark can go stale.
     */
    private static void retireExpiredMarks(Player player, INinjaData ninjaData, CompoundTag data, long now) {
        retire(data, ninjaData, CurseSealOneAbility.UNTIL_TAG, CurseSealOneAbility.ID, now);
        retire(data, ninjaData, CurseSealTwoAbility.UNTIL_TAG, CurseSealTwoAbility.ID, now);
        // The slow fall is a separate mark and is dropped with the seal whether or not the seal was
        // still in the toggle set when its own mark ran out.
        if (data.contains(CurseSealTwoAbility.SLOW_FALL_TAG)
                && data.getLong(CurseSealTwoAbility.SLOW_FALL_TAG) <= now) {
            data.remove(CurseSealTwoAbility.SLOW_FALL_TAG);
        }
        if (retire(data, ninjaData, ChakraCloakAbility.UNTIL_TAG, ChakraCloakAbility.ID, now)) {
            ChakraCloakAbility.removeModifiers(player);
        }
        if (retire(data, ninjaData, TailedCloakAbility.UNTIL_TAG, TailedCloakAbility.ID, now)) {
            TailedCloakAbility.removeModifiers(player);
        }
        if (retire(data, ninjaData, LightningModeAbility.UNTIL_TAG, LightningModeAbility.ID, now)) {
            LightningModeAbility.removeModifiers(player);
            data.remove(ZAP_READY_TAG);
        }
        // The shell has no modifier of its own; the mark is the whole of it.
        if (data.contains(OrochimaruBodyAbility.SHELL_UNTIL_TAG)
                && data.getLong(OrochimaruBodyAbility.SHELL_UNTIL_TAG) <= now) {
            data.remove(OrochimaruBodyAbility.SHELL_UNTIL_TAG);
        }
    }

    /**
     * @return whether the mark was stale and has just been removed, which is the caller's signal to
     *         take the stance's modifiers with it.
     */
    private static boolean retire(CompoundTag data, INinjaData ninjaData, String tag, ResourceLocation abilityId,
                                  long now) {
        if (!data.contains(tag) || data.getLong(tag) > now) {
            return false;
        }
        if (ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(abilityId)) {
            // The stance is still held; its own upkeep refreshed the mark a moment ago and this read
            // must have been taken between the two. Leave it alone.
            return false;
        }
        data.remove(tag);
        return true;
    }
}
