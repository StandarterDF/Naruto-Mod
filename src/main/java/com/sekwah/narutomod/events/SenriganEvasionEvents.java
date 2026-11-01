package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SenriganEvasionAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.mixin.common.AbstractArrowAccessor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SenriganEvasionEvents {
    private SenriganEvasionEvents() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof Player player)) {
            return;
        }
        // Do not evade your own projectiles.
        Projectile projectile = event.getProjectile();
        if (projectile.getOwner() == player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            // Both sides have to eat the impact: the client predicts the hit on its own copy of the
            // projectile, so a server-only cancel made the arrow visibly bounce off the caster and
            // then jump ahead once the correction arrived.
            event.setCanceled(true);
            pushProjectileThrough(player, projectile);
            if (player.level().isClientSide) {
                return;
            }
            CompoundTag data = projectile.getPersistentData();
            // Already evaded by this same player: keep passing through without paying again.
            if (data.contains("narutomod:senriganEvaded")) {
                long mark = data.getLong("narutomod:senriganEvaded");
                int storedPlayer = (int) (mark >> 32);
                int expiry = (int) (mark & 0xFFFFFFFFL);
                if (storedPlayer == player.getId() && projectile.tickCount < expiry) {
                    return;
                }
                data.remove("narutomod:senriganEvaded");
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (ninjaData.getChakra() >= cost) {
                ninjaData.useChakra(cost, 10);
                // Mark so the same projectile does not charge again on the next tick.
                data.putLong("narutomod:senriganEvaded",
                        ((long) player.getId() << 32) | (projectile.tickCount + 20L));
            } else {
                // Not enough chakra: end the stance and let the projectile land.
                ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                        com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
            }
        });
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        boolean isExplosion = event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        boolean isFall = event.getSource().is(DamageTypeTags.IS_FALL);
        boolean isFire = event.getSource().is(DamageTypeTags.IS_FIRE);
        // Falling and fire are never evaded.
        if (isFall || isFire) {
            return;
        }
        // Anything without a living direct source is not a body blow, unless it is an explosion, which
        // the evasion fully absorbs.
        if (!isExplosion && !(event.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (isExplosion) {
                if (ninjaData.getChakra() >= cost) {
                    // Fully absorb the blast, paid for with chakra.
                    event.setCanceled(true);
                    ninjaData.useChakra(cost, 10);
                    if (player.level() instanceof ServerLevel level) {
                        level.sendParticles(ParticleTypes.ENCHANT,
                                player.getX(), player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                                12, 0.35d, 0.35d, 0.35d, 0.03d);
                        level.sendParticles(ParticleTypes.END_ROD,
                                player.getX(), player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                                8, 0.25d, 0.25d, 0.25d, 0.02d);
                        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                    }
                } else {
                    // Not enough chakra: the stance ends and the blast lands in full.
                    ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                            com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
                }
                return;
            }
            if (ninjaData.getChakra() >= cost) {
                // Half damage passes through, paid for with chakra.
                event.setAmount(event.getAmount() * 0.5f);
                ninjaData.useChakra(cost, 10);
            } else {
                // Not enough chakra: the stance ends and this hit lands in full.
                ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                        com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
            }
        });
    }

    /**
     * A projectile that reaches the evading player is also handled on the attack event: some
     * projectiles (arrows with special hit handling, tridents) can otherwise still be treated as
     * having landed even when the impact event was cancelled.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Projectile projectile)) {
            return;
        }
        if (event.getSource().getEntity() == player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            CompoundTag data = projectile.getPersistentData();
            if (data.contains("narutomod:senriganEvaded")) {
                long mark = data.getLong("narutomod:senriganEvaded");
                int storedPlayer = (int) (mark >> 32);
                int expiry = (int) (mark & 0xFFFFFFFFL);
                if (storedPlayer == player.getId() && projectile.tickCount < expiry) {
                    event.setCanceled(true);
                    pushProjectileThrough(player, projectile);
                    return;
                }
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (ninjaData.getChakra() >= cost) {
                ninjaData.useChakra(cost, 10);
                event.setCanceled(true);
                data.putLong("narutomod:senriganEvaded",
                        ((long) player.getId() << 32) | (projectile.tickCount + 20L));
                pushProjectileThrough(player, projectile);
            } else {
                ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                        com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
            }
        });
    }

    /**
     * Explosions are swallowed before any entity damage is applied, the same way Momoshiki's palm
     * handles them: the evading player is simply removed from the blast's affected entities.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Player player : level.players()) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                if (!SenriganEvasionAbility.isActive(ninjaData)) {
                    return;
                }
                float cost = SenriganEvasionAbility.hitCost(ninjaData);
                if (ninjaData.getChakra() < cost) {
                    ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                            com.sekwah.narutomod.abilities.NarutoAbilities.SENRIGAN_EVASION.get());
                    return;
                }
                boolean caught = event.getAffectedEntities().removeIf(entity -> entity == player);
                if (caught) {
                    ninjaData.useChakra(cost, 10);
                    level.sendParticles(ParticleTypes.ENCHANT,
                            player.getX(), player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                            16, 0.35d, 0.35d, 0.35d, 0.03d);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                }
            });
        }
    }

    /** Push an evaded projectile past the player's hitbox so it keeps flying instead of bouncing. */
    private static void pushProjectileThrough(Player player, Projectile projectile) {
        if (projectile instanceof AbstractArrow arrow) {
            if (((AbstractArrowAccessor) arrow).naruto$isInGround()) {
                return;
            }
        }
        net.minecraft.world.phys.Vec3 motion = projectile.getDeltaMovement();
        if (motion.lengthSqr() < 1.0e-6d) {
            return;
        }
        // Just past the hitbox, not four blocks ahead: a long jump can bury the projectile in a wall
        // and make it vanish, which read as "the arrow bounced off and disappeared".
        double distance = player.getBbWidth() * 0.5d + 1.0d;
        net.minecraft.world.phys.Vec3 centre = player.position()
                .add(0.0d, player.getBbHeight() * 0.5d, 0.0d);
        projectile.setPos(centre.add(motion.normalize().scale(distance)));
    }
}