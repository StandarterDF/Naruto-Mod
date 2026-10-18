package com.sekwah.narutomod.mixin.common;

import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The door into a campfire's cooking state, the campfire twin of
 * {@link AbstractFurnaceAccessor}.
 *
 * <p>Vanilla keeps {@code cookingProgress} and {@code cookingTime} as package-private int arrays with
 * no setters, so the fire breath could not fan a campfire along without an accessor. Only the arrays
 * themselves need handing over - the values inside are what changes. The project's access transformer
 * never reaches the compile classpath in this workspace, and a mixin accessor is remapped by the
 * refmap besides - see {@code FireBreathJutsuAbility#applyBlockHeat}.</p>
 */
@Mixin(CampfireBlockEntity.class)
public interface CampfireAccessor {

    @Accessor("cookingProgress")
    int[] naruto$cookingProgress();

    @Accessor("cookingTime")
    int[] naruto$cookingTime();
}
