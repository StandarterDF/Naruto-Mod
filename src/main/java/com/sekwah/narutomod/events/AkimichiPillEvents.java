package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.effect.NarutoEffects;
import com.sekwah.narutomod.item.AkimichiPillItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AkimichiPillEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (!player.isAlive()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        // Green pill: delayed nausea after the strength window.
        if (data.contains(AkimichiPillItem.SPINACH_NAUSEA_AT_TAG)) {
            long at = data.getLong(AkimichiPillItem.SPINACH_NAUSEA_AT_TAG);
            if (now >= at) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
                data.remove(AkimichiPillItem.SPINACH_NAUSEA_AT_TAG);
            }
        }

        // Red pill: timed flight, then collapse into critical exhaustion.
        if (data.contains(AkimichiPillItem.CHILI_UNTIL_TAG)) {
            long until = data.getLong(AkimichiPillItem.CHILI_UNTIL_TAG);
            if (now < until) {
                if (!player.getAbilities().mayfly) {
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                }
            } else {
                if (data.contains(AkimichiPillItem.CHILI_SAVED_TAG)) {
                    CompoundTag saved = data.getCompound(AkimichiPillItem.CHILI_SAVED_TAG);
                    player.getAbilities().mayfly = saved.getBoolean("mayfly");
                    player.getAbilities().flying = saved.getBoolean("flying");
                    player.onUpdateAbilities();
                    data.remove(AkimichiPillItem.CHILI_SAVED_TAG);
                } else {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                player.setHealth(1.0f);
                player.addEffect(new MobEffectInstance(NarutoEffects.CRITICAL_EXHAUSTION.get(), 20 * 30, 0));
                data.remove(AkimichiPillItem.CHILI_UNTIL_TAG);
            }
        }
    }
}
