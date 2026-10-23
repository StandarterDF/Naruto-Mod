package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.JouganAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The combat half of the Jougan: the eye sees where a body's chakra runs thin, so a punch that
 * would land anyway lands on that spot.
 *
 * <p>Only a swing with the caster's own body counts - an arrow is not aimed by the eye - and only a
 * hostile mob is read that way, which keeps the bonus a hunting tool rather than a PvP surprise. The
 * damage is added before armour is applied, exactly like any other attack bonus, and the spark is
 * only there so the player can see that it fired.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JouganEvents {

    private JouganEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!(target instanceof Mob) || !(event.getSource().getDirectEntity() instanceof Player player)
                || event.getSource().getEntity() != player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!JouganAbility.isActive(ninjaData)) {
                return;
            }
            event.setAmount(event.getAmount() + JouganAbility.WEAK_POINT_DAMAGE);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                        6, 0.25d, 0.25d, 0.25d, 0.1d);
            }
        });
    }
}
