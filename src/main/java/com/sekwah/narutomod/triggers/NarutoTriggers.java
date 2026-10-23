package com.sekwah.narutomod.triggers;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;

/**
 * The advancement triggers the mod adds, so its progress can be written as ordinary advancements.
 *
 * <p>Vanilla has no idea what a jutsu cast or a found scroll is, so a few small triggers are the
 * cleanest way to get mod progress into the advancement screen: the achievements themselves stay
 * plain JSON, which keeps them editable without touching code.</p>
 *
 * <p>In 1.20.1 criterion triggers are not a forge registry but a plain static map
 * ({@link CriteriaTriggers#register}), so they are simply registered once while the mod loads, before
 * any datapack is read.</p>
 */
public final class NarutoTriggers {

    public static final ResourceLocation JUTSU_CAST_ID = new ResourceLocation(NarutoMod.MOD_ID, "jutsu_cast");
    public static final ResourceLocation SCROLL_FOUND_ID = new ResourceLocation(NarutoMod.MOD_ID, "scroll_found");
    public static final ResourceLocation MASTERY_ID = new ResourceLocation(NarutoMod.MOD_ID, "mastery");
    public static final ResourceLocation NINJA_AWAKENED_ID = new ResourceLocation(NarutoMod.MOD_ID, "ninja_awakened");
    public static final ResourceLocation NINJA_RANK_ID = new ResourceLocation(NarutoMod.MOD_ID, "ninja_rank");

    public static JutsuCastTrigger JUTSU_CAST;
    public static ScrollFoundTrigger SCROLL_FOUND;
    public static MasteryTrigger MASTERY;
    public static NinjaAwakenedTrigger NINJA_AWAKENED;
    public static NinjaRankTrigger NINJA_RANK;

    private NarutoTriggers() {
    }

    /** Must run before any advancement JSON is parsed, i.e. from the mod constructor. */
    public static void register() {
        JUTSU_CAST = CriteriaTriggers.register(new JutsuCastTrigger());
        SCROLL_FOUND = CriteriaTriggers.register(new ScrollFoundTrigger());
        MASTERY = CriteriaTriggers.register(new MasteryTrigger());
        NINJA_AWAKENED = CriteriaTriggers.register(new NinjaAwakenedTrigger());
        NINJA_RANK = CriteriaTriggers.register(new NinjaRankTrigger());
    }
}
