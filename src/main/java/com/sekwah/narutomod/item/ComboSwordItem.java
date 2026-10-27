package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One of the great swords: a heavy blade that grows heavier the longer a fight goes on.
 *
 * <p>Every hit landed in quick succession after the last one adds a step to the swing, and the
 * damage of the next blow grows with it. Let the chain run out and the sword drops back to its
 * starting weight, so it rewards staying on a target rather than trading a blow and backing off.</p>
 *
 * <p>The chain lives on the stack itself, so two of the same sword in a hotbar do not share it.</p>
 *
 * <p>The damage is added by {@code ComboSwordEvents}, not here: swing damage is worked out before
 * the hit lands, which is too early to look at a chain that this hit has not joined yet.</p>
 */
public class ComboSwordItem extends SwordItem {

    private static final String COMBO_TAG = "narutomod:combo";
    private static final String LAST_HIT_TAG = "narutomod:combo_time";

    /** Damage added per step of the chain. */
    private final float damagePerStep;

    /** How many steps the chain can hold. */
    private final int maxCombo;

    /** How long, in ticks, the chain is kept after a hit before it drops back to nothing. */
    private final int comboWindow;

    /** Chakra drawn out of the target by every hit, for the blades that are supposed to do that. */
    private final float chakraPerHit;

    public ComboSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties,
                          float damagePerStep, int maxCombo, int comboWindow, float chakraPerHit) {
        super(tier, attackDamage, attackSpeed, properties);
        this.damagePerStep = damagePerStep;
        this.maxCombo = maxCombo;
        this.comboWindow = comboWindow;
        this.chakraPerHit = chakraPerHit;
    }

    public float getDamagePerStep() {
        return this.damagePerStep;
    }

    public int getMaxCombo() {
        return this.maxCombo;
    }

    public int getComboWindow() {
        return this.comboWindow;
    }

    public float getChakraPerHit() {
        return this.chakraPerHit;
    }

    /**
     * The chain as it stands, or nothing at all if the last hit was too long ago.
     */
    public int getCombo(ItemStack stack, long gameTime) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(COMBO_TAG) || !tag.contains(LAST_HIT_TAG)) {
            return 0;
        }
        if (gameTime - tag.getLong(LAST_HIT_TAG) > this.comboWindow) {
            return 0;
        }
        return tag.getInt(COMBO_TAG);
    }

    /**
     * Adds a step to the chain and starts the window over. Returns the chain this hit joins.
     */
    public int extendCombo(ItemStack stack, long gameTime) {
        int combo = Math.min(getCombo(stack, gameTime) + 1, this.maxCombo);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(COMBO_TAG, combo);
        tag.putLong(LAST_HIT_TAG, gameTime);
        return combo;
    }

    /**
     * Damage this many steps of the chain is worth.
     */
    public float getBonusDamage(int combo) {
        return this.damagePerStep * combo;
    }

    /**
     * Right-click raises the blade guard. Every one of the great swords is heavy enough to turn a
     * blow; the move is the same one the tree teaches, so a sword is a shield as well as a weapon.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide) {
            com.sekwah.narutomod.abilities.jutsus.ParryAbility.raiseGuard(player);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        tooltip.add(Component.translatable("item.narutomod.combo_sword.chain",
                Component.literal(String.valueOf(this.maxCombo)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.combo_sword.growth",
                Component.literal(String.valueOf(this.damagePerStep)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
        if (this.chakraPerHit > 0.0f) {
            tooltip.add(Component.translatable("item.narutomod.combo_sword.drain",
                    Component.literal(String.valueOf(this.chakraPerHit)).withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY));
        }

        int combo = stack.getTag() == null ? 0 : stack.getTag().getInt(COMBO_TAG);
        if (combo > 0) {
            tooltip.add(Component.translatable("item.narutomod.combo_sword.current",
                    Component.literal(String.valueOf(combo)).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
