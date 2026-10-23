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

    private NarutoEffects() {
    }

    public static void register(IEventBus eventBus) {
        EFFECTS.register(eventBus);
    }
}
