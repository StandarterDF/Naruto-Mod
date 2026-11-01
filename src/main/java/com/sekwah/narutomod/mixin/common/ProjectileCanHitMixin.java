package com.sekwah.narutomod.mixin.common;

import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.jutsus.SenriganEvasionAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Senrigan evasion: a projectile never registers the evading player as a hit at all, so it flies
 * straight through instead of bouncing off and being corrected by the server a tick later.
 */
@Mixin(Projectile.class)
public abstract class ProjectileCanHitMixin {

    private static final String EVADED_TAG = "narutomod:senriganEvaded";

    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void narutomod$senriganPassThrough(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!(target instanceof Player player)) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        if (self.getOwner() == player) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SenriganEvasionAbility.isActive(ninjaData)) {
                return;
            }
            float cost = SenriganEvasionAbility.hitCost(ninjaData);
            if (ninjaData.getChakra() < cost) {
                if (!player.level().isClientSide) {
                    ninjaData.getToggleAbilityData().removeAbilityEndedSilently(player, ninjaData,
                            NarutoAbilities.SENRIGAN_EVASION.get());
                }
                return;
            }
            // Charge once per projectile per 20 ticks, on the server only.
            CompoundTag data = self.getPersistentData();
            long mark = data.getLong(EVADED_TAG);
            int storedPlayer = (int) (mark >> 32);
            int expiry = (int) (mark & 0xFFFFFFFFL);
            boolean alreadyPaid = storedPlayer == player.getId() && self.tickCount < expiry;
            if (!alreadyPaid && !player.level().isClientSide) {
                ninjaData.useChakra(cost, 10);
                data.putLong(EVADED_TAG, ((long) player.getId() << 32) | (self.tickCount + 20L));
            }
            cir.setReturnValue(false);
        });
    }
}