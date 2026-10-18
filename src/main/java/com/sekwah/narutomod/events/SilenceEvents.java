package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SilenceAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dulls mob ears for a caster holding the silence stance.
 *
 * <p>The game-event side of the stance stops anything hearing the caster, but a mob does not need to
 * hear: its targeting goals simply look for the nearest thing they are allowed to attack. So a
 * silenced player is refused as a target from beyond {@link SilenceAbility#DETECTION_RANGE} blocks
 * and is still quite visible up close - the stance is a whisper, not invisibility. Targets already
 * held give up too, once the caster is further away than that, so a chase that began before the
 * silence came up does not simply continue.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SilenceEvents {

    /** How often existing targets are checked and dropped, in ticks. */
    private static final int CULL_INTERVAL = 20;

    private SilenceEvents() {
    }

    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        LivingEntity target = event.getNewTarget();
        if (!(target instanceof Player player) || !SilenceAbility.isSilenced(player)) {
            return;
        }
        double range = SilenceAbility.DETECTION_RANGE;
        if (mob.distanceToSqr(player) > range * range) {
            event.setCanceled(true);
        }
    }

    /**
     * Lets go of chases that were already on. Only run once a second per silenced caster, and only
     * over the cull reach: a mob further out than that could not have been chasing them anyway.
     */
    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (player.tickCount % CULL_INTERVAL != 0 || !SilenceAbility.isSilenced(player)) {
            return;
        }
        double cull = SilenceAbility.CULL_RANGE;
        double notice = SilenceAbility.DETECTION_RANGE;
        double noticeSqr = notice * notice;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(cull))) {
            if (mob.getTarget() == player && mob.distanceToSqr(player) > noticeSqr) {
                mob.setTarget(null);
            }
        }
    }
}
