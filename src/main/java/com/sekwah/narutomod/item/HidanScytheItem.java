package com.sekwah.narutomod.item;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class HidanScytheItem extends SwordItem {

    /** NBT key holding the kill count of the scythe. */
    public static final String KILLS_TAG = "narutomod:kills";

    /** Bonus damage per kill. */
    public static final float DAMAGE_PER_KILL = 0.05f;

    /** Stable id for the kill-bonus damage modifier. */
    private static final UUID KILL_BONUS_UUID =
            UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    /** Minimum Kenjutsu nodes to use the scythe's contract/effects. */
    public static final int MIN_KENJUTSU_FOR_USE = 5;

    /** Minimum Kenjutsu nodes to grow sharper with kills. */
    public static final int MIN_KENJUTSU_FOR_KILLS = 8;

    private static Set<ResourceLocation> kenjutsuNodes;

    public HidanScytheItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    /** Whether the player has enough Kenjutsu nodes to use the scythe's contract. */
    public boolean playerMeetsRequirement(Player player) {
        return kenjutsuCount(player) >= MIN_KENJUTSU_FOR_USE;
    }

    /** How many Kenjutsu branch nodes the player has unlocked. */
    public static int kenjutsuCount(Player player) {
        final int[] count = {0};
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            for (ResourceLocation nodeId : ninjaData.getSkillData().getUnlocked()) {
                if (kenjutsuNodeIds().contains(nodeId)) {
                    count[0]++;
                }
            }
        });
        return count[0];
    }

    private static Set<ResourceLocation> kenjutsuNodeIds() {
        Set<ResourceLocation> nodes = kenjutsuNodes;
        if (nodes == null) {
            nodes = new HashSet<>();
            for (Skill skill : Skill.all()) {
                if (skill.branch() == SkillBranch.KENJUTSU) {
                    nodes.add(skill.id());
                }
            }
            kenjutsuNodes = nodes;
        }
        return nodes;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof Player player && !player.level().isClientSide) {
            if (kenjutsuCount(player) >= MIN_KENJUTSU_FOR_USE) {
                player.getPersistentData().putUUID("narutomod:hidan_marked", target.getUUID());
                player.displayClientMessage(Component.translatable("naruto.hidan.marked").withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        if (kenjutsuCount(player) < MIN_KENJUTSU_FOR_USE) {
            player.displayClientMessage(Component.translatable("naruto.hidan.nomark").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        UUID markedId = player.getPersistentData().hasUUID("narutomod:hidan_marked")
                ? player.getPersistentData().getUUID("narutomod:hidan_marked") : null;
        if (markedId == null) {
            player.displayClientMessage(Component.translatable("naruto.hidan.nomark").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        LivingEntity target = serverLevel.getEntity(markedId) instanceof LivingEntity l ? l : null;
        if (target == null || !target.isAlive() || target.distanceTo(player) > 16.0d) {
            player.displayClientMessage(Component.translatable("naruto.hidan.nomark").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        float selfDamage = 4.0f;
        player.hurt(player.damageSources().magic(), selfDamage);
        target.hurt(player.damageSources().magic(), selfDamage);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(), 8, 0.3d, 0.3d, 0.3d, 0.0d);
        return InteractionResultHolder.consume(stack);
    }

    /** @return how many kills this scythe has taken. */
    public static int getKills(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    /** Records one more kill on the scythe. */
    public static void addKill(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KILLS_TAG, getKills(stack) + 1);
    }

    /** Bonus damage this scythe currently deals from its kill count. */
    public float killBonus(ItemStack stack) {
        return Math.round(getKills(stack) * DAMAGE_PER_KILL * 100.0f) / 100.0f;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        float bonus = killBonus(stack);
        if (bonus <= 0.0f) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(KILL_BONUS_UUID,
                "Hidan scythe kills", bonus, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.hidan_scythe.contract").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("item.narutomod.combo_sword.kenjutsu_requirement",
                Component.literal(String.valueOf(MIN_KENJUTSU_FOR_USE)).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.DARK_GRAY));
        int kills = getKills(stack);
        tooltip.add(Component.translatable("item.narutomod.combo_sword.kills",
                Component.literal(String.valueOf(kills)).withStyle(ChatFormatting.GOLD),
                Component.literal(String.valueOf(Math.round(kills * DAMAGE_PER_KILL * 100.0f) / 100.0f)).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
    }
}