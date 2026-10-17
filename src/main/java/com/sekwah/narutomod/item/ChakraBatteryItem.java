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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A battery for chakra: the one piece of ninja kit that lets chakra be stored instead of spent.
 *
 * <p>The first ninja to use it becomes its owner, and only that ninja can draw from it or fill it
 * afterwards - a dropped battery is a dead battery to everyone else. The binding is written into the
 * stack's own NBT, so it travels with the item through chests, shulker boxes and death alike.</p>
 *
 * <p>Use it to draw chakra out of it, sneak and use it to put chakra back in. Both directions move
 * {@link #TRANSFER_PER_USE} at a time, so filling one is a deliberate act rather than a bank deposit,
 * and a ninja out of chakra has to stand there clicking if they want the whole reserve.</p>
 */
public class ChakraBatteryItem extends Item {

    /** How much chakra a battery can hold. */
    public static final float CAPACITY = 500.0f;

    /** How much moves in or out per use. */
    public static final float TRANSFER_PER_USE = 50.0f;

    /** How much a battery hands over when it is called on to keep a technique running. */
    private static final float UPKEEP_TOP_UP = 50.0f;

    /**
     * How low the ninja's own chakra has to run before a battery is called on at all.
     *
     * <p>Every technique that is paid for tick by tick costs only a fraction of this per tick, so
     * the pool is refilled well before the upkeep check can actually fail, and the battery is only
     * drained at about the rate the technique costs - not in one burst.</p>
     */
    private static final float UPKEEP_RESERVE = 30.0f;

    private static final String OWNER_TAG = "Owner";
    private static final String OWNER_NAME_TAG = "OwnerName";
    private static final String CHAKRA_TAG = "Chakra";

    public ChakraBatteryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        // First touch decides whose battery it is. Everyone else gets told to get their own.
        if (!hasOwner(stack)) {
            bindOwner(stack, player);
            player.displayClientMessage(Component.translatable("jutsu.battery.bound")
                    .withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        if (!isOwner(stack, player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.battery.owner",
                    getOwnerName(stack)).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        if (player.isShiftKeyDown()) {
            deposit(stack, player, ninjaData);
        } else {
            withdraw(stack, player, ninjaData);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    /** Pours chakra from the ninja into the battery, up to the transfer rate and the capacity. */
    private void deposit(ItemStack stack, Player player, INinjaData ninjaData) {
        float stored = getStoredChakra(stack);
        float room = CAPACITY - stored;
        if (room <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.battery.full")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        float moved = Math.min(Math.min(TRANSFER_PER_USE, room), ninjaData.getChakra());
        if (moved <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.battery.nochakra")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ninjaData.addChakra(-moved);
        setStoredChakra(stack, stored + moved);
        chime(player);
        player.displayClientMessage(Component.translatable("jutsu.battery.deposit",
                (int) moved, (int) getStoredChakra(stack), (int) CAPACITY).withStyle(ChatFormatting.AQUA), true);
    }

    /** Draws chakra back out of the battery, up to the transfer rate and the ninja's own maximum. */
    private void withdraw(ItemStack stack, Player player, INinjaData ninjaData) {
        float stored = getStoredChakra(stack);
        if (stored <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.battery.empty")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        float room = Math.max(ninjaData.getMaxChakra() - ninjaData.getChakra(), 0.0f);
        float moved = Math.min(Math.min(TRANSFER_PER_USE, stored), room);
        if (moved <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.battery.selfull")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ninjaData.addChakra(moved);
        setStoredChakra(stack, stored - moved);
        chime(player);
        player.displayClientMessage(Component.translatable("jutsu.battery.withdraw",
                (int) moved, (int) getStoredChakra(stack), (int) CAPACITY).withStyle(ChatFormatting.AQUA), true);
    }

    private void chime(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /**
     * Keeps a technique that is paid for tick by tick running on battery power.
     *
     * <p>Called by the upkeep of sustained techniques - toggles and channeled casts - just before
     * they check whether the caster can still pay. If the ninja's own pool has run down to the
     * reserve, a battery in their inventory is tapped for it silently, so a technique ends when the
     * batteries do rather than the moment the ninja's own chakra does. Nothing is taken out of the
     * pool that the technique would not have taken anyway: the battery ends up drained at roughly
     * the rate the technique costs.</p>
     *
     * <p>Only batteries in the ninja's own inventory count, and only ones that are theirs. A battery
     * nobody has claimed is claimed on the spot, the same way the first use of it would.</p>
     *
     * @return the amount of chakra actually moved out of the batteries, zero if none was
     */
    public static float fuelUpkeep(Player player, INinjaData ninjaData) {
        if (ninjaData.getChakra() >= UPKEEP_RESERVE) {
            return 0.0f;
        }
        float room = Math.max(ninjaData.getMaxChakra() - ninjaData.getChakra(), 0.0f);
        float wanted = Math.min(UPKEEP_TOP_UP, room);
        if (wanted <= 0.0f) {
            return 0.0f;
        }
        Inventory inventory = player.getInventory();
        float moved = 0.0f;
        for (int slot = 0; slot < inventory.getContainerSize() && moved < wanted; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof ChakraBatteryItem)) {
                continue;
            }
            if (!hasOwner(stack)) {
                bindOwner(stack, player);
            } else if (!isOwner(stack, player)) {
                continue;
            }
            float stored = getStoredChakra(stack);
            float take = Math.min(Math.min(stored, wanted - moved), room - moved);
            if (take <= 0.0f) {
                continue;
            }
            setStoredChakra(stack, stored - take);
            moved += take;
        }
        if (moved > 0.0f) {
            ninjaData.addChakra(moved);
        }
        return moved;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.narutomod.chakra_battery.charge",
                        (int) getStoredChakra(stack), (int) CAPACITY)
                .withStyle(ChatFormatting.AQUA));
        if (hasOwner(stack)) {
            tooltip.add(Component.translatable("item.narutomod.chakra_battery.owner", getOwnerName(stack))
                    .withStyle(ChatFormatting.DARK_AQUA));
        } else {
            tooltip.add(Component.translatable("item.narutomod.chakra_battery.unbound")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getStoredChakra(stack) > 0.0f;
    }

    // ------------------------------------------------------------------------------------------
    // Stack data
    // ------------------------------------------------------------------------------------------

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrCreateTag();
    }

    public static float getStoredChakra(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0.0f : tag.getFloat(CHAKRA_TAG);
    }

    public static void setStoredChakra(ItemStack stack, float chakra) {
        tag(stack).putFloat(CHAKRA_TAG, Math.min(Math.max(chakra, 0.0f), CAPACITY));
    }

    public static boolean hasOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(OWNER_TAG);
    }

    public static boolean isOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getString(OWNER_TAG).equals(player.getUUID().toString());
    }

    public static void bindOwner(ItemStack stack, Player player) {
        tag(stack).putString(OWNER_TAG, player.getUUID().toString());
        tag(stack).putString(OWNER_NAME_TAG, player.getName().getString());
    }

    /** @return the display name of the owner, or an empty string if the battery is not bound. */
    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return "";
        }
        if (tag.contains(OWNER_NAME_TAG)) {
            return tag.getString(OWNER_NAME_TAG);
        }
        // Batteries from before names were stored still have to show something.
        String uuid = tag.getString(OWNER_TAG);
        try {
            return UUID.fromString(uuid).toString();
        } catch (IllegalArgumentException ignored) {
            return uuid;
        }
    }
}
