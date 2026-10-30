package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SenriganGuidanceAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenriganGuidanceEvents {
    private SenriganGuidanceEvents() {
    }

    private static volatile boolean redirecting = false;

    /** Cone half-angle for guidance: targets must lie within about 30 degrees of the crosshair. */
    private static final double GUIDANCE_CONE_COS = 0.82d;

    private static final String GUIDED_TAG = "narutomod:senriganGuided";

    /** Finds the living creature closest to the caster's crosshair within 32 blocks. */
    public static LivingEntity findTarget(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double bestDot = GUIDANCE_CONE_COS;
        LivingEntity best = null;
        AABB box = player.getBoundingBox().inflate(32.0d);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity e : candidates) {
            if (e == player || !e.isAlive() || !e.isPickable()) {
                continue;
            }
            if (e instanceof Player p && p.isSpectator()) {
                continue;
            }
            if (Friends.areFriends(player, e)) {
                continue;
            }
            if (e instanceof ShadowCloneEntity clone && clone.isOwnedBy(player)) {
                continue;
            }
            if (e instanceof TamableAnimal tame && tame.isTame() && player.getUUID().equals(tame.getOwnerUUID())) {
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
        if (event.getRayTraceResult() instanceof EntityHitResult hit) {
            LivingEntity target = findTarget(player);
            if (target != null && hit.getEntity() == target) {
                return; // allow normal hit
            }
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        // Single pass over the level's projectiles: guide those whose owner has the stance up,
        // and restore normal physics for the rest that were previously guided.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                new AABB(-3.0e7d, -3.0e7d, -3.0e7d, 3.0e7d, 3.0e7d, 3.0e7d))) {
            Entity owner = projectile.getOwner();
            // Non-combat projectiles (ender pearls, fishing hooks, eyes of ender) are never guided.
            if (projectile instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl
                    || projectile instanceof net.minecraft.world.entity.projectile.FishingHook
                    || projectile.getClass().getName().equals("net.minecraft.world.entity.projectile.EyeOfEnder")) {
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                }
                continue;
            }
            if (!(owner instanceof Player player)) {
                // Owner gone: stop guiding and restore physics.
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                }
                continue;
            }
            if (projectile instanceof AbstractArrow arrow
                    && ((com.sekwah.narutomod.mixin.common.AbstractArrowAccessor) (Object) arrow).naruto$isInGround()) {
                if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                    projectile.noPhysics = false;
                    projectile.getPersistentData().remove(GUIDED_TAG);
                }
                continue;
            }
            if (guidanceActive(player)) {
                LivingEntity target = findTarget(player);
                if (target == null) {
                    if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                        projectile.noPhysics = false;
                        projectile.getPersistentData().remove(GUIDED_TAG);
                    }
                    continue;
                }
                double speed = projectile.getDeltaMovement().length();
                if (speed < 0.05d) {
                    if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                        projectile.noPhysics = false;
                        projectile.getPersistentData().remove(GUIDED_TAG);
                    }
                    continue;
                }
                projectile.noPhysics = true;
                projectile.getPersistentData().putBoolean(GUIDED_TAG, true);
                Vec3 aim = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d)
                        .subtract(projectile.position());
                if (aim.lengthSqr() > 1.0e-8d) {
                    projectile.setDeltaMovement(aim.normalize().scale(speed));
                }
            } else if (projectile.getPersistentData().getBoolean(GUIDED_TAG)) {
                projectile.noPhysics = false;
                projectile.getPersistentData().remove(GUIDED_TAG);
            }
        }
    }
}