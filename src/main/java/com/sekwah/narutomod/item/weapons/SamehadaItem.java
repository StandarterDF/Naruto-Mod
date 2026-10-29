package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.item.ComboSwordItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Samehada, the living blade: it eats chakra and it remembers being fed.
 *
 * <p>Whatever it cuts loses chakra, and the bigger the thing is the more there is to take - a
 * chicken barely moistens the blade, an armoured beast fills the caster's pool. A victim that has
 * no pool of its own (anything but a caster) simply has the chakra pulled out of its flesh, which
 * is why the amount follows its maximum health rather than anything it is carrying.</p>
 *
 * <p>Against a peaceful creature the blade is generous instead: it feeds its caster. The roll gets
 * easier the more the caster has let the blade eat, but that progress is deliberately slow - the
 * taste for it is mastered at half the pace of any technique. Once mastered the meal no longer
 * just fills the stomach, it settles it too.</p>
 *
 * <p>The feed amount never grows: mastery only makes the blade offer it more often.</p>
 */
public class SamehadaItem extends ComboSwordItem {

    /** Where the blade's own feeding mastery is filed. Not a technique, hence its own id. */
    public static final ResourceLocation FEEDING_MASTERY =
            new ResourceLocation(NarutoMod.MOD_ID, "samehada_feeding");

    /** Chakra drawn out of a victim per point of its maximum health. */
    private static final float CHAKRA_PER_HEALTH = 0.1f;

    /** Ceiling on a single bite, so one dragon cannot empty into the caster's whole pool. */
    private static final float MAX_CHAKRA_BITE = 6.0f;

    /** Chance of a peaceful hit feeding the caster before any mastery at all. */
    private static final float FEED_CHANCE_BASE = 0.2f;

    /** Hunger points one feed restores; mastery only changes how often this lands. */
    private static final int FEED_HUNGER = 2;

    /** Saturation modifier of one feed, the same units vanilla food uses. */
    private static final float FEED_SATURATION = 0.6f;

    /** Feeding is mastered at half speed, so a proc is worth half a cast. */
    private static final float FEED_CREDIT = 0.5f;

    /** Level from which a feed also fills the saturation bar, not just the hunger bar. */
    private static final float FULL_MASTERY = 0.98f;

    public SamehadaItem(Tier tier, int attackDamage, float attackSpeed, Properties properties,
                        float damagePerStep, int maxCombo, int comboWindow) {
        super(tier, attackDamage, attackSpeed, properties, damagePerStep, maxCombo, comboWindow,
                0.0f, 2);
    }

    /** Chakra this victim gives up to one hit of the blade. */
    public float chakraBite(LivingEntity victim) {
        return Math.min(MAX_CHAKRA_BITE, victim.getMaxHealth() * CHAKRA_PER_HEALTH);
    }

    /** How far the caster has come in getting the blade to feed them, from nothing to nearly one. */
    public float feedingLevel(INinjaData data) {
        return Mastery.level(data, FEEDING_MASTERY);
    }

    /** Chance of a peaceful hit feeding the caster at this level of mastery. */
    public float feedChance(float level) {
        return FEED_CHANCE_BASE + (1.0f - FEED_CHANCE_BASE) * level;
    }

    /**
     * Rolls the blade's appetite against a peaceful kill and, on a hit, feeds the caster.
     *
     * @return true if the caster was actually fed
     */
    public boolean feedCaster(Player caster, INinjaData data) {
        float level = feedingLevel(data);
        if (caster.getRandom().nextFloat() >= feedChance(level)) {
            return false;
        }
        FoodData food = caster.getFoodData();
        food.eat(FEED_HUNGER, FEED_SATURATION);
        if (level >= FULL_MASTERY) {
            // Fully settled: the blade fills the saturation bar to whatever the stomach now holds.
            food.setSaturation(food.getFoodLevel());
        }
        Mastery.recordCredit(caster, data, FEEDING_MASTERY, FEED_CREDIT);
        return true;
    }

    /**
     * Whether the creature counts as peaceful for the blade: a beast or a villager, not something
     * that came to kill the caster. The caster's own pets are excluded by the caller.
     */
    public static boolean isPeaceful(LivingEntity victim) {
        return victim instanceof Animal
                || victim instanceof WaterAnimal
                || victim instanceof AmbientCreature
                || victim instanceof AbstractVillager;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.samehada.drain")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.samehada.feed")
                .withStyle(ChatFormatting.GRAY));
    }
}
