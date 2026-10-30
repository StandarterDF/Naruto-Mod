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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class AsuraMissileItem extends Item {
    public AsuraMissileItem(Properties properties) {
        super(properties);
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
        LivingEntity target = Targeting.lookedAtEntity(player, 24.0d) instanceof LivingEntity l ? l : null;
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