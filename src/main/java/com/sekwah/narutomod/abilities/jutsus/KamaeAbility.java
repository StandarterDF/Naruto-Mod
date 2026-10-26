package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;

import java.util.UUID;

/**
 * The three guards of the sword: high, middle and low.
 *
 * <p>Every stance a swordsman takes is a trade. Held high, the blade comes down on whatever is in
 * front of it and the shoulders are open; held middle, the guard covers the body and the cuts have to
 * be worked for; held low, the legs are free and the body is out of the way of everything that is not
 * aimed at it. The technique keeps all three rather than teaching one, and steps on to the next each
 * time the blade is raised, so a fight that changes shape can be answered without stopping to
 * think.</p>
 *
 * <p>The stance is not a spell that is thrown: it is held while the sword is in the hand, paid for
 * out of stamina every tick, and given up the moment the blade is sheathed. Its gifts are written as
 * transient modifiers and a short effect refreshed every tick, so dropping the guard takes all of
 * them with it instead of leaving a body quietly faster than it should be.</p>
 */
public class KamaeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A guard is a shape of the body, not a noise. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 2 2 - the kenjutsu seals and the two keys of the guard. */
    public static final long COMBO = 132222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kamae");

    /** The stance the caster is in: 0 high, 1 middle, 2 low. It outlives a relog on purpose. */
    public static final String STANCE_TAG = "narutomod:kamae";

    /** The mark that says the guard is up right now, read by anything that leans on it. */
    public static final String UNTIL_TAG = "narutomod:kamae_until";

    /** The three stances, in the order the guard steps through them. */
    public static final int UPPER = 0;
    public static final int MIDDLE = 1;
    public static final int LOWER = 2;

    public static final int STANCE_COUNT = 3;

    /** What raising the guard costs, and what holding it there costs every tick. */
    private static final float OPEN_COST = 10.0f;
    private static final float UPKEEP = 0.05f;

    private static final int STAMINA_REGEN_DELAY = 10;

    /** A tenth more of what the arm already carries, and a tenth more of the step. */
    private static final double DAMAGE_BONUS = 0.1d;
    private static final double SPEED_BONUS = 0.1d;

    /**
     * How long each gift lasts. It is renewed every tick, so this is only how long it lingers after
     * the guard drops - short enough that the body is honest again by the next breath.
     */
    private static final int EFFECT_DURATION = 20;

    /** Stable ids, so the same modifier is added and taken off rather than stacking. */
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("3f7c1a52-8b64-4d19-a7e0-52c9f1b73d01");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("b6d4e217-4f83-4a26-9c15-7e08a2d64b32");

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the guard drops still find it.
     */
    private static final int MARK_GRACE = 5;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kamae.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The tick that raises the guard pays for raising it as well as its own upkeep. The mark
        // tells the two apart, because the toggle set already holds the stance by the time this is
        // first called.
        boolean opening = !isUp(player);
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST + UPKEEP : UPKEEP);
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
        // A guard is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        boolean opening = !isUp(player);
        int stance = stance(player);
        if (opening) {
            // Each raising steps on to the next guard, so all three are reached by raising and
            // lowering the blade in turn.
            stance = (lastStance(player) + 1) % STANCE_COUNT;
            player.getPersistentData().putInt(STANCE_TAG, stance);
            player.displayClientMessage(Component.translatable(stanceKey(stance))
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        applyStance(player, stance);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);
    }

    /** Gives the gift of the stance the caster is in and takes the other two away. */
    private static void applyStance(Player player, int stance) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (stance == UPPER && damage != null && damage.getModifier(DAMAGE_MODIFIER_ID) == null) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID, "narutomod:kamae_upper",
                    DAMAGE_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        } else if (stance != UPPER && damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }

        if (stance == LOWER && speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, "narutomod:kamae_lower",
                    SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        } else if (stance != LOWER && speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }

        // The middle guard is the one the plate cannot give: it is given as an effect, refreshed
        // with a short duration so it is gone within a second of the blade being lowered.
        if (stance == MIDDLE) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    EFFECT_DURATION, 0, true, false, false));
        }
    }

    private static String stanceKey(int stance) {
        return switch (stance) {
            case UPPER -> "jutsu.kamae.upper";
            case MIDDLE -> "jutsu.kamae.middle";
            default -> "jutsu.kamae.lower";
        };
    }

    /** The stance the caster is in, or the high guard for a body that has never taken one. */
    public static int stance(Player player) {
        return player.getPersistentData().contains(STANCE_TAG)
                ? player.getPersistentData().getInt(STANCE_TAG)
                : UPPER;
    }

    /** The stance the guard was last in, so the next raising knows what to step on to. */
    private static int lastStance(Player player) {
        return player.getPersistentData().contains(STANCE_TAG)
                ? player.getPersistentData().getInt(STANCE_TAG)
                : STANCE_COUNT - 1;
    }

    /** @return whether the guard is up right now, asked by the hit hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** The guard is mastered by holding it, so casting it on and off records nothing. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The modifiers and the effect are synced from the server.
    }

    /** Lowering the blade takes the guard, its gift and its mark with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        // The middle guard's resistance was given a second of life rather than stripped here, so a
        // potion the caster has drunk is not taken with it.
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
