package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.TravellingArcEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * A crescent of wind thrown off the edge of the blade - and it carries.
 *
 * <p>Every sword in the mod is a short weapon, and the answer to a spear, a bow or simply a body
 * that will not close the gap is to cut the air itself. The caster draws the edge across in front
 * of them and the cut travels: a crescent that crosses the ground ahead, widening a little as it
 * goes, opening everything the front passes through, and the wound keeps bleeding after the blade
 * has moved on.</p>
 *
 * <p>A quarter of the cut is not the edge at all but the vacuum behind it, which no plate stops.
 * The technique needs the sword as much as it needs the arm; without one there is no edge to draw
 * the air from. The flight itself lives in {@code TravellingArcEntity}: this class only pays for
 * the draw and lets it go.</p>
 */
public class VacuumBladeAbility extends Ability implements Ability.Cooldown {

    /** A cut of this length is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** 1 3 2 3 1 2 - the kenjutsu guard seal with the cut added. */
    public static final long COMBO = 123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "vacuum_blade");

    /** How far the crescent carries, and the full width of the cone it opens. */
    private static final double RANGE = 12.0d;
    private static final double CONE_DEGREES = 30.0d;

    /** Cosine of half the cone: a body is inside when the angle to it is within fifteen degrees. */
    private static final double CONE_DOT = Math.cos(Math.toRadians(CONE_DEGREES / 2.0d));

    /** What the cut costs, and how long the arm needs before it can be drawn again. */
    private static final float COST = 25.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 160;

    /** The cut itself: a quarter of it is vacuum, which passes through armour. */
    private static final float DAMAGE = 6.0f;
    private static final float MAGIC_SHARE = 0.25f;

    /**
     * The wooden sword's share of the edge: three points of attack on the stack. A wooden blade
     * throws the cut as written; anything sharper throws it harder, in proportion.
     */
    private static final float WOOD_REFERENCE = 3.0f;

    /** What each level of Sharpness on the blade adds to its share, in the same points. */
    private static final float SHARPNESS_PER_LEVEL = 0.5f;

    /** How long the wound keeps bleeding, on the same mark the bone blade leaves. */
    private static final int BLEED_TICKS = 60;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.vacuum_blade.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        TravellingArcEntity wave = new TravellingArcEntity(level, player, RANGE, CONE_DOT,
                DAMAGE * swordPower(player) / WOOD_REFERENCE,
                TravellingArcEntity.KIND_VACUUM, false, BLEED_TICKS, MAGIC_SHARE);
        level.addFreshEntity(wave);
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /**
     * How much edge the held blade lends the cut: its attack attribute in the hand, plus a share
     * per level of Sharpness. Read off the stack, so any sword works - vanilla tiers, the mod's own
     * blades, and whatever another mod adds. A wooden blade reads 3 and throws the cut as written;
     * diamond reads 6 and throws it twice as hard.
     */
    private static float swordPower(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SwordItem)) {
            return WOOD_REFERENCE;
        }
        double attack = 0.0d;
        for (AttributeModifier modifier : held.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            attack += modifier.getAmount();
        }
        attack += SHARPNESS_PER_LEVEL
                * EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, held);
        return (float) Math.max(1.0d, attack);
    }
}
