package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.CloneSacrificeAbility;
import com.sekwah.narutomod.abilities.jutsus.CloneSparringAbility;
import com.sekwah.narutomod.abilities.jutsus.ExplosionSilencerAbility;
import com.sekwah.narutomod.abilities.jutsus.IronGuardianAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The consequences of the summoning, training and sealing marks.
 *
 * <p>Everything here is a reaction, never a cast: the techniques write a mark or put a body down,
 * and this class is where the world takes those marks seriously. A sparring copy pulls its blows, a
 * lethal one takes a copy instead of the caster, a summoned golem runs out of time, and a silencing
 * seal eats the blocks an explosion would have broken.</p>
 *
 * <p>All of it is server side. None of these decisions can be trusted to a client, and none of them
 * have anything to draw.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SummonMedicineEvents {

    /** What a sparring copy's blow is worth. Training is a mark on a real hit, not a fake one. */
    private static final float SPARRING_DAMAGE = 1.0f;

    /** How often one sparring copy may earn mastery from landing a blow. */
    private static final int SPARRING_CREDIT_INTERVAL = 20;

    /** Game time the copy last earned its half of the spar at, so the credit is not paid per hit. */
    private static final String SPARRING_CREDITED = "narutomod:sparring_credited";

    /** How far a copy may be from its caster and still be close enough to die in their place. */
    private static final double SACRIFICE_RANGE = 16.0d;

    /** What the caster is left on after a copy has taken the blow. */
    private static final float SACRIFICE_HEALTH = 4.0f;

    /**
     * The mercy window granted by a substitution. A cancelled hit does not set the victim's own
     * invulnerability, so without this the same tick's second blow would spend the next copy.
     */
    private static final int SACRIFICE_INVULNERABILITY = 20;

    /** What an explosion still does to a body standing inside a silencing seal. */
    private static final float SILENCED_DAMAGE_MULTIPLIER = 0.5f;

    /**
     * The live silencing seals. A seal is a thing left in the world, so it cannot hang off the
     * caster; the record is the whole state and it expires on its own.
     */
    private static final List<SilencerZone> ZONES = new ArrayList<>();

    private SummonMedicineEvents() {
    }

    /** One drawn seal: where it was anchored, and the game time it stops working. */
    private record SilencerZone(ResourceKey<Level> dimension, Vec3 centre, long until) {
    }

    /**
     * Registers a silencing seal drawn at {@code centre}. Called by
     * {@link ExplosionSilencerAbility} on cast; anything else that wants to leave one behind can use
     * it too.
     */
    public static void addSilencerZone(ServerLevel level, Vec3 centre, int durationTicks) {
        ZONES.add(new SilencerZone(level.dimension(), centre, level.getGameTime() + durationTicks));
    }

    /**
     * The normal damage pass: a sparring copy pulls its blow, and an explosion inside a live seal
     * only lands half of its force.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        dampenSparringBlow(event);
        halveSilencedExplosion(event);
    }

    /**
     * A copy that is sparring is not fighting for its caster: every blow it lands is worth a single
     * point, which is what makes training with a clone a choice rather than a free second fighter.
     * The blow still earns its share of the spar, throttled so a flurry is not a mastery farm.
     */
    private static void dampenSparringBlow(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ShadowCloneEntity clone)) {
            return;
        }
        if (!isSparring(clone)) {
            return;
        }
        event.setAmount(SPARRING_DAMAGE);
        awardSparringCredit(clone);
    }

    /** @return whether the copy is currently held in a spar, see {@link CloneSparringAbility}. */
    private static boolean isSparring(ShadowCloneEntity clone) {
        return clone.getPersistentData().getLong(CloneSparringAbility.SPARRING_UNTIL_KEY)
                > clone.level().getGameTime();
    }

    /** Pays the copy's half of the spar into its caster's mastery, at most once per interval. */
    private static void awardSparringCredit(ShadowCloneEntity clone) {
        Player owner = clone.getOwner();
        if (owner == null) {
            return;
        }
        CompoundTag data = clone.getPersistentData();
        long now = clone.level().getGameTime();
        if (data.getLong(SPARRING_CREDITED) > now) {
            return;
        }
        data.putLong(SPARRING_CREDITED, now + SPARRING_CREDIT_INTERVAL);

        Ability ability = NarutoRegistries.ABILITIES.getValue(CloneSparringAbility.ID);
        if (!(ability instanceof CloneSparringAbility sparring)) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .ifPresent(ownerData -> sparring.credit(owner, ownerData));
    }

    /** Half the force of any explosion whose centre is inside a live seal. */
    private static void halveSilencedExplosion(LivingHurtEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (isSilenced(victim.level(), victim.position())) {
            event.setAmount(event.getAmount() * SILENCED_DAMAGE_MULTIPLIER);
        }
    }

    /**
     * The blow that would have killed is spent on a copy instead.
     *
     * <p>Runs last, so the amount being compared is the one that would really have been applied, and
     * the decision is made against the health that would really have been lost. The copy is taken out
     * of the world outright - a decoy that survived would be a second life rather than a trade - and
     * the mark is cleared so one blow can never spend two copies.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLethalHurt(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player caster) || caster.level().isClientSide) {
            return;
        }
        CompoundTag data = caster.getPersistentData();
        long now = caster.level().getGameTime();
        if (data.getLong(CloneSacrificeAbility.SACRIFICE_UNTIL_KEY) <= now) {
            return;
        }
        if (event.getAmount() < caster.getHealth()) {
            return;
        }
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }

        ShadowCloneEntity copy = nearestCopy(level, caster);
        if (copy == null) {
            return;
        }

        copy.discard();
        data.remove(CloneSacrificeAbility.SACRIFICE_UNTIL_KEY);
        event.setCanceled(true);
        caster.setHealth(SACRIFICE_HEALTH);
        caster.invulnerableTime = SACRIFICE_INVULNERABILITY;

        level.sendParticles(ParticleTypes.CLOUD, copy.getX(),
                copy.getY() + copy.getBbHeight() * 0.5d, copy.getZ(),
                30, 0.5d, 0.7d, 0.5d, 0.02d);
        level.sendParticles(ParticleTypes.CLOUD, caster.getX(),
                caster.getY() + caster.getBbHeight() * 0.5d, caster.getZ(),
                20, 0.4d, 0.6d, 0.4d, 0.02d);
    }

    /** The closest copy of the caster's that could be spent on the blow. */
    private static ShadowCloneEntity nearestCopy(ServerLevel level, Player caster) {
        List<ShadowCloneEntity> copies = level.getEntitiesOfClass(ShadowCloneEntity.class,
                caster.getBoundingBox().inflate(SACRIFICE_RANGE),
                clone -> clone.isAlive() && clone.isOwnedBy(caster));
        return copies.stream()
                .min(java.util.Comparator.comparingDouble(clone -> clone.distanceToSqr(caster)))
                .orElse(null);
    }

    /**
     * A summoned guardian only ever picks a monster. Players, animals, other summons and the caster's
     * own side are not what it was called to hold the line against, and a body that cannot be given
     * a valid target is left with none.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem) || golem.level().isClientSide) {
            return;
        }
        if (!golem.getPersistentData().getBoolean(IronGuardianAbility.SUMMON_KEY)) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (target != null && !(target instanceof Enemy)) {
            event.setNewTarget(null);
        }
    }

    /** A summon goes back to the ground when its time is up, without leaving a body behind. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem) || golem.level().isClientSide) {
            return;
        }
        CompoundTag data = golem.getPersistentData();
        if (!data.getBoolean(IronGuardianAbility.SUMMON_KEY)) {
            return;
        }
        if (golem.level().getGameTime() < data.getLong(IronGuardianAbility.SUMMON_UNTIL_KEY)) {
            return;
        }
        if (golem.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, golem.getX(),
                    golem.getY() + golem.getBbHeight() * 0.5d, golem.getZ(),
                    30, 0.6d, 0.8d, 0.6d, 0.02d);
        }
        golem.discard();
    }

    /**
     * A live seal swallows the ground an explosion would have taken. The entities hit are untouched
     * here: the blast still happens, only the force on a body inside the seal is halved, see
     * {@link #onLivingHurt}.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!isSilenced(event.getLevel(), event.getExplosion().getPosition())) {
            return;
        }
        event.getAffectedBlocks().clear();
        event.getAffectedEntities().clear();
    }

    /** Seals do not travel between saves; a stopped server takes its live arrays with it. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            ZONES.clear();
        }
    }

    /**
     * Draws the edge of every live seal so the caster can see where it works. The array is anchored
     * on the ground, so the ring is sent to the clients at ground height every half second: server
     * particles are broadcast to everyone nearby by vanilla, which is exactly the audience that needs
     * to know they are standing inside a silenced field.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        ZONES.removeIf(zone -> zone.until() <= now);
        if (now % 10 != 0L) {
            return;
        }
        for (SilencerZone zone : ZONES) {
            if (!zone.dimension().equals(level.dimension())) {
                continue;
            }
            drawZoneEdge(level, zone.centre());
        }
    }

    /** A ring of sealing glyphs around the edge, with a dim column at the anchor. */
    private static void drawZoneEdge(ServerLevel level, Vec3 centre) {
        double radius = ExplosionSilencerAbility.ZONE_RADIUS;
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0d * i / points;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.ENCHANT, x, centre.y + 0.15d, z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, x, centre.y + 0.4d, z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, centre.x, centre.y + 0.1d, centre.z,
                3, 0.08d, 0.1d, 0.08d, 0.0d);
    }

    /**
     * @return whether {@code at} lies inside a live seal in that level. Expired seals are dropped on
     *         the way through, so nothing has to sweep them on a timer.
     */
    private static boolean isSilenced(Level level, Vec3 at) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        long now = server.getGameTime();
        ZONES.removeIf(zone -> zone.until() <= now);
        double radiusSqr = ExplosionSilencerAbility.ZONE_RADIUS * ExplosionSilencerAbility.ZONE_RADIUS;
        for (SilencerZone zone : ZONES) {
            if (zone.dimension().equals(server.dimension()) && zone.centre().distanceToSqr(at) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }
}
