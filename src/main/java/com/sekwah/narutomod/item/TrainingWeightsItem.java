package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A pair of weighted bands worn on the wrists, or slung over the shoulders when they are not being
 * worn: the oldest training tool a taijutsu student is given and the last one they give up.
 *
 * <p>It is carried rather than equipped. A weight that sits in the pack while the ninja runs, climbs
 * and fights makes every step and every technique a little more expensive, and that is the training:
 * there is no slot to remember and nothing to take off before a technique, only a pack that is
 * heavier than it was. Two of them are the limit - a body can only be dragged down so far before the
 * training stops being training - and each one costs a twentieth of the ninja's speed and a tenth
 * again of their stamina.</p>
 *
 * <p>The item itself only answers questions about itself: whether this player is carrying one, how
 * many, and by how much they are slowed and strained. The slowdown is applied through
 * {@link #applySlowdown(Player)}, which keeps a single attribute modifier up to date and removes it
 * again once the weights are gone; the stamina side is read through {@link #staminaMultiplier(Player)}
 * by whatever spends stamina. Neither is written to the save file - a transient attribute modifier
 * and a value read fresh every hit cannot outlive the weights in the pack.</p>
 */
public class TrainingWeightsItem extends Item {

    /** A twentieth of the ninja's speed per weight carried. */
    public static final double SLOW_PER_WEIGHT = 0.05d;

    /** A tenth again of every stamina cost per weight carried. */
    public static final float STAMINA_EXTRA_PER_WEIGHT = 0.10f;

    /** How many weights the body can be dragged down by; the item stacks to exactly this. */
    public static final int MAX_WEIGHTS = 2;

    /** Stable id, so the single speed modifier is updated rather than stacked. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("5f0c9a2e-3b71-4d68-9c14-8a2e7d5b0c93");

    private static final String SPEED_MODIFIER_NAME = "narutomod:training_weights";

    public TrainingWeightsItem(Properties properties) {
        super(properties);
    }

    /**
     * @return how many weights this player is carrying, capped at {@link #MAX_WEIGHTS}; anything in
     *         the inventory counts, worn or not, because the item is carried rather than equipped
     */
    public static int carried(Player player) {
        Inventory inventory = player.getInventory();
        int carried = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof TrainingWeightsItem) {
                carried += stack.getCount();
                if (carried >= MAX_WEIGHTS) {
                    return MAX_WEIGHTS;
                }
            }
        }
        return carried;
    }

    /** @return whether this player is training under weight at all, asked before the stamina maths. */
    public static boolean isCarrying(Player player) {
        return carried(player) > 0;
    }

    /** @return the player's speed multiplier, one when the pack is light and lower under the weights. */
    public static double movementMultiplier(Player player) {
        return 1.0d - SLOW_PER_WEIGHT * carried(player);
    }

    /** @return what a stamina cost has to be multiplied by, one when the pack is light. */
    public static float staminaMultiplier(Player player) {
        return 1.0f + STAMINA_EXTRA_PER_WEIGHT * carried(player);
    }

    /**
     * Keeps the weight's drag on the player's speed up to date. Idempotent and cheap: call it every
     * server tick and it adds the modifier once, changes it when a weight is added or dropped, and
     * takes it off again the moment the pack is empty, so no stale drag can survive a death or a relog.
     */
    public static void applySlowdown(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int carried = carried(player);
        AttributeModifier existing = speed.getModifier(SPEED_MODIFIER_ID);
        if (carried <= 0) {
            if (existing != null) {
                speed.removeModifier(SPEED_MODIFIER_ID);
            }
            return;
        }
        double amount = -SLOW_PER_WEIGHT * carried;
        if (existing != null && Math.abs(existing.getAmount() - amount) < 1.0e-4d) {
            return;
        }
        if (existing != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME,
                amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        // The tooltip speaks for the stack in the hand: two weights read as the pair they are.
        int weights = Math.min(Math.max(stack.getCount(), 1), MAX_WEIGHTS);
        tooltip.add(Component.translatable("item.narutomod.training_weights.slow",
                percent(SLOW_PER_WEIGHT * weights)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.training_weights.stamina",
                percent(STAMINA_EXTRA_PER_WEIGHT * weights)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.training_weights.hint", MAX_WEIGHTS)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Turns a fraction into the whole percentage the tooltip shows, rounded rather than truncated. */
    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100.0d);
    }
}
