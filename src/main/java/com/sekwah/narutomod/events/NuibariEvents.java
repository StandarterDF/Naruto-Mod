package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.item.weapons.NuibariItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NuibariEvents {

    private NuibariEvents() {
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof NuibariItem)) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        event.setCanceled(true);
        NuibariItem.performAttack(player);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(NuibariItem.THREAD_TAG)) {
            return;
        }
        long now = entity.level().getGameTime();
        long until = data.getLong(NuibariItem.THREAD_TAG);
        if (until <= now) {
            data.remove(NuibariItem.THREAD_TAG);
            return;
        }
        if (now % 20 == 0L && entity.isAlive() && !entity.isSpectator()) {
            entity.hurt(entity.damageSources().magic(), NuibariItem.BLEED_DAMAGE);
        }
        if (entity.level() instanceof ServerLevel level && entity.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5d, entity.getZ(),
                    2, 0.25d, 0.25d, 0.25d, 0.01d);
        }
    }
}
