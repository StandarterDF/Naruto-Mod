package com.sekwah.narutomod.world;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * The paintings a ninja can hang on a wall.
 *
 * <p>Collecting is its own quiet progression: a scroll on a wall says more about a campaign than
 * another stat ever will. The variants reuse the vanilla painting art deliberately - a new texture
 * is a small thing to ask of a resource pack, but the point here is the collectible entry, not the
 * brushwork - so servers and packs can repaint them without touching code.</p>
 */
public final class NarutoPaintings {

    public static final DeferredRegister<PaintingVariant> PAINTINGS =
            DeferredRegister.create(Registries.PAINTING_VARIANT, NarutoMod.MOD_ID);

    /** A training scroll, two by two. */
    public static final RegistryObject<PaintingVariant> TRAINING_SCROLL = PAINTINGS.register("training_scroll",
            () -> new PaintingVariant(2, 2));

    /** The ramen stand at dusk, two by one. */
    public static final RegistryObject<PaintingVariant> RAMEN_STAND = PAINTINGS.register("ramen_stand",
            () -> new PaintingVariant(2, 1));

    /** The valley at the end, four by two. */
    public static final RegistryObject<PaintingVariant> VALLEY_END = PAINTINGS.register("valley_end",
            () -> new PaintingVariant(4, 2));

    private NarutoPaintings() {
    }

    public static void register(IEventBus eventBus) {
        PAINTINGS.register(eventBus);
    }
}
