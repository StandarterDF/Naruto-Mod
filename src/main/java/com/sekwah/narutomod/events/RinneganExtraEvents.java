package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.AnimalPathAbility;
import com.sekwah.narutomod.abilities.jutsus.NarakaPathAbility;
import com.sekwah.narutomod.abilities.jutsus.OuterPathArmorAbility;
import com.sekwah.narutomod.abilities.jutsus.OuterPathChainsAbility;
import com.sekwah.narutomod.abilities.jutsus.RinboHengokuAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganPrisonAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.UUID;
import java.util.List;

/**
 * The consequences of the Rinnegan's quieter paths: a court that has to judge, a pack that has to
 * be kept on a leash, an echo that has to land its second blow, a shell that has to be taken off,
 * and chains that have to let go.
 *
 * <p>None of these is cast here. Every technique writes a mark - a zone in a static record, a tag
 * and an end time on a body - and this class honours the mark on the server for as long as it is in
 * the future. That split keeps the abilities readable and puts every per-tick check in one place,
 * which is also the only place that has to be careful about the client: nothing in this class runs
 * on it.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class RinneganExtraEvents {

    /** How often the court draws its ring, so it is visible without flooding the client. */
    private static final long RING_INTERVAL = 10L;

    /** How long a chain's slowdown and blindness are granted for on each renewal. */
    private static final int CHAIN_EFFECT_DURATION = 10;

    /** How long a prisoner's killer is punished for: three minutes of weakness and slowness. */
    private static final int PRISON_KILLER_DEBUFF_DURATION = 3600;

    private RinneganExtraEvents() {
    }

    /**
     * The courts standing in the world. A court is a place, not an effect, so it is ticked from the
     * server rather than from any one body: the caster may walk away, and the court keeps judging
     * until its time is up.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();

        Iterator<NarakaPathAbility.NarakaZone> zones = NarakaPathAbility.zones().iterator();
        while (zones.hasNext()) {
            NarakaPathAbility.NarakaZone zone = zones.next();
            // A zone from a previous server run has no world left to judge; so does one whose
            // caster has left the level the court was raised in.
            if (zone.level().getServer() != server || now >= zone.expiresAt()) {
                zones.remove();
                continue;
            }
            Player caster = server.getPlayerList().getPlayer(zone.caster());
            if (caster == null || caster.level() != zone.level()) {
                zones.remove();
                continue;
            }
            double radius = NarakaPathAbility.radiusAt(zone, now);
            if (now % RING_INTERVAL == 0L) {
                NarakaPathAbility.drawRing(zone.level(), zone.centre(), radius);
            }
            if (now % NarakaPathAbility.JUDGEMENT_INTERVAL == 0L) {
                judge(zone.level(), caster, zone.centre(), radius);
            }
        }
    }

    /** One judgement: allies are mended and cleansed, everyone else is weakened. */
    private static void judge(ServerLevel level, Player caster, Vec3 centre, double radius) {
        AABB court = new AABB(centre, centre).inflate(radius);
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, court,
                entity -> entity.isAlive() && !entity.isSpectator()
                        && entity.distanceToSqr(centre) <= radius * radius);

        for (LivingEntity target : inside) {
            if (Friends.isOwnSide(caster, target)) {
                target.heal(NarakaPathAbility.HEAL_AMOUNT);
                target.removeEffect(MobEffects.POISON);
                target.removeEffect(MobEffects.WITHER);
                target.removeEffect(MobEffects.BLINDNESS);
                target.removeEffect(MobEffects.CONFUSION);
            } else if (!(target instanceof Player other) || (!other.isCreative() && !other.isSpectator())) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                        NarakaPathAbility.WEAKNESS_DURATION, 0, false, true));
            }
        }
    }

    /**
     * Every mark that rides on a living body is honoured here: the pack's leash and lifetime, the
     * echo's lifetime, the chains' hold and the shell's expiry.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        // Cheap early-out: ordinary mobs carry none of these marks, so there is nothing to upkeep.
        if (!data.getBoolean(AnimalPathAbility.SUMMON_TAG)
                && !data.getBoolean(RinboHengokuAbility.ECHO_TAG)
                && !data.contains(OuterPathChainsAbility.UNTIL_TAG)
                && !data.contains(OuterPathArmorAbility.UNTIL_TAG)
                && !data.getBoolean(RinneganPrisonAbility.PRISON_TAG)) {
            return;
        }
        long now = entity.level().getGameTime();

        upkeepWolf(entity, data, now);
        upkeepEcho(entity, data, now);
        upkeepChains(entity, data, now);
        upkeepShell(entity, data, now);
        // Prison upkeep only matters for summoned prisoners; guard it explicitly.
        if (data.getBoolean(RinneganPrisonAbility.PRISON_TAG)) {
            upkeepPrison(entity, data, now);
        }
    }

    /** A summoned wolf is pulled back to its owner when it strays, and taken home when time is up. */
    private static void upkeepWolf(LivingEntity entity, CompoundTag data, long now) {
        if (!data.getBoolean(AnimalPathAbility.SUMMON_TAG)) {
            return;
        }
        if (now >= data.getLong(AnimalPathAbility.UNTIL_TAG)) {
            entity.discard();
            return;
        }
        if (!(entity instanceof Wolf wolf) || wolf.getOwnerUUID() == null) {
            return;
        }
        Player owner = entity.level().getPlayerByUUID(wolf.getOwnerUUID());
        if (owner == null || owner.level() != entity.level()) {
            return;
        }
        if (wolf.distanceToSqr(owner) <= AnimalPathAbility.LEASH * AnimalPathAbility.LEASH) {
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, wolf.getX(),
                    wolf.getY() + wolf.getBbHeight() * 0.5d, wolf.getZ(),
                    8, 0.25d, 0.25d, 0.25d, 0.01d);
        }
        wolf.teleportTo(owner.getX(), owner.getY(), owner.getZ());
    }

    /** An echo lasts exactly as long as it was promised to. */
    private static void upkeepEcho(LivingEntity entity, CompoundTag data, long now) {
        if (!data.getBoolean(RinboHengokuAbility.ECHO_TAG)) {
            return;
        }
        if (now >= data.getLong(RinboHengokuAbility.UNTIL_TAG)) {
            entity.discard();
        }
    }

    /**
     * Chains refresh their own slowdown and blindness for as long as the mark says they are held.
     * The mark is short, so letting go of the channel lets go of the bodies within a few ticks.
     */
    private static void upkeepChains(LivingEntity entity, CompoundTag data, long now) {
        if (!data.contains(OuterPathChainsAbility.UNTIL_TAG)) {
            return;
        }
        if (now >= data.getLong(OuterPathChainsAbility.UNTIL_TAG)) {
            data.remove(OuterPathChainsAbility.UNTIL_TAG);
            return;
        }
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHAIN_EFFECT_DURATION,
                OuterPathChainsAbility.SLOW_AMPLIFIER, false, true));
        // Blindness is part of the pin, not only of the throw: it lasts exactly as long as the hold.
        entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CHAIN_EFFECT_DURATION, 0, false, true));
    }

    /**
     * The shell's modifiers are taken off when its mark runs out. Normally {@code handleAbilityEnded}
     * does this on the same tick the stance ends; this is the belt to that pair of braces, for a
     * body that lost its capability or left before the stance could be closed properly.
     */
    /** A prison summon is taken back when its time is up; while alive it serves its jailer. Released prisoners are under genjutsu and never target each other. */
    private static void upkeepPrison(LivingEntity entity, CompoundTag data, long now) {
        if (!data.getBoolean(RinneganPrisonAbility.PRISON_TAG)) {
            return;
        }
        if (now >= data.getLong(RinneganPrisonAbility.UNTIL_TAG)) {
            entity.discard();
            return;
        }
        if (!(entity instanceof Mob mob)) return;
        UUID ownerId = data.hasUUID(RinneganPrisonAbility.OWNER_TAG) ? data.getUUID(RinneganPrisonAbility.OWNER_TAG) : null;
        if (ownerId == null) return;
        Player owner = entity.level().getPlayerByUUID(ownerId);
        if (owner == null || !owner.isAlive() || owner.level() != entity.level()) return;

        // Never hold the caster (or any ally/friend of his) as a target.
        LivingEntity target = mob.getTarget();
        if (target != null && (!target.isAlive() || target == owner || Friends.isOwnSide(owner, target))) {
            mob.setTarget(null);
        }

        // Once every twenty ticks pick the caster's nearest enemy and set it as the target.
        if (now % 20L == 0L) {
            List<LivingEntity> enemies = entity.level().getEntitiesOfClass(LivingEntity.class,
                    mob.getBoundingBox().inflate(16.0d),
                    e -> e.isAlive() && e != owner && e != mob
                            && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                            && !Friends.isOwnSide(owner, e)
                            && !e.getPersistentData().getBoolean(RinneganPrisonAbility.PRISON_TAG));
            LivingEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : enemies) {
                double d = e.distanceToSqr(mob);
                if (d < best) { best = d; nearest = e; }
            }
            if (nearest != null) {
                mob.setTarget(nearest);
            }
        }

        // Follow the jailer when he walks away.
        if (mob.distanceTo(owner) > 10.0d) {
            mob.getNavigation().moveTo(owner, 1.0d);
        }
    }

    private static void upkeepShell(LivingEntity entity, CompoundTag data, long now) {
        if (!(entity instanceof Player player) || !data.contains(OuterPathArmorAbility.UNTIL_TAG)) {
            return;
        }
        if (now >= data.getLong(OuterPathArmorAbility.UNTIL_TAG)) {
            OuterPathArmorAbility.removeModifiers(player);
        }
    }

    /** The shell turns a share of every blow aside, and the echo repeats every melee blow. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0.0f || event.isCanceled()) {
            return;
        }
        // A prison summon never harms its jailer.
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Mob prisonMob) {
            CompoundTag prisonData = prisonMob.getPersistentData();
            if (prisonData.getBoolean(RinneganPrisonAbility.PRISON_TAG)
                    && prisonData.hasUUID(RinneganPrisonAbility.OWNER_TAG)
                    && prisonData.getUUID(RinneganPrisonAbility.OWNER_TAG).equals(victim.getUUID())) {
                event.setCanceled(true);
                return;
            }
        }
        long now = victim.level().getGameTime();
        if (victim.getPersistentData().getLong(OuterPathArmorAbility.UNTIL_TAG) > now) {
            event.setAmount(event.getAmount() * OuterPathArmorAbility.DAMAGE_MULTIPLIER);
        }
        repeatWithEcho(event, victim, now);
    }

    /**
     * Hands every melee blow to the caster's echo as well. The echo's own blow is indirect magic, so
     * it can never come back through this hook and multiply itself.
     */
    private static void repeatWithEcho(LivingHurtEvent event, LivingEntity victim, long now) {
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof Player attacker) || event.getSource().getEntity() != attacker) {
            return;
        }
        if (victim.getPersistentData().getBoolean(RinboHengokuAbility.ECHO_TAG)) {
            return;
        }
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        ShadowCloneEntity echo = echoOf(level, attacker, now);
        if (echo == null) {
            return;
        }
        float amount = event.getAmount() * RinboHengokuAbility.REPEAT_SHARE;
        attacker.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data ->
                data.scheduleDelayedTickEvent(delayed ->
                        strikeFromEcho(level, echo, delayed, victim, amount),
                        RinboHengokuAbility.REPEAT_DELAY));
    }

    /** The caster's living echo, if one is out and still in time. */
    private static ShadowCloneEntity echoOf(ServerLevel level, Player caster, long now) {
        List<ShadowCloneEntity> echoes = level.getEntitiesOfClass(ShadowCloneEntity.class,
                caster.getBoundingBox().inflate(RinboHengokuAbility.ECHO_RANGE),
                clone -> clone.isAlive()
                        && clone.getPersistentData().getBoolean(RinboHengokuAbility.ECHO_TAG)
                        && clone.getPersistentData().getLong(RinboHengokuAbility.UNTIL_TAG) > now
                        && clone.getOwnerUUID().map(id -> id.equals(caster.getUUID())).orElse(false));
        return echoes.isEmpty() ? null : echoes.get(0);
    }

    /** The echo's half of the blow, a few ticks after the caster's. */
    private static void strikeFromEcho(ServerLevel level, ShadowCloneEntity echo, Player caster,
                                       LivingEntity victim, float amount) {
        if (!echo.isAlive() || !victim.isAlive() || victim.level() != level || caster.level() != level) {
            return;
        }
        if (level.getGameTime() >= echo.getPersistentData().getLong(RinboHengokuAbility.UNTIL_TAG)) {
            return;
        }
        Player owner = echo.getOwnerUUID().map(level::getPlayerByUUID).orElse(null);
        Entity cause = owner != null ? owner : echo;
        victim.hurt(level.damageSources().indirectMagic(echo, cause), amount);
        level.sendParticles(ParticleTypes.SOUL, victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.6d, victim.getZ(),
                6, 0.25d, 0.35d, 0.25d, 0.01d);
    }

    /** A body inside the shell does not get pushed around. */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().getLong(OuterPathArmorAbility.UNTIL_TAG)
                > entity.level().getGameTime()) {
            event.setStrength(0.0f);
            event.setRatioX(0.0d);
            event.setRatioZ(0.0d);
        }
    }

    /** A prison summon that dies feeds its owner absorption, and punishes its killer. */
    @SubscribeEvent
    public static void onPrisonDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(RinneganPrisonAbility.PRISON_TAG)) return;
        UUID ownerId = data.hasUUID(RinneganPrisonAbility.OWNER_TAG) ? data.getUUID(RinneganPrisonAbility.OWNER_TAG) : null;
        if (ownerId == null) return;
        Player owner = entity.level().getPlayerByUUID(ownerId);
        if (owner == null) return;
        float maxHp = data.getFloat(RinneganPrisonAbility.MAXHP_TAG);
        float hearts = maxHp / 10.0f;
        owner.setAbsorptionAmount(owner.getAbsorptionAmount() + hearts);
        CompoundTag pdata = owner.getPersistentData();
        long until = entity.level().getGameTime() + 3600; // 3 minutes
        pdata.putLong(RinneganPrisonAbility.ABSORPTION_UNTIL_TAG, until);
        pdata.putFloat(RinneganPrisonAbility.ABSORPTION_AMOUNT_TAG,
                pdata.getFloat(RinneganPrisonAbility.ABSORPTION_AMOUNT_TAG) + hearts);
        Entity killer = event.getSource().getEntity();
        if (killer != null && killer != owner && killer instanceof LivingEntity livingKiller) {
            livingKiller.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,
                    PRISON_KILLER_DEBUFF_DURATION, 1, false, true));
            livingKiller.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    PRISON_KILLER_DEBUFF_DURATION, 1, false, true));
        }
    }

    /**
     * Per-player bookkeeping for the Rinnegan's prison: passive chakra while prisoners are held,
     * and the absorption timer running out. Runs on the server only.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        CompoundTag pdata = player.getPersistentData();
        // Passive chakra while prisoners are still held in the cell (captured but not yet released):
        // a flat trickle for every body recorded in the list, whatever the body was worth.
        ListTag prisoners = pdata.getList(RinneganPrisonAbility.PRISON_LIST_TAG, 8);
        if (!prisoners.isEmpty() && player.tickCount % 20 == 0) {
            final float gain = prisoners.size() * RinneganPrisonAbility.CHAKRA_PER_PRISONER;
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(nd ->
                    nd.addChakra(gain));
        }
        // Passive chakra while released prisoners are still alive: each living summoned body feeds
        // its jailer. Counted by UUID from the released list, so it does not depend on a radius.
        if (player.tickCount % 20 == 0 && player.level() instanceof ServerLevel level) {
            ListTag released = pdata.getList(RinneganPrisonAbility.RELEASED_LIST_TAG, 8);
            int alive = 0;
            for (int i = 0; i < released.size(); i++) {
                try {
                    Entity e = level.getEntity(java.util.UUID.fromString(released.getString(i)));
                    if (e instanceof Mob mob && mob.isAlive()
                            && mob.getPersistentData().getBoolean(RinneganPrisonAbility.PRISON_TAG)) {
                        alive++;
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (alive > 0) {
                final float gain = alive * RinneganPrisonAbility.CHAKRA_PER_PRISONER;
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(nd -> nd.addChakra(gain));
            }
        }
        // Absorption timer.
        long until = pdata.getLong(RinneganPrisonAbility.ABSORPTION_UNTIL_TAG);
        if (until > 0 && player.level().getGameTime() >= until) {
            float amount = pdata.getFloat(RinneganPrisonAbility.ABSORPTION_AMOUNT_TAG);
            player.setAbsorptionAmount(Math.max(0.0f, player.getAbsorptionAmount() - amount));
            pdata.remove(RinneganPrisonAbility.ABSORPTION_UNTIL_TAG);
            pdata.remove(RinneganPrisonAbility.ABSORPTION_AMOUNT_TAG);
        }
    }

    /** A summoned wolf was never a real wolf, so nothing may fall out of it. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().getBoolean(AnimalPathAbility.SUMMON_TAG)
                || entity.getPersistentData().getBoolean(RinneganPrisonAbility.PRISON_TAG)) {
            event.getDrops().clear();
        }
    }
}
