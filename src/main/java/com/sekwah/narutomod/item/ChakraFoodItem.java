package com.sekwah.narutomod.item;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A proper ninja meal: food that also puts something back into the pools the body runs on.
 *
 * <p>Vanilla food restores hunger and saturation, and that is all it does here too - the effects on
 * these dishes are ordinary vanilla {@code FoodProperties}. The extra is paid on the last bite: a
 * flat amount of chakra and stamina, which is why the food is worth preparing before a mission
 * rather than eating whatever is at hand. Amounts are small on purpose; food is a preparation, not
 * a replacement for charging.</p>
 *
 * <p>Any food listed in the {@code narutomod:chakra_food} tag also gives a little chakra even if it
 * is not one of these dishes, so a datapack can add its own cooking without touching code.</p>
 */
public class ChakraFoodItem extends Item {

    private final float chakra;
    private final float stamina;

    public ChakraFoodItem(Properties properties, float chakra, float stamina) {
        super(properties);
        this.chakra = chakra;
        this.stamina = stamina;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        ItemStack result = super.finishUsingItem(stack, level, living);
        if (level.isClientSide || !(living instanceof Player player)) {
            return result;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isNinjaModeEnabled()) {
                return;
            }
            if (this.chakra != 0.0f) {
                ninjaData.addChakra(this.chakra);
            }
            if (this.stamina != 0.0f) {
                ninjaData.addStamina(this.stamina);
            }
        });
        return result;
    }
}
