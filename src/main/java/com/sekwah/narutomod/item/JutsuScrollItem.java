package com.sekwah.narutomod.item;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.triggers.NarutoAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A scroll that teaches one technique.
 *
 * <p>The deep techniques are not bought with points alone: their node in the skill tree also asks
 * for the matching scroll (see {@code Skill#requiresScroll()}), and the scroll only counts once it
 * has been found and read. Reading it writes the technique into the caster's scroll knowledge, which
 * survives death and logout; a second copy of the same scroll is not wasted, it is spent on mastery
 * of the technique instead.</p>
 */
public class JutsuScrollItem extends Item {

    /** How much mastery one duplicate scroll is worth, in cast-equivalents. */
    private static final float DUPLICATE_CREDIT = 2.0f;

    /** The technique this scroll knows, as a registry id such as {@code narutomod:rasengan}. */
    private final ResourceLocation knowledge;

    public JutsuScrollItem(Properties properties, ResourceLocation knowledge) {
        super(properties);
        this.knowledge = knowledge;
    }

    public ResourceLocation knowledge() {
        return this.knowledge;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isNinjaModeEnabled()) {
                player.displayClientMessage(Component.translatable("jutsu.not_a_ninja")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            boolean firstRead = ninjaData.getSkillData().learnScroll(this.knowledge);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                NarutoAdvancements.onScrollRead(serverPlayer, this.knowledge);
            }
            if (firstRead) {
                player.displayClientMessage(Component.translatable("naruto.scroll.read",
                        Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                Ability ability = NarutoRegistries.ABILITIES.getValue(this.knowledge);
                if (ability != null) {
                    Mastery.recordCredit(player, ninjaData, ability, DUPLICATE_CREDIT);
                }
                player.displayClientMessage(Component.translatable("naruto.scroll.duplicate",
                        Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.AQUA), false);
            }
            stack.shrink(1);
        });
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("naruto.scroll.teaches",
                Component.translatable(this.knowledge.toString()).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("naruto.scroll.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
