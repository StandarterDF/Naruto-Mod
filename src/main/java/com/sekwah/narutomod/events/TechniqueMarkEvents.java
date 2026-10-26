package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.WindStepAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * The timed marks the newer techniques leave on bodies.
 *
 * <p>Several techniques need to say "this body is affected until tick N" rather than hold a
 * reference to it: a severed tenketsu, a suggestion that has to survive a relog, a sleeping mob that
 * has to be woken by the first hit. The marks live in the entity's persistent data - the same place
 * the soft fist and the genjutsu already keep theirs - and the effects they imply are enforced here,
 * on the server, for as long as the mark is in the future.</p>
 *
 * <p>Everything in this class is a consequence of a mark, never a cast: the techniques themselves
 * stay self-contained and only write the mark.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class TechniqueMarkEvents {

    /** A body whose chakra flow was cut by the Byakugan does not close its wounds. */
    private static final String RUPTURE = "narutomod:node_rupture_until";

    /** A mind that was talked around by the Sharingan will not pick the caster back up. */
    private static final String SUGGESTION = "narutomod:suggestion_until";
    private static final String SUGGESTION_SOURCE = "narutomod:suggestion_source";

    /** A body put to sleep by the Nemu palm, and the mark that says when it wakes on its own. */
    private static final String SLEEP = "narutomod:nemuri_until";

    /** A clone that exists to be hit instead of the caster. */
    private static final String DECOY = "narutomod:decoy_clone";
    private static final String DECOY_UNTIL = "narutomod:decoy_until";

    /** A clone that feeds its caster back a little of what it borrowed. */
    private static final String MEDIC = "narutomod:medic_clone";
    private static final String MEDIC_UNTIL = "narutomod:medic_until";
    private static final String MEDIC_HEALED = "narutomod:medic_healed";

    /** An entity frozen by the Jougan's stasis. */
    private static final String STASIS = "narutomod:stasis_until";

    /** A mind held by the moon's illusion. */
    private static final String TSUKUYOMI = "narutomod:tsukuyomi_until";

    /** The gentle fist stance that threads a little chakra through every palm. */
    private static final String SOSHIKEN = "narutomod:soshiken_until";

    /** The silent kill stance: quiet feet and a harder blow from behind. */
    private static final String SILENT_KILL = "narutomod:silent_kill_until";

    /** The drunken fist: an unreadable sway that dodges and misses in equal measure. */
    private static final String DRUNKEN_FIST = "narutomod:drunken_fist_until";

    /** The last technique still sitting in the caster's hand. */
    private static final String RESIDUAL_FORCE = "narutomod:residual_force_until";

    /** How far a decoy pulls a mob's attention away from its owner. */
    private static final double DECOY_RANGE = 16.0d;

    /** How much health a medic clone can give back before it pops. */
    private static final float MEDIC_BUDGET = 8.0f;

    private TechniqueMarkEvents() {
    }

    /** A severed node also closes the body's own healing, not only its chakra. */    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().getLong(RUPTURE) > entity.level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    /** Marks wear off; sleeping bodies get their mind back when the mark does. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();

        if (data.contains(SLEEP)) {
            if (now >= data.getLong(SLEEP)) {
                wake(entity);
            }
        }

        // The moon's illusion lets go of the mind the same way sleep does.
        if (data.contains(TSUKUYOMI) && now >= data.getLong(TSUKUYOMI)) {
            data.remove(TSUKUYOMI);
            if (entity instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }

        // A body under the weight of the sky does not get to leave the ground.
        if (data.contains("narutomod:heaven_weight_until")) {
            if (now >= data.getLong("narutomod:heaven_weight_until")) {
                data.remove("narutomod:heaven_weight_until");
            } else if (!entity.onGround()) {
                entity.setDeltaMovement(entity.getDeltaMovement().x, Math.min(-0.08d, entity.getDeltaMovement().y),
                        entity.getDeltaMovement().z);
            }
        }

        // Frozen bodies keep still until the seam closes; the non-living ones (arrows, charges,
        // falling blocks) are handled on the level tick, because this event only sees living bodies.
        if (data.contains(STASIS) && now >= data.getLong(STASIS)) {
            data.remove(STASIS);
            restoreGravity(entity, data);
        }
    }

    /** Sleep lasts only until the first hit lands. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide && victim.getPersistentData().contains(SLEEP)) {
            wake(victim);
        }
        // The drunken sway: one blow in four goes past the caster, one in seven of the caster's own
        // blows lands nowhere. Rolled before anything else so the rest of the pipeline sees a clean
        // hit or none at all.
        long now = victim.level().getGameTime();
        if (victim instanceof Player swaying
                && swaying.getPersistentData().getLong(DRUNKEN_FIST) > now
                && swaying.getRandom().nextFloat() < 0.25f) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Player swinger
                && swinger.getPersistentData().getLong(DRUNKEN_FIST) > now
                && swinger.getRandom().nextFloat() < 0.15f) {
            event.setCanceled(true);
            return;
        }
        // Silent kill: a blow from behind while the stance is held lands half again as hard.
        if (event.getSource().getEntity() instanceof Player assassin
                && assassin.getPersistentData().getLong(SILENT_KILL) > now
                && isBehind(victim, assassin)) {
            event.setAmount(event.getAmount() * 1.5f);
        }
        // Residual force: the weight of the last technique is still in the hand.
        if (event.getSource().getEntity() instanceof Player caster
                && event.getSource().getDirectEntity() == caster
                && caster.getPersistentData().getLong(RESIDUAL_FORCE) > now) {
            event.setAmount(event.getAmount() + 5.0f);
            victim.knockback(0.5d, caster.getX() - victim.getX(), caster.getZ() - victim.getZ());
            caster.getPersistentData().remove(RESIDUAL_FORCE);
        }
        // Footwork of the wind: the stance is fast and light, and it pays for that with force.
        if (event.getSource().getEntity() instanceof Player attacker) {
            INinjaData data = attacker.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
            if (data != null && WindStepAbility.isActive(data)) {
                event.setAmount(event.getAmount() * 0.8f);
            }
            // Soshiken: an empty palm carries chakra into the tenketsu on every hit.
            if (data != null
                    && attacker.getPersistentData().getLong(SOSHIKEN) > attacker.level().getGameTime()
                    && attacker.getMainHandItem().isEmpty()
                    && victim != attacker) {
                event.setAmount(event.getAmount() + 2.0f);
                victim.hurt(attacker.damageSources().magic(), 2.0f);
                victim.getPersistentData().putLong("narutomod:tenketsu_until",
                        victim.level().getGameTime() + 20L);
                float cost = Math.min(6.0f, data.getChakra());
                if (cost > 0.0f) {
                    data.useChakra(cost, 10);
                }
            }
            // A suggestion lasts only until the one who planted it strikes.
            CompoundTag victimData = victim.getPersistentData();
            if (victimData.getString(SUGGESTION_SOURCE).equals(attacker.getStringUUID())) {
                victimData.remove(SUGGESTION);
                victimData.remove(SUGGESTION_SOURCE);
            }
        }
    }

    /**
     * Two marks keep a mob from choosing a target: a suggestion that names the caster, and a decoy
     * standing between the two. A decoy is the better target of the two, so it is offered first.
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

        ShadowCloneEntity decoy = nearestDecoy(mob, chosen);
        if (decoy != null) {
            event.setNewTarget(decoy);
            return;
        }

        CompoundTag data = mob.getPersistentData();
        if (data.getLong(SUGGESTION) > mob.level().getGameTime()
                && data.getString(SUGGESTION_SOURCE).equals(chosen.getStringUUID())) {
            event.setNewTarget(null);
        }
    }

    /**
     * Runs the marks that belong to a player: the carried body above the shoulders, the medic clone
     * feeding health back, and the decoy's countdown.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        // The body on the shoulders has to follow every step, so it is pinned every tick; the
        // medic clone only has something to do once a second.
        carryBody(player);
        if (player.tickCount % 20 != 0) {
            return;
        }
        tickMedicClones(player);
    }

    /** A body on the shoulders is held above the head and rides along with every step. */
    private static void carryBody(Player player) {
        UUID carried = com.sekwah.narutomod.abilities.jutsus.BeastCarryAbility.carried(player);
        if (carried == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity body = level.getEntity(carried);
        if (!(body instanceof LivingEntity living) || !living.isAlive()) {
            com.sekwah.narutomod.abilities.jutsus.BeastCarryAbility.clear(player);
            return;
        }
        living.setPos(player.getX(), player.getY() + player.getBbHeight() + 0.1d, player.getZ());
        living.setDeltaMovement(0.0d, 0.0d, 0.0d);
        living.fallDistance = 0.0f;
    }

    /** Each medic clone pays its caster one heart's worth of health every two seconds. */
    private static void tickMedicClones(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<ShadowCloneEntity> clones = level.getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(24.0d), clone -> clone.getPersistentData().getBoolean(MEDIC));
        for (ShadowCloneEntity clone : clones) {
            if (!clone.isAlive() || clone.getOwnerUUID().map(id -> !id.equals(player.getUUID())).orElse(true)) {
                continue;
            }
            CompoundTag data = clone.getPersistentData();
            if (level.getGameTime() >= data.getLong(MEDIC_UNTIL)) {
                clone.discard();
                continue;
            }
            float healed = data.getFloat(MEDIC_HEALED);
            if (healed >= MEDIC_BUDGET || player.getHealth() >= player.getMaxHealth()) {
                continue;
            }
            float amount = Math.min(1.0f, MEDIC_BUDGET - healed);
            player.heal(amount);
            data.putFloat(MEDIC_HEALED, healed + amount);
        }
    }

    /** The closest decoy of the chosen target's owner, if one is close enough to matter. */
    private static ShadowCloneEntity nearestDecoy(Mob mob, LivingEntity chosen) {
        if (!(chosen instanceof Player owner) || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        List<ShadowCloneEntity> decoys = level.getEntitiesOfClass(ShadowCloneEntity.class,
                mob.getBoundingBox().inflate(DECOY_RANGE), clone ->
                        clone.isAlive()
                                && clone.getPersistentData().getBoolean(DECOY)
                                && clone.getPersistentData().getLong(DECOY_UNTIL) > level.getGameTime()
                                && clone.getOwnerUUID().map(id -> id.equals(owner.getUUID())).orElse(false));
        return decoys.isEmpty() ? null : decoys.get(0);
    }

    /** @return whether the attacker stands behind the victim's facing. */
    private static boolean isBehind(LivingEntity victim, LivingEntity attacker) {
        double dx = attacker.getX() - victim.getX();
        double dz = attacker.getZ() - victim.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001d) {
            return false;
        }
        var look = victim.getLookAngle();
        return (look.x * dx + look.z * dz) / length < -0.35d;
    }

    /** Ends a sleep early and gives the body its mind back. */
    private static void wake(LivingEntity entity) {        entity.getPersistentData().remove(SLEEP);
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
    }

    /** Carrying a body takes both arms, so the carrier cannot swing at anything. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (com.sekwah.narutomod.abilities.jutsus.BeastCarryAbility.carried(event.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    /** A body that has been made heavy cannot jump while the mark lasts. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity().getPersistentData().getLong("narutomod:heaven_weight_until")
                > event.getEntity().level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    /**
     * Holds the non-living things the Jougan froze - arrows, lit charges, falling blocks - and gives
     * their gravity back when the seam closes. They are not {@link LivingEntity}s, so the living tick
     * above never sees them; this pass is cheap because it only looks at a level every ten ticks.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now % 10L != 0L) {
            return;
        }
        for (Entity entity : level.getEntities().getAll()) {
            CompoundTag data = entity.getPersistentData();
            if (entity instanceof ShadowCloneEntity clone
                    && data.getBoolean(DECOY)
                    && now >= data.getLong(DECOY_UNTIL)) {
                clone.discard();
                continue;
            }
            if (!data.contains(STASIS)) {
                continue;
            }
            if (now >= data.getLong(STASIS)) {
                data.remove(STASIS);
                restoreGravity(entity, data);
            } else {
                entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            }
        }
    }

    /** Gives a frozen body its gravity back, but only if the freeze is what took it away. */
    private static void restoreGravity(Entity entity, CompoundTag data) {
        if (data.getBoolean("narutomod:stasis_restore_gravity")) {
            data.remove("narutomod:stasis_restore_gravity");
            entity.setNoGravity(false);
        }
    }
}
