package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.KamuiJutsuAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps the Kamui pocket dimension honest: nothing lives in there that a caster did not drag in,
 * and everything dragged in gets thrown back out again after a while.
 *
 * @see KamuiJutsuAbility
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KamuiEvents {

    /**
     * Counts down the time anything but the caster has left in the void.
     *
     * <p>Dragged entities carry their own countdown and return coordinate, so this only has to look
     * at things that were actually taken. Anything that is not in the pocket any more (it was spat
     * out, or it died and something else took its tags) is dropped from the books.</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        int ticksLeft = KamuiJutsuAbility.banishTicksLeft(entity);
        if (ticksLeft < 0) {
            return;
        }
        if (!KamuiJutsuAbility.isInKamui(entity)) {
            KamuiJutsuAbility.clearBanish(entity);
            return;
        }
        if (ticksLeft > 1) {
            KamuiJutsuAbility.setBanishTicksLeft(entity, ticksLeft - 1);
            return;
        }
        KamuiJutsuAbility.spitBack(entity);
    }

    /**
     * The pocket has no wildlife, no monsters and no ore: it is a flat bedrock plane in the void,
     * and the only things in it are the ones a caster brought.
     *
     * <p>1.20.1 has no {@code LivingSpawnEvent.CheckSpawn} any more; the position gate of the mob
     * spawn flow is {@link MobSpawnEvent.PositionCheck}.</p>
     */
    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            return;
        }
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(KamuiJutsuAbility.KAMUI_DIMENSION)) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * A dragged mob waits in the void for its turn, even with nobody else in there to keep it
     * loaded and looked at: without this it would quietly despawn after a minute and the promised
     * trip back would never happen.
     */
    @SubscribeEvent
    public static void onAllowDespawn(MobSpawnEvent.AllowDespawn event) {
        if (event.getResult() != Event.Result.DEFAULT) {
            return;
        }
        if (KamuiJutsuAbility.banishTicksLeft(event.getEntity()) >= 0) {
            event.setResult(Event.Result.DENY);
        }
    }
}
