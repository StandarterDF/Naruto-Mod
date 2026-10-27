package com.sekwah.narutomod.damagetypes;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

public class NarutoDamageTypes {
    public static final ResourceKey<DamageType> FIREBALL = create("fireball");
    public static final ResourceKey<DamageType> WATER_BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(NarutoMod.MOD_ID, "water_bullet"));

    /**
     * What the rasenshuriken cuts with: the thrown wind disc and the burst it comes apart into. Its
     * own damage type so death messages can name the technique, see {@code RasenshurikenJutsuEntity}.
     */
    public static final ResourceKey<DamageType> WIND_RELEASE = create("wind_release");

    /**
     * What the eight inner gates do to the body holding them open. Its own damage type so that it can
     * be excluded from every kind of mitigation: armour, resistance, absorption and the chakra shield
     * all ignore it, see the {@code bypasses_*} tag files. Otherwise the gates' own resistance buff,
     * or a shield shaped out of chakra, would quietly absorb the very thing that is meant to kill the
     * caster.
     */
    public static final ResourceKey<DamageType> GATE_BACKLASH = create("gate_backlash");

    /**
     * What the fire breath burns with. Deliberately not tagged as fire: the technique is a jet of hot
     * chakra, so it hurts but never sets a target alight and never spreads, see
     * {@code FireBreathJutsuAbility}.
     */
    public static final ResourceKey<DamageType> FIRE_BREATH = create("fire_breath");

    /**
     * The crunch of a mountain of rock being dropped on whatever Chibaku Tensei caught. Its own type
     * so the death message can name the technique, see {@code ChibakuTenseiEntity}. Like every other
     * custom type in the mod it needs its JSON in {@code data/narutomod/damage_type/} or the server
     * falls over the first time it lands.
     */
    public static final ResourceKey<DamageType> CHIBAKU_TENSEI = create("chibaku_tensei");

    /**
     * What the black flame of Amaterasu burns with. Its own type because the flame is not vanilla
     * fire at all - water, rain and fire resistance do nothing to it - and so the death message can
     * name the technique, see {@code AmaterasuEvents}. Like every other custom type it needs its
     * JSON in {@code data/narutomod/damage_type/} or the server falls over the first time it bites.
     */
    public static final ResourceKey<DamageType> AMATERASU = create("amaterasu");

    /**
     * What the thrown Tailed Beast Bomb hits with: the direct impact before the blast. Its own
     * type so the death message can name the technique, see {@code TailedBallEntity}. Like every
     * other custom type it needs its JSON in {@code data/narutomod/damage_type/} or the server
     * falls over the first time it lands.
     */
    public static final ResourceKey<DamageType> TAILED_BALL = create("tailed_ball");

    /**
     * What a sage's nature-charged blows carry: the frog kata's invisible strike and every other
     * hit dealt while sage mode is up. Its own type so the death message can name it, and so that
     * later work (Six Paths resistance, Truth-Seeking Orbs) can recognise senjutsu and treat it
     * differently from ordinary chakra. Like every other custom type it needs its JSON in
     * {@code data/narutomod/damage_type/} or the server falls over the first time it lands.
     */
    public static final ResourceKey<DamageType> SENJUTSU = create("senjutsu");

    public static ResourceKey<DamageType> create(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(NarutoMod.MOD_ID, name));
    }

    public static void register(BootstapContext<DamageType> context, ResourceKey<DamageType> key, float exhaustion) {
        context.register(key, new DamageType(key.location().getNamespace() + "." + key.location().getPath(), exhaustion));
    }

    public static DamageSource getDamageSource(Level level, ResourceKey<DamageType> type) {
        return getDamageSource(level, type, null, null, null);
    }

    public static DamageSource getDamageSource(Level level, ResourceKey<DamageType> type, @Nullable Entity source) {
        return getDamageSource(level, type, source, source, null);
    }

    public static DamageSource getDamageSource(Level level, ResourceKey<DamageType> type, @Nullable Entity source, @Nullable Entity indirectSource) {
        return getDamageSource(level, type, source, indirectSource, null);
    }

    public static DamageSource getDamageSource(Level level, ResourceKey<DamageType> type, @Nullable Entity source, @Nullable Entity indirectSource, Vec3 position) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), source, indirectSource, position);
    }

    public static void register(BootstapContext<DamageType> context, ResourceKey<DamageType> key) {
        register(context, key, 0.0F);
    }

    public static void bootstrap(BootstapContext<DamageType> context) {
        register(context, FIREBALL);
        register(context, WATER_BULLET);
        register(context, WIND_RELEASE);
        register(context, GATE_BACKLASH);
        register(context, FIRE_BREATH);
        register(context, CHIBAKU_TENSEI);
        register(context, AMATERASU);
        register(context, TAILED_BALL);
        register(context, SENJUTSU);
    }
}
