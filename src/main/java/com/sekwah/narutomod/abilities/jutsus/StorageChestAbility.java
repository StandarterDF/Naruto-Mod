package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.menu.SupplyChestContainer;
import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;

/**
 * Opens the caster's supply chest, the one place their worker clones bring a harvest.
 *
 * <p>The chest is not a block - there is only ever one of it and its slots live in the caster's own
 * data, see {@code INinjaData#getSupplyChest}. That is exactly what lets a worker drop something
 * into it from anywhere, with no chunk to keep loaded; this technique is simply how the caster looks
 * inside. It costs nothing, since it is a look rather than a cast.</p>
 *
 * <p>The chest grows with its contents, so the size is written along with the request: the client
 * builds a window that matches the grid the caster is about to see rather than guessing a size.</p>
 */
public class StorageChestAbility extends Ability {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** B V C - kept short, since it is opened often. */
    public static final long COMBO = 321L;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (player instanceof ServerPlayer serverPlayer) {
            SupplyChestContainer chest = ninjaData.getSupplyChest();
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                            (id, inventory, p) -> new SupplyChestMenu(id, inventory, chest),
                            Component.translatable("narutomod:storage_chest")),
                    buffer -> buffer.writeVarInt(chest.getContainerSize()));
        }
    }

    @Override
    public boolean logInChat() {
        return false;
    }
}
