package com.sekwah.narutomod.abilities.mastery;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.mastery.MasteryData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Technique mastery: the more a technique is cast, the cheaper and quicker it becomes, and the
 * better its special extras get.
 *
 * <p>The progress is a plain fraction worked out from a cast count with a curve that never quite
 * reaches one, so a technique is always a little better after every cast but never maxes out with a
 * bang: <pre>level = casts^e / (casts^e + halfway^e)</pre>. The exponent is what keeps the first
 * cast from handing out a chunk of the bar: at one the curve climbs steeply from the word go, at two
 * the first few casts are worth almost nothing and the technique has to actually be lived in before
 * it starts paying off, while half of it still lands at {@code halfway} casts whatever the exponent.
 * Everything else is derived from that one number, so retuning the curve retunes the whole system at
 * once.</p>
 */
public final class Mastery {

    private Mastery() {
    }

    /** Below this mastery the death penalty is at its mildest, above {@link #DEATH_HIGH_BAND} at its worst. */
    private static final float DEATH_LOW_BAND = 0.5f;
    private static final float DEATH_HIGH_BAND = 0.95f;

    /** A level this close to one is treated as one when turning it back into a cast count. */
    private static final float LEVEL_CEILING = 0.9995f;

    /**
     * The most mastery and skill nodes together may take off a price or cooldown, so a fully built
     * caster still has to pay something for every technique.
     */
    private static final float MAX_REDUCTION = 0.9f;

    /**
     * How fast mastery is earned, as a multiplier on every gain. One is the real pace; the
     * {@code /naruto debug masteryspeed} command turns it up or down so a tester can feel how a
     * technique plays at its early and late values without grinding through the curve twice. It
     * only affects future earnings, never the mastery already on a technique, and it is not saved:
     * a restart puts it back to normal.
     */
    private static volatile float gainMultiplier = 1.0f;

    /** The current testing multiplier on mastery gains. */
    public static float gainMultiplier() {
        return gainMultiplier;
    }

    /** Sets the testing multiplier on mastery gains; zero freezes progress entirely. */
    public static void setGainMultiplier(float multiplier) {
        gainMultiplier = Math.max(0f, multiplier);
    }

    /** The curve's own shape, not tuned from config here; {@link #level(int)} reads the config. */
    public static float level(int casts) {
        if (casts <= 0) {
            return 0f;
        }
        float halfway = Math.max(1f, NarutoConfig.masteryHalfwayCasts);
        double exponent = exponent();
        double cast = Math.pow(casts, exponent);
        return (float) (cast / (cast + Math.pow(halfway, exponent)));
    }

    public static float level(INinjaData ninjaData, ResourceLocation ability) {
        if (ability == null) {
            return 0f;
        }
        return level(ninjaData.getMasteryData().getCasts(ability));
    }

    public static float level(INinjaData ninjaData, Ability ability) {
        return level(ninjaData, idOf(ability));
    }

    public static ResourceLocation idOf(Ability ability) {
        return NarutoRegistries.ABILITIES.getResourceKey(ability)
                .map(ResourceKey::location)
                .orElse(null);
    }

    /** The cast count that would give the given level, undoing the curve. */
    public static int castsForLevel(float level) {
        if (level <= 0f) {
            return 0;
        }
        float capped = Math.min(level, LEVEL_CEILING);
        float halfway = Math.max(1f, NarutoConfig.masteryHalfwayCasts);
        double ratio = capped / (1.0d - capped);
        return (int) Math.round(halfway * Math.pow(ratio, 1.0d / exponent()));
    }

    /** The curve's exponent, read once per call so a config change takes effect without a restart. */
    private static double exponent() {
        return Math.max(0.1d, NarutoConfig.masteryExponent);
    }

    /** Records one successful cast of the technique, the only thing that moves mastery forward. */
    public static void recordCast(Player player, INinjaData ninjaData, Ability ability) {
        award(player, ninjaData, ability, 1.0f);
    }

    /**
     * Records mastery that is measured out as a technique works rather than as a cast - chakra
     * charge paying in chakra it restored, say. The amount is in cast-equivalents and may be a
     * fraction; whatever does not add up to a whole cast is banked and carried into the next one, so
     * a run of short channels is worth the same as one long one.
     */
    public static void recordCredit(Player player, INinjaData ninjaData, Ability ability, float credit) {
        award(player, ninjaData, ability, credit);
    }

    /**
     * The whole of mastery earning funnels through here, so the testing speed multiplier applies the
     * same way whether a technique is counted by casts or by the work it did.
     */
    private static void award(Player player, INinjaData ninjaData, Ability ability, float credit) {
        if (!NarutoConfig.masteryEnabled || player.isCreative()) {
            return;
        }
        float scaled = credit * gainMultiplier;
        if (scaled <= 0f) {
            return;
        }
        ResourceLocation id = idOf(ability);
        if (id == null) {
            return;
        }
        MasteryData data = ninjaData.getMasteryData();
        float total = data.getCredit(id) + scaled;
        int whole = (int) total;
        if (whole > 0) {
            data.addCasts(id, whole);
            total -= whole;
        }
        data.setCredit(id, total);
    }

    /**
     * The price of a technique after mastery, for the check and the payment alike so they can never
     * disagree about what fits in the bar.
     *
     * <p>Skill tree nodes that cheapen techniques stack on top of mastery, and the two together are
     * capped just short of free so no technique ever ends up costing nothing (or less).</p>
     */
    public static float cost(INinjaData ninjaData, Ability ability, float baseCost) {
        float mastery = masteryReduction(ninjaData, ability, NarutoConfig.masteryCostReduction);
        float skills = ninjaData.getSkillData().costReduction();
        return baseCost * (1.0f - Math.min(MAX_REDUCTION, mastery + skills));
    }

    /** The cooldown of a technique after mastery and skill nodes, keyed by the ability's resource id. */
    public static int reduceCooldown(INinjaData ninjaData, String abilityId, int baseCooldown) {
        if (baseCooldown <= 0) {
            return baseCooldown;
        }
        ResourceLocation id = ResourceLocation.tryParse(abilityId);
        float mastery = masteryReduction(ninjaData, id, NarutoConfig.masteryCooldownReduction);
        float skills = ninjaData.getSkillData().cooldownReduction();
        float reduction = Math.min(MAX_REDUCTION, mastery + skills);
        return Math.max(0, Math.round(baseCooldown * (1.0f - reduction)));
    }

    private static float masteryReduction(INinjaData ninjaData, Ability ability, double factor) {
        if (!NarutoConfig.masteryEnabled || ability == null) {
            return 0f;
        }
        return (float) (factor * level(ninjaData, ability));
    }

    private static float masteryReduction(INinjaData ninjaData, ResourceLocation id, double factor) {
        if (!NarutoConfig.masteryEnabled || id == null) {
            return 0f;
        }
        return (float) (factor * level(ninjaData, id));
    }

    /** Whether the technique is mastered enough that the chakra seal is no longer needed at all. */
    public static boolean sealFree(INinjaData ninjaData, Ability ability) {
        return NarutoConfig.masteryEnabled && level(ninjaData, ability) >= NarutoConfig.masterySealFree;
    }

    /**
     * How far the chakra meditation reaches at this mastery: a plain eight blocks at the start,
     * growing hard towards the end so a finished ninja can sit anywhere while their clones work.
     */
    public static float meditationRadius(INinjaData ninjaData, Ability ability) {
        float level = level(ninjaData, ability);
        double max = Math.max(8.0d, NarutoConfig.meditationRadiusMax);
        return (float) (8.0d + (max - 8.0d) * level * level * level);
    }

    /**
     * Sets every technique back a little, more the closer it was to finished and with a spread
     * between deaths. It is deliberately worked on the level rather than the cast count: near the
     * top of the curve a share of the count barely moves the level, which would not make dying
     * matter.
     */
    public static void applyDeathPenalty(Player player, INinjaData ninjaData) {
        if (!NarutoConfig.masteryEnabled) {
            return;
        }
        MasteryData data = ninjaData.getMasteryData();
        List<ResourceLocation> techniques = new ArrayList<>(data.getCasts().keySet());
        RandomSource random = player.getRandom();
        for (ResourceLocation id : techniques) {
            int casts = data.getCasts(id);
            float level = level(casts);
            if (level <= 0f) {
                continue;
            }
            float t = Mth.clamp((level - DEATH_LOW_BAND) / (DEATH_HIGH_BAND - DEATH_LOW_BAND), 0f, 1f);
            float min = (float) Mth.lerp(t, NarutoConfig.masteryDeathLowMin, NarutoConfig.masteryDeathHighMin);
            float max = (float) Mth.lerp(t, NarutoConfig.masteryDeathLowMax, NarutoConfig.masteryDeathHighMax);
            float penalty = min + random.nextFloat() * Math.max(0f, max - min);
            data.setCasts(id, castsForLevel(Math.max(0f, level - penalty)));
        }
    }
}
