package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.menu.SupplyChestContainer;
import com.sekwah.narutomod.menu.SupplyChestMenu;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;

/**
 * Opens the caster's supply chest, the one place their worker clones bring a harvest.
 *
 * <p>The chest is not a block - there is only ever one of it and its slots live in the caster's own
 * data, see {@code INinjaData#getSupplyChest}. That is exactly what lets a worker drop something
 * into it from anywhere, with no chunk to keep loaded; this technique is simply how the caster looks
 * inside. It costs nothing, since it is a look rather than a cast.</p>
 *
 * <p>Holding the last key of the combo instead of tapping it summons a small item-collector clone:
 * one hit point, no fight, standing still for a minute and pulling dropped items into the caster's
 * inventory. Tap for the chest, hold for the collector.</p>
 */
public class StorageChestAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B B - tap to open the chest, hold to summon an item-collector clone. */
    public static final long COMBO = 33L;

    /** Persistent-data mark written on a collector clone. */
    public static final String COLLECTOR_KEY = "narutomod:item_collector_clone";

    /** How long a collector clone sticks around, in ticks (one minute). */
    private static final int COLLECTOR_LIFETIME = 20 * 60;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean hideChannelMessages() {
        return true;
    }

    @Override
    public boolean logInChat() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // A tap (or the minimum-activation packet) opens the chest. Anything that actually held the
        // channel - even for a single tick - is read as a request for a collector clone instead.
        if (ticksActive < 0) {
            if (player instanceof ServerPlayer serverPlayer) {
                SupplyChestContainer chest = ninjaData.getSupplyChest();
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                                (id, inventory, p) -> new SupplyChestMenu(id, inventory, chest),
                                Component.translatable("narutomod:storage_chest")),
                        buffer -> buffer.writeVarInt(chest.getContainerSize()));
            }
            return;
        }

        // Only one collector clone per caster: pop any older one still standing.
        for (ShadowCloneEntity existing : level.getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(256.0d),
                clone -> clone.isAlive() && clone.isOwnedBy(player)
                        && clone.getPersistentData().getBoolean(COLLECTOR_KEY))) {
            existing.discard();
        }

        ShadowCloneEntity clone = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        clone.setOwner(player);
        clone.setLifetime(COLLECTOR_LIFETIME);
        clone.setHealth(1.0f);

        // No fight, no wandering: the collector just stands where it was summoned.
        clone.goalSelector.removeAllGoals(goal -> true);
        clone.targetSelector.removeAllGoals(goal -> true);

        clone.getPersistentData().putBoolean(COLLECTOR_KEY, true);

        clone.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        level.addFreshEntity(clone);

        level.sendParticles(ParticleTypes.CLOUD, clone.getX(),
                clone.getY() + clone.getBbHeight() / 2.0d, clone.getZ(),
                12, 0.3d, 0.4d, 0.3d, 0.01d);
        level.playSound(null, player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.4f);
    }
}