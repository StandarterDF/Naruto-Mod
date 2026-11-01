package com.sekwah.narutomod.item;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.triggers.NarutoAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A scroll that teaches one technique.
 *
 * <p>The deep techniques are not bought with points alone: their node in the skill tree also asks
 * for the matching scroll (see {@code Skill#requiresScroll()}), and the scroll only counts once it
 * has been found and read. Reading it writes the technique into the caster's scroll knowledge, which
 * survives death and logout; a second copy of the same scroll is not wasted, it is spent on mastery
 * of the technique instead.</p>
 *
 * <p>Two things refine that basic bargain. A scroll can name a {@link #base() base} - the eye or the
 * stance the art grows out of - and it simply refuses to be read by a caster who has not learned
 * that base yet, so a Sharingan secret is not burnt by somebody who has no Sharingan (nothing is
 * spent, the scroll stays in hand). And a scroll can carry a {@link #pool() pool} of related
 * techniques: the first copy teaches its own technique, while each further copy reveals one of the
 * still-unknown techniques of the pool, and only once the whole pool is known does the extra copy
 * fall back to mastery.</p>
 */
public class JutsuScrollItem extends Item {

    /** How much mastery one duplicate scroll is worth, in cast-equivalents. */
    private static final float DUPLICATE_CREDIT = 2.0f;

    /** The technique this scroll knows, as a registry id such as {@code narutomod:rasengan}. */
    private final ResourceLocation knowledge;

    /** The technique that has to be learned before this scroll can be read, or null for none. */
    @Nullable
    private final ResourceLocation base;

    /** Techniques a duplicate of this scroll can reveal, in no particular order. */
    private final List<ResourceLocation> pool;

    public JutsuScrollItem(Properties properties, ResourceLocation knowledge) {
        this(properties, knowledge, null, List.of());
    }

    public JutsuScrollItem(Properties properties, ResourceLocation knowledge,
                           @Nullable ResourceLocation base, List<ResourceLocation> pool) {
        super(properties);
        this.knowledge = knowledge;
        this.base = base;
        this.pool = List.copyOf(pool);
    }

    public ResourceLocation knowledge() {
        return this.knowledge;
    }

    /** The technique a caster must have learned before reading, or null when the scroll stands alone. */
    @Nullable
    public ResourceLocation base() {
        return this.base;
    }

    /** Techniques a duplicate of this scroll may reveal; empty when a duplicate only gives mastery. */
    public List<ResourceLocation> pool() {
        return this.pool;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return InteractionResultHolder.pass(stack);
        }
        if (!ninjaData.isNinjaModeEnabled()) {
            player.displayClientMessage(Component.translatable("jutsu.not_a_ninja")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.consume(stack);
        }
        // The base is checked with the same gate the technique itself uses, so the scroll, the tree
        // and the jutsu menu never disagree about what an unopened eye may do. Nothing is consumed:
        // the scroll is kept for the day the caster grows into it.
        if (this.base != null && !AbilityUnlocks.isUnlocked(ninjaData, this.base)) {
            player.displayClientMessage(Component.translatable("naruto.scroll.base_required",
                    Component.translatable(this.base.toString()).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.RED), false);
            return InteractionResultHolder.fail(stack);
        }
        boolean firstRead = ninjaData.getSkillData().learnScroll(this.knowledge);
        if (player instanceof ServerPlayer serverPlayer) {
            NarutoAdvancements.onScrollRead(serverPlayer, this.knowledge);
        }
        if (firstRead) {
            player.displayClientMessage(Component.translatable("naruto.scroll.read",
                    Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            ResourceLocation revealed = this.revealFromPool(ninjaData, player.getRandom());
            if (revealed != null) {
                if (player instanceof ServerPlayer serverPlayer) {
                    NarutoAdvancements.onScrollRead(serverPlayer, revealed);
                }
                player.displayClientMessage(Component.translatable("naruto.scroll.pool_unlocked",
                        Component.translatable(revealed.toString()).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            } else {
                Ability ability = NarutoRegistries.ABILITIES.getValue(this.knowledge);
                if (ability != null) {
                    Mastery.recordCredit(player, ninjaData, ability, DUPLICATE_CREDIT);
                }
                player.displayClientMessage(Component.translatable("naruto.scroll.duplicate",
                        Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.AQUA), false);
            }
        }
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Learns one still-unknown technique of the pool and returns it, or null when the pool is empty
     * or every technique in it is already known.
     *
     * <p>Only the scroll set is consulted: a pool entry is a technique of the same discipline, and
     * what a caster still has to pay for it is the tree node, not another scroll.</p>
     */
    @Nullable
    private ResourceLocation revealFromPool(INinjaData ninjaData, RandomSource random) {
        if (this.pool.isEmpty()) {
            return null;
        }
        List<ResourceLocation> unknown = new ArrayList<>(this.pool.size());
        for (ResourceLocation candidate : this.pool) {
            if (!ninjaData.getSkillData().hasScroll(candidate)) {
                unknown.add(candidate);
            }
        }
        if (unknown.isEmpty()) {
            return null;
        }
        ResourceLocation revealed = unknown.get(random.nextInt(unknown.size()));
        ninjaData.getSkillData().learnScroll(revealed);
        return revealed;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("naruto.scroll.teaches",
                Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        if (this.base != null) {
            tooltip.add(Component.translatable("naruto.scroll.requires_base",
                    Component.translatable(this.base.toString()).withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.DARK_RED));
        }
        tooltip.add(Component.translatable("naruto.scroll.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
