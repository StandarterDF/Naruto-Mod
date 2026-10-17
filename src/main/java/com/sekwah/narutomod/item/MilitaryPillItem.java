package com.sekwah.narutomod.item;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Army rations, the pill a ninja swallows when there is no time to sit down and recover.
 *
 * <p>It hands back a lump of chakra and stamina straight away and keeps the swallower moving and
 * digging quicker for half a minute, so it is the field answer to the meditation technique: less
 * chakra in total, but no clones to look after and nothing to wait for. One pill every minute.</p>
 */
public class MilitaryPillItem extends Item {

    /** Chakra handed back the moment the pill goes down. */
    public static final float CHAKRA_RESTORED = 35.0f;

    /** Stamina handed back the moment the pill goes down. */
    public static final float STAMINA_RESTORED = 35.0f;

    /** How long the speed and haste last, in ticks. */
    private static final int RUSH_DURATION = 20 * 30;

    /** How long the regeneration lasts, in ticks. */
    private static final int HEAL_DURATION = 20 * 8;

    /** Item cooldown, in ticks. */
    private static final int COOLDOWN = 20 * 60;

    public MilitaryPillItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                ninjaData.addChakra(CHAKRA_RESTORED);
                ninjaData.addStamina(STAMINA_RESTORED);
            });
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RUSH_DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, RUSH_DURATION, 0));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, HEAL_DURATION, 0));
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return result;
    }
}
