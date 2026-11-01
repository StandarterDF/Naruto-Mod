package com.sekwah.narutomod.item;

import com.sekwah.narutomod.abilities.jutsus.AsuraMissilesAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class AsuraMissileItem extends Item {

    /** How far the seeker can be aimed, and how far the body under the crosshair is marked. */
    private static final double AIM_RANGE = 24.0d;

    /** Ticks the aimed-at body keeps glowing; refreshed every tick while the aim holds. */
    private static final int AIM_GLOW_TICKS = 10;

    public AsuraMissileItem(Properties properties) {
        super(properties);
    }

    /**
     * While a missile is in hand, the body the caster is aiming at is marked with a glow. The hit
     * itself stays instantaneous - the glow is only there so the caster can see what the seeker
     * will find before spending one. Refreshed every tick and lapsing a moment after the aim moves.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        if (player.getMainHandItem() != stack && player.getOffhandItem() != stack) {
            return;
        }
        if (Targeting.lookedAtEntity(player, AIM_RANGE) instanceof LivingEntity target
                && target != player) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, AIM_GLOW_TICKS, 0, false, false));
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        LivingEntity target = Targeting.lookedAtEntity(player, AIM_RANGE) instanceof LivingEntity l ? l : null;
        if (target == null || target == player) {
            player.displayClientMessage(Component.translatable("jutsu.fail.asura_missiles.notarget")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            AsuraMissilesAbility.fireOne(serverLevel, player, target);
        });
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}