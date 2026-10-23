package com.sekwah.narutomod.loot;

import com.mojang.serialization.Codec;
import com.sekwah.narutomod.NarutoMod;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The loot modifier types the mod registers, and the only place a new one has to be added.
 *
 * <p>Registering the codec makes the {@code type} of a JSON file under
 * {@code data/narutomod/loot_modifiers/} resolvable; the files themselves are then listed in
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.</p>
 */
public final class NarutoLootModifiers {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, NarutoMod.MOD_ID);

    /** Adds one of a weighted list of the mod's items to a chest. */
    public static final RegistryObject<Codec<AddLootModifier>> ADD_LOOT =
            SERIALIZERS.register("add_loot", () -> AddLootModifier.CODEC);

    private NarutoLootModifiers() {
    }

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
