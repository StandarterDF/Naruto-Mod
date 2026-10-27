package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.CamelliaDanceAbility;
import com.sekwah.narutomod.abilities.jutsus.MindTransferAbility;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * The consequences of the clan techniques that cannot live inside them: what a landed blow does to a
 * body made of bone and to an empty body.
 *
 * <p>A technique knows how to start and how to stop; it does not know when somebody hits it. The
 * bloodline techniques here are all read from the hit that lands - the Camellia Dance sharpens a
 * blow that has already been swung and a transferred mind doubles the harm its abandoned body takes.
 * Those two are decided here, on the server, from the marks the abilities leave behind; the
 * abilities themselves stay self-contained.</p>
 *
 * <p>Everything that has to be undone eventually is also undone here: a mind that has to come home
 * when the creature it was in dies, a caster who left the world mid-technique.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class ClanTechniqueEvents {

    /** A body nobody is in takes twice the harm it would have taken with a mind in it. */
    private static final float ABANDONED_BODY_MULTIPLIER = 2.0f;

    private static final UUID NO_ENTITY = new UUID(0L, 0L);

    private ClanTechniqueEvents() {
    }

    /**
     * Every blow is read here: an empty body takes it twice over, and a bone blade adds its own cut.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        long now = victim.level().getGameTime();

        // A mind that is elsewhere cannot defend the body it left.
        if (victim instanceof Player body && MindTransferAbility.isTransferred(body)) {
            event.setAmount(event.getAmount() * ABANDONED_BODY_MULTIPLIER);
        }

        // The Camellia Dance: only a swing in the caster's own hand counts, not a thrown thing.
        if (event.getSource().getEntity() instanceof Player attacker
                && event.getSource().getDirectEntity() == attacker
                && attacker != victim
                && attacker.getPersistentData().getLong(CamelliaDanceAbility.UNTIL_TAG) > now) {
            cut(attacker, victim, event);
        }
    }

    /**
     * A bone blade's cut: a little more than the blow was worth, a quarter of the whole carried past
     * armour as a second, unblockable hit, and a wound that keeps bleeding.
     */
    private static void cut(Player attacker, LivingEntity victim, LivingHurtEvent event) {
        float total = event.getAmount() + CamelliaDanceAbility.BONUS_DAMAGE;
        event.setAmount(total);

        CompoundTag data = victim.getPersistentData();
        data.putLong(CamelliaDanceAbility.BLEED_TAG, victim.level().getGameTime() + CamelliaDanceAbility.BLEED_TICKS);
        // Vanilla has no bleed, so wither stands in for it: it drains the same way and reads as a wound.
        victim.addEffect(new MobEffectInstance(MobEffects.WITHER, CamelliaDanceAbility.BLEED_TICKS,
                0, false, false));

        if (victim.level() instanceof ServerLevel level) {
            Vec3 at = victim.position().add(0.0d, victim.getBbHeight() * 0.6d, 0.0d);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.2d, 0.2d, 0.2d, 0.05d);
        }

        // The second hit carries no entity, so it cannot come back through this hook and cut again.
        victim.hurt(attacker.damageSources().magic(), total * CamelliaDanceAbility.ARMOUR_BYPASS);
    }

    /**
     * Ends the holds whose owner is gone: the creature a mind was in has died, or the caster did. A
     * creature whose caster can no longer be found - a server that was stopped mid-transfer - is
     * given its own mind back where it stands, so nothing is left frozen forever.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        if (dead instanceof Mob mob) {
            // Most mobs never carried a mind: no tag means nothing to release, not a missing owner.
            if (!mob.getPersistentData().hasUUID(MindTransferAbility.OWNER_TAG)) {
                return;
            }
            UUID owner = mob.getPersistentData().getUUID(MindTransferAbility.OWNER_TAG);
            if (!owner.equals(NO_ENTITY)) {
                Player caster = mob.level().getServer() == null ? null
                        : mob.level().getServer().getPlayerList().getPlayer(owner);
                if (caster == null) {
                    MindTransferAbility.releaseMob(mob);
                } else {
                    MindTransferAbility.release(caster);
                }
            }
        }
        if (dead instanceof Player player && MindTransferAbility.isTransferred(player)) {
            MindTransferAbility.release(player);
        }
    }

    /** A caster who leaves mid-technique brings the mind home with them. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        // Unconditional: a cast that was refused before its first tick still left a mark behind.
        MindTransferAbility.release(player);
    }
}
