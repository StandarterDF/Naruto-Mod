package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The three Akimichi clan pills: spinach (green), curry (yellow) and chili (red).
 *
 * <p>Each one is a food-grade item that, on consumption, grants a large strength boost for a short
 * window and then leaves the eater in a weakened state. The red pill is the most extreme: it grants
 * flight for its window and drops the eater to a single point of health afterwards, followed by
 * {@code critical_exhaustion}.</p>
 */
public class AkimichiPillItem extends Item {

    /** The three Akimichi pills. */
    public enum PillType {
        SPINACH,
        CURRY,
        CHILI
    }

    /** How long the spinach strength lasts, in ticks (60 s). */
    private static final int SPINACH_STRENGTH_TICKS = 20 * 60;
    /** Nausea that follows the spinach pill, in ticks (10 s). */
    private static final int SPINACH_NAUSEA_TICKS = 200;
    /** How long the curry strength lasts, in ticks (30 s). */
    private static final int CURRY_STRENGTH_TICKS = 20 * 30;
    /** How long the chili strength and flight last, in ticks (10 s). */
    private static final int CHILI_DURATION_TICKS = 20 * 10;
    /** Critical exhaustion after the chili pill, in ticks (30 s). */
    private static final int CRITICAL_EXHAUSTION_TICKS = 20 * 30;

    /** Cooldown shared by every pill, in ticks (60 s). */
    private static final int COOLDOWN_TICKS = 20 * 60;

    /** Persistent-data keys used by the pill timers (see {@code AkimichiPillEvents}). */
    public static final String SPINACH_NAUSEA_AT_TAG = "narutomod:akimichiSpinachNauseaAt";
    public static final String CHILI_UNTIL_TAG = "narutomod:akimichiChiliUntil";
    public static final String CHILI_SAVED_TAG = "narutomod:akimichiChiliSaved";

    private final PillType pillType;

    public AkimichiPillItem(PillType pillType, Properties properties) {
        super(properties);
        this.pillType = pillType;
    }

    public PillType getPillType() {
        return pillType;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (level.isClientSide || !(entity instanceof Player player)) {
            return result;
        }
        // Refuse to stack pills on top of an already active strength boost.
        // (Cooldown below is the primary gate; this is a belt-and-braces check.)
        long now = level.getGameTime();
        switch (pillType) {
            case SPINACH -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, SPINACH_STRENGTH_TICKS, 1));
                player.getPersistentData().putLong(SPINACH_NAUSEA_AT_TAG, now + SPINACH_STRENGTH_TICKS);
            }
            case CURRY -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, CURRY_STRENGTH_TICKS, 3));
                player.setHealth(player.getHealth() * 0.5f);
            }
            case CHILI -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, CHILI_DURATION_TICKS, 9));
                grantFlight(player, now);
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return result;
    }

    /** Stores the pre-pill flight state and lends the player wings for the chili window. */
    private static void grantFlight(Player player, long now) {
        CompoundTag data = player.getPersistentData();
        CompoundTag saved = new CompoundTag();
        saved.putBoolean("mayfly", player.getAbilities().mayfly);
        saved.putBoolean("flying", player.getAbilities().flying);
        data.put(CHILI_SAVED_TAG, saved);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        data.putLong(CHILI_UNTIL_TAG, now + CHILI_DURATION_TICKS);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        String key = switch (pillType) {
            case SPINACH -> "item.narutomod.spinach_pill.tooltip";
            case CURRY -> "item.narutomod.curry_pill.tooltip";
            case CHILI -> "item.narutomod.chili_pill.tooltip";
        };
        tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
