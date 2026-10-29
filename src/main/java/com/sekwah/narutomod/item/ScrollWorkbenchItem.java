package com.sekwah.narutomod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * A field workbench, folded into a scroll.
 *
 * <p>Ninja travel light, and a crafting table is the one thing a camp always needs and never has.
 * The scroll is the table: opening it gives the ordinary three by three grid, so the recipes and the
 * recipe book keep working exactly as they do at home. It is deliberately not a portable furnace or
 * an anvil - one convenience is enough, and the rest of the camp still has to be built.</p>
 */
public class ScrollWorkbenchItem extends Item {

    public ScrollWorkbenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            BlockPos pos = player.blockPosition();
            // A real crafting table is not there, so the menu's own validity check and its result
            // computation (which both go through this access) have to be faked: the access hands
            // out the level and runs the callbacks instead of testing for a block that is not there.
            ContainerLevelAccess access = new ContainerLevelAccess() {
                @Override
                public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> function) {
                    return Optional.of(function.apply(level, pos));
                }

                @Override
                public void execute(BiConsumer<Level, BlockPos> consumer) {
                    consumer.accept(level, pos);
                }
            };
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, owner) -> new CraftingMenu(id, inventory, access) {
                        @Override
                        public boolean stillValid(Player p) {
                            return true;
                        }
                    },
                    Component.translatable("item.narutomod.scroll_workbench")));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
