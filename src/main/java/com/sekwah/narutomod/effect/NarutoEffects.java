package com.sekwah.narutomod.effect;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * The few status effects the mod needs of its own.
 *
 * <p>They live in a registry of their own rather than being faked with vanilla effects, so the HUD
 * shows the right name and icon and a server can tell them apart.</p>
 */
public final class NarutoEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, NarutoMod.MOD_ID);

    /** Slowness only: heavy legs from an empty chakra pool, never weakness or damage. */
    private static final UUID EXHAUSTION_SPEED_ID = UUID.fromString("7b0d2e4a-3f4e-4a1e-9f0e-3f3e5b8c9d10");

    public static final RegistryObject<MobEffect> CHAKRA_EXHAUSTION = EFFECTS.register("chakra_exhaustion",
            () -> new ExhaustionEffect(MobEffectCategory.HARMFUL, 0x5A3B8C));

    /** Critical exhaustion: the aftermath of the red Akimichi pill. */
    private static final UUID CRITICAL_EXHAUSTION_SPEED_ID = UUID.fromString("8c1e3f5b-4a5f-4b2f-a01f-4a4f6c9d0e21");
    private static final UUID CRITICAL_EXHAUSTION_ATTACK_ID = UUID.fromString("9d2f4a6c-5b6a-4c3a-b12a-5b5a7d0e1f32");

    public static final RegistryObject<MobEffect> CRITICAL_EXHAUSTION = EFFECTS.register("critical_exhaustion",
            () -> new CriticalExhaustionEffect(MobEffectCategory.HARMFUL, 0x8B0000));

    /**
     * The afterglow of a full charge: while it lasts the passive trickle of chakra runs several
     * times faster, so topping the bar up is worth doing before a fight rather than a chore. The
     * effect carries no modifier of its own - the regen code reads it, see {@code NinjaData}.
     */
    public static final RegistryObject<MobEffect> CHAKRA_FLOW = EFFECTS.register("chakra_flow",
            () -> new PlainEffect(MobEffectCategory.BENEFICIAL, 0x3FC7D4));

    /**
     * A plain harmful effect whose only bite is a movement modifier; the constructor of
     * {@link MobEffect} is protected, so it needs a small subclass of its own.
     */
    private static final class ExhaustionEffect extends MobEffect {
        private ExhaustionEffect(MobEffectCategory category, int colour) {
            super(category, colour);
            this.addAttributeModifier(Attributes.MOVEMENT_SPEED, EXHAUSTION_SPEED_ID.toString(),
                    -0.15d, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }

    /** An effect with nothing but a name and an icon; the logic that reads it lives elsewhere. */
    private static final class PlainEffect extends MobEffect {
        private PlainEffect(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }

    /** Critical exhaustion: slowness + weakness, and regeneration is blocked in {@code PlayerEvents}. */
    private static final class CriticalExhaustionEffect extends MobEffect {
        private CriticalExhaustionEffect(MobEffectCategory category, int colour) {
            super(category, colour);
            this.addAttributeModifier(Attributes.MOVEMENT_SPEED, CRITICAL_EXHAUSTION_SPEED_ID.toString(),
                    -0.35d, AttributeModifier.Operation.MULTIPLY_TOTAL);
            this.addAttributeModifier(Attributes.ATTACK_DAMAGE, CRITICAL_EXHAUSTION_ATTACK_ID.toString(),
                    -0.5d, AttributeModifier.Operation.MULTIPLY_TOTAL);
        }
    }

    private NarutoEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
