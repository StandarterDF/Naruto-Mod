package com.sekwah.narutomod.mixin.common;

import com.sekwah.narutomod.abilities.jutsus.SilenceAbility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops game events that a silenced caster would otherwise make the world hear.
 *
 * <p>Everything the skulk responds to reaches it through {@link GameEventDispatcher#post}, so this is
 * the one gate where a footstep, a jump, a block being placed or a blow being landed can all be
 * refused at once, before any listener - sculk sensor, shrieker or warden - is even told about it.
 * The event carries the entity that caused it, so all that is needed is to check whether that entity
 * is a player holding the silence stance. Events with no source entity (a block ticking on its own,
 * say) are left alone: this mutes a ninja, not the world.</p>
 */
@Mixin(GameEventDispatcher.class)
public class GameEventDispatcherMixin {

    @Inject(method = "post", at = @At("HEAD"), cancellable = true)
    private void narutomod$silencePlayerEvents(GameEvent gameEvent, Vec3 source, GameEvent.Context context,
                                                CallbackInfo callback) {
        Entity entity = context.sourceEntity();
        if (entity instanceof Player player && SilenceAbility.isSilenced(player)) {
            callback.cancel();
        }
    }
}
