package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.KamuiJutsuAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /**
     * While the swirl is held open the caster is half inside their own pocket, and nothing that hits
     * them is really touching them: damage is refused outright.
     *
     * <p>Run at the highest priority so it happens before anything that reacts to being hurt - in
     * particular before the second breath trick, which must not spend itself on an attack that was
     * never going to land.</p>
     *
     * <p>The ways out of the world are left alone: {@code /kill}, the void below the platforms and
     * starvation still work, so phasing cannot be used to sit out a fight forever or to ignore the
     * rules of the game.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE)) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (KamuiJutsuAbility.isHolding(ninjaData)) {
                event.setCanceled(true);
            }
        });
    }
}
