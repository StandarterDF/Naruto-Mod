package com.sekwah.narutomod.item;

import net.minecraft.world.item.Item;

/**
 * A ninja's field ration: pressed beef and kelp between two slices of bread, wrapped to survive a
 * week in a pouch.
 *
 * <p>It is the meal a ninja eats on the road rather than at a table - no bowl, no chopsticks, no
 * buffs - and it is worth about a cooked steak, a little more filling. The point of it is that nine
 * of them stack: a mission can be provisioned without a chest full of ramen.</p>
 *
 * <p>The properties (nutrition, saturation and stack size) are set where the item is registered, in
 * {@code NarutoItems}, so this class stays a plain food with no behaviour of its own.</p>
 */
public class NinjaRationItem extends Item {

    public NinjaRationItem(Properties properties) {
        super(properties);
    }
}
