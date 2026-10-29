package com.sekwah.narutomod.item;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * A scroll that does not teach a technique but trains the body: reading it grants a small permanent
 * bonus to one of the caster's attributes. They are found in the world rather than bought, so that
 * exploring keeps mattering even after every technique has been learned.
 */
public class StatScrollItem extends Item {

    public enum Stat {
        CHAKRA("chakra", 5.0f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addChakraBonus(amount);
                data.addChakra(amount);
            }
        },
        STAMINA("stamina", 5.0f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addStaminaBonus(amount);
                data.addStamina(amount);
            }
        },
        CHAKRA_REGEN("chakra_regen", 0.5f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addChakraRegenBonus(amount);
            }
        },
        STAMINA_REGEN("stamina_regen", 0.5f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addStaminaRegenBonus(amount);
            }
        },
        VITALITY("vitality", 2.0f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addVitalityBonus((int) amount);
                applyAttribute(player, data, Attributes.MAX_HEALTH,
                        "narutomod:stat_scroll_vitality",
                        UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012"),
                        data.getVitalityBonus());
            }
        },
        STRENGTH("strength", 1.0f) {
            @Override
            public void grant(Player player, INinjaData data) {
                data.addStrengthBonus((int) amount);
                applyAttribute(player, data, Attributes.ATTACK_DAMAGE,
                        "narutomod:stat_scroll_strength",
                        UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901234"),
                        data.getStrengthBonus());
            }
        };

        public final String id;
        public final float amount;

        Stat(String id, float amount) {
            this.id = id;
            this.amount = amount;
        }

        public abstract void grant(Player player, INinjaData data);

        /** Applies a permanent attribute bonus scaled by the total count of that scroll read. */
        protected static void applyAttribute(Player player, INinjaData data, net.minecraft.world.entity.ai.attributes.Attribute attr,
                                            String name, UUID uuid, int total) {
            StatScrollItem.applyAttribute(player, attr, name, uuid, total);
        }
    }

    private final Stat stat;

    public StatScrollItem(Properties properties, Stat stat) {
        super(properties);
        this.stat = stat;
    }

    /** Applies (or re-applies) a permanent attribute bonus, idempotently. */
    public static void applyAttribute(Player player,
                                     net.minecraft.world.entity.ai.attributes.Attribute attr,
                                     String name, UUID uuid, int total) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = player.getAttribute(attr);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        if (total > 0) {
            instance.addPermanentModifier(new AttributeModifier(uuid, name, total,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** Re-applies the permanent attribute bonuses, so they survive a respawn or a lost modifier. */
    public static void applyBonuses(Player player, INinjaData data) {
        applyAttribute(player, Attributes.MAX_HEALTH,
                "narutomod:stat_scroll_vitality",
                UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012"),
                data.getVitalityBonus());
        applyAttribute(player, Attributes.ATTACK_DAMAGE,
                "narutomod:stat_scroll_strength",
                UUID.fromString("c3d4e5f6-a7b8-9012-cdef-345678901234"),
                data.getStrengthBonus());
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
            stat.grant(player, ninjaData);
            player.displayClientMessage(Component.translatable("naruto.stat_scroll.read",
                    Component.translatable("item.narutomod.stat_scroll." + stat.id)
                            .withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GREEN), false);
            stack.shrink(1);
        });
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, java.util.List<Component> tooltip,
                               net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("naruto.stat_scroll.teaches",
                Component.translatable("item.narutomod.stat_scroll." + stat.id).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY));
    }
}
