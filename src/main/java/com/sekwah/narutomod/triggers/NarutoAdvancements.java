package com.sekwah.narutomod.triggers;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The few places that tell the advancement triggers something happened.
 *
 * <p>Kept in one place so the game code stays readable: callers say what happened in mod terms, and
 * this translates it into trigger calls with the right ids.</p>
 */
public final class NarutoAdvancements {

    private NarutoAdvancements() {
    }

    /** A technique was cast for real: the trigger plus wherever its mastery now stands. */
    public static void onTechniqueCast(Player player, INinjaData ninjaData, Ability ability) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isCreative()) {
            return;
        }
        ResourceLocation id = Mastery.idOf(ability);
        if (id == null) {
            return;
        }
        NarutoTriggers.JUTSU_CAST.trigger(serverPlayer, id);
        int level = Math.round(Mastery.level(ninjaData, ability) * 100f);
        if (level > 0) {
            NarutoTriggers.MASTERY.trigger(serverPlayer, id, level);
        }
    }

    /** A scroll was read, first time or not. */
    public static void onScrollRead(ServerPlayer player, ResourceLocation knowledge) {
        if (knowledge == null) {
            return;
        }
        NarutoTriggers.SCROLL_FOUND.trigger(player, knowledge);
    }
}
