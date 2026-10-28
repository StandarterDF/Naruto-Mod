package com.sekwah.narutomod.item.weapons;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bone multitool grown by the Bone Tools technique: one implement that digs like a pickaxe,
 * an axe and a shovel, fights like a sword, and tills, strips and flattens on right-click like a
 * hoe and an axe.
 *
 * <p>Left-click breaks whatever is in front of it at stone-tier-plus speed; right-click on a block
 * first tries the hoe actions (till, then flatten path) and then the axe action (strip), the same
 * order a ninja would reach for them in the field. Like the bone blade it is not a durable item:
 * it carries a number of uses in its own tag and crumbles on the last one, so it stays a
 * technique rather than a free tool to keep and upgrade.</p>
 */
public class BoneMultitoolItem extends DiggerItem {

    /** NBT key holding the uses left in this particular tool. */
    public static final String USES_TAG = "narutomod:bone_tool_uses";

    /** NBT key holding the grown tier, 0 (wood-like) to 4 (netherite-like). */
    public static final String TIER_TAG = "narutomod:bone_tool_tier";

    /** Dig speeds per tier, matching wood through netherite - and past it at the top. */
    private static final float[] SPEEDS = {2.0f, 4.0f, 6.0f, 8.0f, 12.0f};

    /**
     * What the tool adds to the wielder's own fist, per tier: a fresh bone tool is barely a club,
     * the top tier matches a netherite sword exactly (1 base + 7 = 8). The swing is slower than a
     * sword's, so even at the top it is a workman's answer rather than a replacement for a blade -
     * that is what the Camellia Dance is for.
     */
    private static final float[] ATTACK_DAMAGES = {0.0f, 2.0f, 3.5f, 5.5f, 7.0f};

    /** How fast the tool swings: slower than a sword, it is first of all a tool. */
    private static final float SWING_SPEED = -2.8f;

    /** Uses per tier: a practised hand grows a tool that lasts. */
    private static final int[] USES = {32, 48, 64, 96, 256};

    /** How many uses one grown tool is worth, before tier is known. */
    public static final int MAX_USES = 64;

    /** What the tool digs at: stone tier plus a little bone sharpness. */
    private static final float DIG_SPEED = 7.0f;

    /** @return the grown tier of this tool, 0 (wood-like) to 4 (netherite-like). */
    public static int tierOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TIER_TAG)) {
            return 1;
        }
        return Math.max(0, Math.min(SPEEDS.length - 1, tag.getInt(TIER_TAG)));
    }

    /** @return the dig speed of a tier. */
    public static float speedFor(int tier) {
        return SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, tier))];
    }

    /** @return the attack bonus of a tier, on top of the wielder's own fist. */
    public static float attackFor(int tier) {
        return ATTACK_DAMAGES[Math.max(0, Math.min(ATTACK_DAMAGES.length - 1, tier))];
    }

    /** @return the full life of a tier. */
    public static int maxUsesFor(int tier) {
        return USES[Math.max(0, Math.min(USES.length - 1, tier))];
    }

    public BoneMultitoolItem(Tier tier) {
        // The damage the constructor is given is only the fallback: the real value lives in the
        // stack's grown tier, see getAttributeModifiers below.
        super(0.0f, SWING_SPEED, tier, BlockTags.MINEABLE_WITH_PICKAXE, new Properties().stacksTo(1));
    }

    /**
     * The tool hits as hard as its grown tier, not as hard as its item type: a wood-like multitool
     * is nearly harmless, and only the top tier, grown at 90% mastery, is a netherite sword's equal.
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return super.getAttributeModifiers(slot, stack);
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID,
                "Bone tool damage", attackFor(tierOf(stack)), AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID,
                "Bone tool speed", SWING_SPEED, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.SWORD_EFFICIENT)) {
            return speedFor(tierOf(stack));
        }
        return super.getDestroySpeed(stack, state);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.NEEDS_STONE_TOOL);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action)
                || ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action)
                || ToolActions.DEFAULT_HOE_ACTIONS.contains(action)
                || ToolActions.DEFAULT_SWORD_ACTIONS.contains(action);
    }

    /**
     * Right-click on a block: till the soil, flatten a path, or strip a log - whichever the block
     * answers to first, like reaching for the hoe and then the axe.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockState clicked = level.getBlockState(pos);
        BlockState transformed = null;
        if (player != null && !player.isShiftKeyDown()) {
            transformed = clicked.getToolModifiedState(context, ToolActions.HOE_TILL, false);
        }
        if (transformed == null || transformed == clicked) {
            transformed = clicked.getToolModifiedState(context, ToolActions.SHOVEL_FLATTEN, false);
        }
        if (transformed == null || transformed == clicked) {
            transformed = clicked.getToolModifiedState(context, ToolActions.AXE_STRIP, false);
        }
        if (transformed == null || transformed == clicked) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, transformed, 11);
            level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            spendUse(stack, player);
            if (player != null) {
                practice(player, 0.5f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide) {
            spendUse(stack, attacker instanceof Player player ? player : null);
            if (attacker instanceof Player player) {
                practice(player, 0.5f);
            }
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && this.getDestroySpeed(stack, state) > 1.0f) {
            spendUse(stack, miner instanceof Player player ? player : null);
            if (miner instanceof Player player) {
                // Most of the practice comes from the work, not the cast: a cast is rare once the
                // tool lasts, so each broken block counts as a full cast-equivalent.
                practice(player, 1.0f);
            }
        }
        return true;
    }

    /**
     * Credits the Bone Tools technique for work done with the tool it grew, in cast-equivalents.
     * Server side only; mastery itself decides whether it counts.
     */
    private static void practice(Player player, float credit) {
        player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .ifPresent(ninjaData -> com.sekwah.narutomod.abilities.mastery.Mastery.recordCredit(
                        player, ninjaData, com.sekwah.narutomod.abilities.NarutoAbilities.BONE_TOOLS.get(), credit));
    }

    /** Spends one use, crumbling the tool on the last one. */
    private static void spendUse(ItemStack stack, @Nullable Player player) {
        int left = remaining(stack) - 1;
        if (left <= 0) {
            stack.shrink(1);
            if (player != null) {
                player.displayClientMessage(Component.translatable("item.narutomod.bone_multitool.broken")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        stack.getOrCreateTag().putInt(USES_TAG, left);
    }

    /** @return how many uses are left before the bone gives out. */
    public static int remaining(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(USES_TAG) ? tag.getInt(USES_TAG) : maxUsesFor(tierOf(stack));
    }

    /**
     * The tool has no durability at all: the uses in the tag are the only wear it knows, so there
     * is no bar to repair and no anvil that will touch it.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return 0;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** A fresh tool starts with its full, short life. */
    public static ItemStack create() {
        return create(1);
    }

    /** A fresh tool of the given tier starts with that tier's full life. */
    public static ItemStack create(int tier) {
        tier = Math.max(0, Math.min(SPEEDS.length - 1, tier));
        ItemStack stack = new ItemStack(com.sekwah.narutomod.item.NarutoItems.BONE_MULTITOOL.get());
        stack.getOrCreateTag().putInt(TIER_TAG, tier);
        stack.getOrCreateTag().putInt(USES_TAG, maxUsesFor(tier));
        return stack;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    /**
     * The uses are shown as a vanilla durability bar even though there is no durability to repair:
     * green while fresh, red when nearly gone, like any tool.
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return remaining(stack) < maxUsesFor(tierOf(stack));
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float fraction = (float) remaining(stack) / (float) maxUsesFor(tierOf(stack));
        return Math.round(13.0f * Math.max(0.0f, Math.min(1.0f, fraction)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fraction = (float) remaining(stack) / (float) maxUsesFor(tierOf(stack));
        return net.minecraft.util.Mth.hsvToRgb(Math.max(0.0f, Math.min(1.0f, fraction)) / 3.0f, 1.0f, 1.0f);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.bone_multitool.tier",
                Component.translatable("item.narutomod.bone_multitool.tier" + tierOf(stack))
                        .withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.bone_multitool.uses",
                Component.literal(String.valueOf(remaining(stack))).withStyle(ChatFormatting.YELLOW),
                Component.literal(String.valueOf(maxUsesFor(tierOf(stack)))).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.bone_multitool.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }
}
