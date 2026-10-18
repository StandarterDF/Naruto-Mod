package com.sekwah.narutomod.mixin.common;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The door into a furnace's cooking state.
 *
 * <p>Vanilla keeps {@code cookingProgress}, {@code cookingTotalTime} and the burn counters
 * package-private and offers no setter, so the fire breath could not nudge a furnace along without
 * either an access transformer or this accessor. The AT route never reached the compile classpath in
 * this workspace, and a mixin accessor is remapped by the refmap besides, so it stays correct once
 * the mod is obfuscated - see {@code FireBreathJutsuAbility#applyBlockHeat}.</p>
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceAccessor {

    @Accessor("litTime")
    int naruto$litTime();

    @Accessor("litTime")
    void naruto$setLitTime(int litTime);

    @Accessor("litDuration")
    int naruto$litDuration();

    @Accessor("litDuration")
    void naruto$setLitDuration(int litDuration);

    @Accessor("cookingProgress")
    int naruto$cookingProgress();

    @Accessor("cookingProgress")
    void naruto$setCookingProgress(int cookingProgress);

    @Accessor("cookingTotalTime")
    int naruto$cookingTotalTime();
}
