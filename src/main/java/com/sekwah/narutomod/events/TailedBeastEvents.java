package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.SealBreakAbility;
import com.sekwah.narutomod.abilities.jutsus.SealVesselAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * The consequences of the vessel: the ritual that has to be stood still for, the seal wearing down,
 * and the two forms that keep working after the cast that opened them.
 *
 * <p>The techniques of this branch only write what they are doing - a mark, a cost, a few effects -
 * because none of them can see a body walking away from a ritual, a clock running out or a blow that
 * has not landed yet. Those are consequences of a mark, and they live here: the ritual is broken by
 * movement, the vessel is worn down by time and by the forms that borrow it, the frenzy sweeps and
 * stops sparing allies for as long as it lasts, and the settled cloak strikes with its own arms.</p>
 *
 * <p>The stances are also cleaned up here. A stance that ends properly takes its marks and modifiers
 * off in {@code handleAbilityEnded}, but a death, a relog or a mark that ran out while the toggle set
 * was being rebuilt can leave a stale one behind, so every player tick walks the two marks once and
 * retires the ones whose stance is no longer held.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TailedBeastEvents {

    /**
     * One point of vessel wears off every three seconds. A hundred points over the six thousand ticks
     * the seal is written for is the same span, so the clock and the integrity run out together.
     */
    private static final int SEAL_DECAY_INTERVAL = 60;

    /** A settled cloak costs the vessel a point every five seconds on top of the wear of time. */
    private static final int CLOAK_DRAIN_INTERVAL = 100;

    /** How often the frenzy's red chakra is drawn over the caster. */
    private static final int FRENZY_PARTICLE_INTERVAL = 4;

    /** A beast has two arms, and they strike two bodies at most. */
    private static final int ARMS = 2;

    private TailedBeastEvents() {
    }

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

        keepRitual(player, ninjaData, data, now);
        wearVessel(player, data, now);
        keepSealBreak(player, data, now);
        keepPerfectCloak(player, data, now);
        clearLostStances(player, ninjaData, data, now);
    }

    /**
     * The ritual is a standing still. A step off the spot, a crouch or a death breaks it, and the
     * chakra is already spent by then; only when the five seconds are up is the vessel actually
     * written.
     */
    private static void keepRitual(Player player, INinjaData ninjaData, CompoundTag data, long now) {
        if (!data.contains(SealVesselAbility.RITUAL_UNTIL_TAG)) {
            return;
        }
        if (!player.isAlive() || player.isShiftKeyDown() || !SealVesselAbility.holdsRitualSpot(player)) {
            SealVesselAbility.interrupt(player);
            return;
        }
        Ability ability = NarutoRegistries.ABILITIES.getValue(SealVesselAbility.ID);
        if (!(ability instanceof SealVesselAbility vessel)) {
            // The technique is not registered in this instance: there is a mark and nothing to write.
            data.remove(SealVesselAbility.RITUAL_UNTIL_TAG);
            return;
        }
        if (now >= data.getLong(SealVesselAbility.RITUAL_UNTIL_TAG)) {
            vessel.complete(player, ninjaData);
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            SealVesselAbility.drawRitual(level, player);
        }
    }

    /** The vessel wears down on its own and is dropped the moment it is out of time or out of itself. */
    private static void wearVessel(Player player, CompoundTag data, long now) {
        if (!data.contains(SealVesselAbility.UNTIL_TAG)) {
            return;
        }
        if (now >= data.getLong(SealVesselAbility.UNTIL_TAG) || SealVesselAbility.integrity(player) <= 0) {
            SealVesselAbility.close(player);
            player.displayClientMessage(Component.translatable("jutsu.seal_vessel.spent")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (player.tickCount % SEAL_DECAY_INTERVAL == 0) {
            SealVesselAbility.spendIntegrity(player, 1);
        }
    }

    /**
     * The frenzy: a sweep of the beast's chakra every second, and the price paid on the tick the mark
     * lets go. The sweep does not ask who is standing next to the caster - that is the whole danger of
     * forcing the seal open - so it hits allies, pets and the caster's own clones alike.
     */
    private static void keepSealBreak(Player player, CompoundTag data, long now) {
        if (!data.contains(SealBreakAbility.UNTIL_TAG)) {
            return;
        }
        if (data.getLong(SealBreakAbility.UNTIL_TAG) <= now) {
            endSealBreak(player, data);
            return;
        }
        if (player.isAlive() && player.tickCount % SealBreakAbility.SWEEP_INTERVAL == 0) {
            sweep(player);
        }
        if (player.tickCount % FRENZY_PARTICLE_INTERVAL == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.7d, player.getZ(),
                    3, 0.4d, 0.5d, 0.4d, 0.01d);
        }
    }

    private static void sweep(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SealBreakAbility.SWEEP_RADIUS),
                target -> target != player && target.isAlive() && !target.isSpectator()
                        && !(target instanceof Player other && other.isCreative()));
        for (LivingEntity target : caught) {
            target.hurt(player.damageSources().magic(), SealBreakAbility.SWEEP_DAMAGE);
        }
        if (!caught.isEmpty()) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                    player.getY() + 1.0d, player.getZ(), 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }

    /** The body is handed back the way it was found: six points of blood and a fifth of the vessel. */
    private static void endSealBreak(Player player, CompoundTag data) {
        data.remove(SealBreakAbility.UNTIL_TAG);
        SealVesselAbility.spendIntegrity(player, SealBreakAbility.INTEGRITY_COST);
        player.displayClientMessage(Component.translatable("jutsu.seal_break.end")
                .withStyle(ChatFormatting.RED), true);
        player.hurt(player.damageSources().magic(), SealBreakAbility.BACKLASH);
    }

    /**
     * The settled cloak: one point of vessel every five seconds, and two arms of chakra that hit the
     * closest bodies that are not on the caster's side. The arms choose their own targets, which is
     * what makes the form worth holding.
     */
    private static void keepPerfectCloak(Player player, CompoundTag data, long now) {
        if (data.getLong(PerfectCloakAbility.UNTIL_TAG) <= now) {
            return;
        }
        if (player.tickCount % CLOAK_DRAIN_INTERVAL == 0) {
            SealVesselAbility.spendIntegrity(player, 1);
        }
        if (player.isAlive() && player.tickCount % PerfectCloakAbility.ARM_INTERVAL == 0) {
            strikeWithArms(player);
        }
    }

    private static void strikeWithArms(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(PerfectCloakAbility.ARM_RADIUS),
                target -> canStrike(player, target));
        near.sort(Comparator.comparingDouble(player::distanceToSqr));
        int strikes = Math.min(ARMS, near.size());
        for (int i = 0; i < strikes; i++) {
            LivingEntity target = near.get(i);
            target.hurt(player.damageSources().magic(), PerfectCloakAbility.ARM_DAMAGE);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            level.sendParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    10, 0.3d, 0.4d, 0.3d, 0.02d);
        }
    }

    /** Anything but the caster, their allies and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !Friends.isOwnSide(caster, target);
    }

    /**
     * The gates of the branch are the vessel itself, and the abilities ask before every upkeep tick.
     * All that is left for this pass is a mark whose stance is no longer held - a death, a relog - so
     * the cloak is not left hanging on a body that is no longer wearing it.
     */
    private static void clearLostStances(Player player, INinjaData ninjaData, CompoundTag data, long now) {
        if (data.contains(DividedCloakAbility.UNTIL_TAG)
                && data.getLong(DividedCloakAbility.UNTIL_TAG) <= now
                && !ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(DividedCloakAbility.ID)) {
            DividedCloakAbility.clear(player);
        }
        if (data.contains(PerfectCloakAbility.UNTIL_TAG)
                && data.getLong(PerfectCloakAbility.UNTIL_TAG) <= now
                && !ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(PerfectCloakAbility.ID)) {
            PerfectCloakAbility.clear(player);
        }
    }

    /**
     * The frenzy does not know sides. The friends list refuses technique damage between allies as a
     * last line of defence, so while the seal is cracked it is told to stand down for this caster:
     * everything the caster throws lands on whoever it reaches, which is the price of the eight
     * seconds rather than a bug in them.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player caster) || attacker == event.getEntity()) {
            return;
        }
        if (!SealBreakAbility.isActive(caster) || !event.isCanceled()) {
            return;
        }
        event.setCanceled(false);
    }
}
