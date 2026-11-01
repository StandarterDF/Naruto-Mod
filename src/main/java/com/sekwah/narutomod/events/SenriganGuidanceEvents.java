package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SenriganGuidanceAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenriganGuidanceEvents {
    private SenriganGuidanceEvents() {
    }

    private static volatile boolean redirecting = false;

    /**
     * UUIDs of projectiles currently being guided (noPhysics=true), kept per dimension so that an
     * idle level cannot clear the marks of a level that is still guiding.
     */
    private static final Map<ResourceKey<Level>, Set<UUID>> GUIDED_BY_LEVEL = new HashMap<>();

    private static Set<UUID> guidedFor(ServerLevel level) {
        return GUIDED_BY_LEVEL.computeIfAbsent(level.dimension(), key -> new HashSet<>());
    }

    /** Cone half-angle for guidance: targets must lie within about 30 degrees of the crosshair. */
    private static final double GUIDANCE_CONE_COS = 0.82d;

    private static final String GUIDED_TAG = "narutomod:senriganGuided";

    /** Last tick's distance to the guided target, used for the manual hit check. */
    private static final String PREV_DIST_TAG = "narutomod:guidedPrevDist";

    /**
     * Shared target filter: the caster, dead/unpickable entities, spectators, friends, the caster's own
     * shadow clones and the caster's tamed animals are never valid targets.
     */
    private static boolean isValidTarget(Player player, LivingEntity e) {
        if (e == player || !e.isAlive() || !e.isPickable()) {
            return false;
        }
        if (e instanceof Player p && p.isSpectator()) {
            return false;
        }
        if (Friends.areFriends(player, e)) {
            return false;
        }
        if (e instanceof ShadowCloneEntity clone && clone.isOwnedBy(player)) {
            return false;
        }
        if (e instanceof TamableAnimal tame && tame.isTame() && player.getUUID().equals(tame.getOwnerUUID())) {
            return false;
        }
        return true;
    }

    /** Finds the living creature closest to the caster's crosshair within 32 blocks. */
    public static LivingEntity findTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double bestDot = GUIDANCE_CONE_COS;
        LivingEntity best = null;
        AABB box = player.getBoundingBox().inflate(32.0d);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : candidates) {
            if (!isValidTarget(player, e)) {
                continue;
            }
            Vec3 to = e.position().add(0.0d, e.getBbHeight() * 0.5d, 0.0d).subtract(eye);
            double len = to.length();
            if (len < 1.0e-6d || len > 32.0d) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot >= bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static boolean guidanceActive(Player player) {
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA).map(nd ->
                SenriganGuidanceAbility.isActive(nd)).orElse(false);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (redirecting) {
            return;
        }
        if (!guidanceActive(player)) {
            return;
        }
        LivingEntity real = findTarget(player);
        if (real == null || real == event.getTarget()) {
            return;
        }
        event.setCanceled(true);
        redirecting = true;
        try {
            player.attack(real);
        } finally {
            redirecting = false;
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        if (!guidanceActive(player)) {
            return;
        }
        LivingEntity real = findTarget(player);
        if (real == null) {
            return;
        }
        event.setCanceled(true);
        if (!redirecting) {
            redirecting = true;
            try {
                player.attack(real);
            } finally {
                redirecting = false;
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        Entity owner = projectile.getOwner();
        if (!(owner instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!guidanceActive(player)) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity le
                && isValidTarget(player, le)) {
            // Valid hit: let the vanilla damage resolve, just stop guiding this projectile.
            if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                projectile.noPhysics = false;
                projectile.getPersistentData().remove(GUIDED_TAG);
            }
            if (player.level() instanceof ServerLevel serverLevel) {
                guidedFor(serverLevel).remove(projectile.getUUID());
            }
            return;
        }
        // Blocks and friendly targets never stop a guided projectile.
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Set<UUID> guidedProjectiles = guidedFor(level);
        // Collect the players who actually have guidance up, once per tick.
        java.util.Set<Player> guidedPlayers = new java.util.HashSet<>();
        for (Player p : level.players()) {
            if (guidanceActive(p)) {
                guidedPlayers.add(p);
            }
        }

        // No one is guiding: only reset previously guided projectiles, without scanning the level.
        if (guidedPlayers.isEmpty()) {
            java.util.Iterator<UUID> it = guidedProjectiles.iterator();
            while (it.hasNext()) {
                UUID uuid = it.next();
                Entity e = level.getEntity(uuid);
                if (e instanceof Projectile projectile) {
                    if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                        projectile.noPhysics = false;
                        projectile.getPersistentData().remove(GUIDED_TAG);
                    }
                }
                it.remove();
            }
            if (guidedProjectiles.isEmpty()) {
                GUIDED_BY_LEVEL.remove(level.dimension());
            }
            return;
        }

        // One target lookup per guiding player per tick.
        java.util.Map<Player, LivingEntity> targetCache = new IdentityHashMap<>();
        for (Player p : guidedPlayers) {
            targetCache.put(p, findTarget(p));
        }

        // Gather candidate projectiles: those near any guiding player plus any previously guided one.
        java.util.Set<Projectile> candidates = new java.util.HashSet<>();
        for (Player p : guidedPlayers) {
            AABB area = p.getBoundingBox().inflate(64.0d);
            candidates.addAll(level.getEntitiesOfClass(Projectile.class, area));
        }
        java.util.Iterator<UUID> git = guidedProjectiles.iterator();
        while (git.hasNext()) {
            UUID uuid = git.next();
            Entity e = level.getEntity(uuid);
            if (e instanceof Projectile p) {
                candidates.add(p);
            } else {
                git.remove();
            }
        }

        // Single pass over the candidate set.
        for (Projectile projectile : candidates) {
            UUID uuid = projectile.getUUID();
            Entity owner = projectile.getOwner();
            // Non-combat projectiles (ender pearls, fishing hooks, eyes of ender) are never guided.
            if (projectile instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl
                    || projectile instanceof net.minecraft.world.entity.projectile.FishingHook
                    || projectile.getClass().getName().equals("net.minecraft.world.entity.projectile.EyeOfEnder")) {
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                    guidedProjectiles.remove(uuid);
                }
                continue;
            }
            if (!(owner instanceof Player player)) {
                // Owner gone: stop guiding and restore physics.
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                    guidedProjectiles.remove(uuid);
                }
                continue;
            }
            if (projectile instanceof AbstractArrow arrow
                    && ((com.sekwah.narutomod.mixin.common.AbstractArrowAccessor) (Object) arrow).naruto$isInGround()) {
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                    guidedProjectiles.remove(uuid);
                }
                continue;
            }
            if (guidedPlayers.contains(player)) {
                LivingEntity target = targetCache.get(player);
                if (target == null) {
                    if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                        projectile.noPhysics = false;
                        projectile.getPersistentData().remove(GUIDED_TAG);
                        guidedProjectiles.remove(uuid);
                    }
                    continue;
                }
                double speed = projectile.getDeltaMovement().length();
                if (speed < 0.05d) {
                    if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                        projectile.noPhysics = false;
                        projectile.getPersistentData().remove(GUIDED_TAG);
                        guidedProjectiles.remove(uuid);
                    }
                    continue;
                }
                net.minecraft.nbt.CompoundTag data = projectile.getPersistentData();
                Vec3 aim = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d)
                        .subtract(projectile.position());
                double dist = aim.length();
                double prevDist = data.getDouble(PREV_DIST_TAG);
                data.putDouble(PREV_DIST_TAG, dist);
                // noPhysics skips entity collisions, so the hit has to be applied by hand.
                double reach = speed + 0.75d;
                if (dist <= reach || (prevDist > 0.0d && prevDist <= reach && dist <= prevDist + speed)) {
                    float dmg = projectile instanceof AbstractArrow arrowHit
                            ? (float) (arrowHit.getBaseDamage() * Mth.clamp(speed * 2.0d, 0.5d, 2.0d))
                            : 4.0f;
                    target.hurt(projectile instanceof AbstractArrow arrowHit
                            ? player.damageSources().arrow(arrowHit, player)
                            : player.damageSources().thrown(projectile, player), dmg);
                    projectile.discard();
                    guidedProjectiles.remove(uuid);
                    continue;
                }
                projectile.noPhysics = true;
                projectile.getPersistentData().putBoolean(GUIDED_TAG, true);
                guidedProjectiles.add(uuid);
                if (aim.lengthSqr() > 1.0e-8d) {
                    projectile.setDeltaMovement(aim.normalize().scale(speed));
                }
            } else if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                projectile.noPhysics = false;
                projectile.getPersistentData().remove(GUIDED_TAG);
                guidedProjectiles.remove(uuid);
            }
        }
        if (guidedProjectiles.isEmpty()) {
            GUIDED_BY_LEVEL.remove(level.dimension());
        }
    }
}