package com.sekwah.narutomod.item;

import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A lump of crystal that holds chakra the way a battery does, but is small enough to craft with.
 *
 * <p>Where the battery is a finished vessel, the crystal is the material: it is what the better
 * recipes want. A crystal is charged by pouring chakra into it by hand, or for free by leaving it
 * under a lightning rod in a storm (see {@code events/LightningEvents}). Sneak and use it to take the
 * chakra back out.</p>
 *
 * <p>Charge is written into the stack's NBT and survives crafting only in the sense that the stack
 * itself survives - a charged crystal put into a recipe is spent like any other ingredient. That is
 * deliberate: the recipes that use crystals are about the material, not about carrying a charge
 * through a workbench.</p>
 */
public class ChakraCrystalItem extends Item {

    /** How much chakra a crystal holds when full. */
    public static final float CAPACITY = 100.0f;

    /** How much moves in or out per use. */
    public static final float TRANSFER_PER_USE = 20.0f;

    private static final String CHARGE_TAG = "Chakra";

    public ChakraCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null || !ninjaData.isNinjaModeEnabled()) {
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        if (player.isShiftKeyDown()) {
            withdraw(stack, player, ninjaData);
        } else {
            charge(stack, player, ninjaData);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    private void charge(ItemStack stack, Player player, INinjaData ninjaData) {
        float stored = getCharge(stack);
        float room = CAPACITY - stored;
        if (room <= 0.0f) {
            player.displayClientMessage(Component.translatable("item.narutomod.chakra_crystal.full")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        float moved = Math.min(Math.min(TRANSFER_PER_USE, room), ninjaData.getChakra());
        if (moved <= 0.0f) {
            player.displayClientMessage(Component.translatable("item.narutomod.chakra_crystal.nochakra")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ninjaData.addChakra(-moved);
        setCharge(stack, stored + moved);
        chime(player);
    }

    private void withdraw(ItemStack stack, Player player, INinjaData ninjaData) {
        float stored = getCharge(stack);
        float room = Math.max(ninjaData.getMaxChakra() - ninjaData.getChakra(), 0.0f);
        float moved = Math.min(Math.min(TRANSFER_PER_USE, stored), room);
        if (moved <= 0.0f) {
            player.displayClientMessage(Component.translatable(stored <= 0.0f
                            ? "item.narutomod.chakra_crystal.empty"
                            : "item.narutomod.chakra_crystal.selfull")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ninjaData.addChakra(moved);
        setCharge(stack, stored - moved);
        chime(player);
    }

    private void chime(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.narutomod.chakra_crystal.charge",
                (int) getCharge(stack), (int) CAPACITY).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getCharge(stack) >= CAPACITY;
    }

    public static float getCharge(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0.0f : tag.getFloat(CHARGE_TAG);
    }

    /** Never lets a stored charge slip past the capacity, whatever the caller asks for. */
    public static void setCharge(ItemStack stack, float charge) {
        stack.getOrCreateTag().putFloat(CHARGE_TAG,
                Math.min(Math.max(charge, 0.0f), CAPACITY));
    }
}
