package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Senrigan's true sight: the caster steps out of their own body and into one of their clones.
 *
 * <p>This used to move only the camera, which read as a spectator trick. Now it is a possession: the
 * caster looks at one of their own shadow clones, the clone is taken and the caster stands where it
 * stood, fighting with their own hands and inventory from the clone's position. The body left behind
 * is remembered in the caster's persistent data; there is no timer, so the possession lasts until it
 * is given up or the borrowed body is destroyed.</p>
 *
 * <p>A killing blow does not kill the caster: it breaks the hold and puts them back in their own
 * body at the health they left it with. Damage that bypasses invulnerability (the void, {@code /kill})
 * is deliberately not stopped - that is not a body being destroyed, that is the world ending.</p>
 *
 * <p>Casting the technique again returns the caster home, and that half is free so a caster whose eye
 * has closed or whose chakra has run dry can never be stranded in a clone.</p>
 */
public class SenriganSightAbility extends Ability {

    /** B V B B C - the eye, then one more key. */
    public static final long COMBO = 3121L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "senrigan_sight");

    /** How far away a clone can be and still be entered. Loaded chunks only, as always. */
    private static final double RANGE = 128.0d;

    /** What it costs to step into a clone. Coming back is free. */
    private static final float POSSESS_COST = 40.0f;

    /** Where the body that was left behind is remembered. */
    private static final String HOME_TAG = "narutomod:senriganHome";
    private static final String HOME_X = "X";
    private static final String HOME_Y = "Y";
    private static final String HOME_Z = "Z";
    private static final String HOME_DIMENSION = "Dimension";
    private static final String HOME_HEALTH = "Health";
    private static final String HOME_YAW = "Yaw";
    private static final String HOME_PITCH = "Pitch";
    private static final String HOME_INVENTORY = "Inventory";
    private static final String HOME_SELECTED = "SelectedSlot";
    private static final String HOME_EFFECTS = "Effects";
    private static final String HOME_FIRE = "Fire";
    private static final String HOME_FROZEN = "Frozen";
    private static final String HOME_AIR = "Air";

    /**
     * The borrowed body's gear goes into the same slots it occupies on a player: the hotbar's first
     * slot (selected while possessing), armour, and the off hand. Slots 2 and up are left empty for
     * whatever the caster picks up, and those pickups come home with them.
     */
    private static final int BODY_MAIN_SLOT = 0;
    private static final int BODY_AMMO_SLOT = 1;
    private static final int FIRST_PICKUP_SLOT = 2;

    /** Armour slots start here in {@code Inventory}; everything below is main inventory and hotbar. */
    private static final int FIRST_ARMOUR_SLOT = 36;

    @Override
    public int noiseRadius() {
        return 0;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.senriganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Coming home is always possible: the eye may have closed or the chakra run dry while the
        // caster was away, and being trapped in a clone would be the worst possible bug.
        if (isPossessing(player)) {
            return true;
        }
        if (!SenriganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (lookedAtClone(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.noclone",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, POSSESS_COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 10);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (isPossessing(serverPlayer)) {
            endPossession(serverPlayer);
            return;
        }
        ShadowCloneEntity clone = lookedAtClone(serverPlayer);
        if (clone == null) {
            return;
        }
        beginPossession(serverPlayer, clone);
    }

    /** Steps the caster into the clone's place, wearing the clone's body and taking the clone away. */
    private static void beginPossession(ServerPlayer player, ShadowCloneEntity clone) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag home = new CompoundTag();
        home.putDouble(HOME_X, player.getX());
        home.putDouble(HOME_Y, player.getY());
        home.putDouble(HOME_Z, player.getZ());
        home.putString(HOME_DIMENSION, level.dimension().location().toString());
        home.putFloat(HOME_HEALTH, player.getHealth());
        home.putFloat(HOME_YAW, player.getYRot());
        home.putFloat(HOME_PITCH, player.getXRot());
        home.put(HOME_INVENTORY, player.getInventory().save(new ListTag()));
        home.putInt(HOME_SELECTED, player.getInventory().selected);
        ListTag effects = new ListTag();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(effect.save(new CompoundTag()));
        }
        home.put(HOME_EFFECTS, effects);
        // Burning, freezing and held breath are not {@code MobEffectInstance}s, so they would stay on
        // the player entity through a swap unless they are moved by hand. A body is taken whole: the
        // caster's own fire and frost are left behind with the body and the clone's are worn instead.
        home.putInt(HOME_FIRE, player.getRemainingFireTicks());
        home.putInt(HOME_FROZEN, player.getTicksFrozen());
        home.putInt(HOME_AIR, player.getAirSupply());
        player.getPersistentData().put(HOME_TAG, home);

        // The point of a possession is that the caster wears the clone, not that they stand where it
        // stood: the borrowed body brings its own gear, its own wounds and its own effects. What the
        // caster's body was carrying is put aside in the tag above and comes back on the way out.
        takeBody(player, clone);

        Vec3 spot = clone.position();
        player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        clone.dispel();

        level.sendParticles(ParticleTypes.PORTAL, spot.x, spot.y + 1.0d, spot.z,
                30, 0.5d, 0.8d, 0.5d, 0.15d);
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
        player.displayClientMessage(Component.translatable("jutsu.senrigan.possess")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Dresses the caster in the clone's gear and sets the borrowed body's health and effects. */
    private static void takeBody(ServerPlayer player, ShadowCloneEntity clone) {
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        inventory.selected = BODY_MAIN_SLOT;

        copyTo(player, clone, EquipmentSlot.MAINHAND, BODY_MAIN_SLOT);
        // The off hand has a fixed player slot, so the clone's off hand goes there rather than into a
        // hotbar slot; the ammo sits in the second hotbar slot instead.
        player.setItemSlot(EquipmentSlot.OFFHAND, clone.getItemBySlot(EquipmentSlot.OFFHAND).copy());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(slot, clone.getItemBySlot(slot).copy());
        }
        ItemStack ammo = clone.getAmmo();
        if (!ammo.isEmpty()) {
            inventory.setItem(BODY_AMMO_SLOT, ammo.copy());
        }

        player.setHealth(Mth.clamp(clone.getHealth(), 1.0f, player.getMaxHealth()));
        player.removeAllEffects();
        for (MobEffectInstance effect : clone.getActiveEffects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        // The borrowed body is a chakra construct and does not heal on its own, so the clone's
        // wounds stay open: a possession is a risk, not a free refill at full health.
        player.setRemainingFireTicks(clone.getRemainingFireTicks());
        player.setTicksFrozen(clone.getTicksFrozen());
        player.setAirSupply(clone.getAirSupply());
        player.inventoryMenu.broadcastChanges();
    }

    private static void copyTo(ServerPlayer player, ShadowCloneEntity clone, EquipmentSlot slot,
                               int inventorySlot) {
        ItemStack stack = clone.getItemBySlot(slot);
        if (!stack.isEmpty()) {
            player.getInventory().setItem(inventorySlot, stack.copy());
        }
    }

    /**
     * Puts the caster back in the body they left, at the health it had, with the inventory and
     * effects it was carrying. Safe to call when nothing is being possessed.
     */
    public static void endPossession(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(HOME_TAG)) {
            return;
        }
        CompoundTag home = data.getCompound(HOME_TAG);
        data.remove(HOME_TAG);

        ResourceLocation dimension = ResourceLocation.tryParse(home.getString(HOME_DIMENSION));
        MinecraftServer server = player.getServer();
        ServerLevel target = server == null || dimension == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (target == null) {
            // A dimension that no longer exists is not worth a crash: the caster keeps their feet
            // and simply comes back where they are.
            target = player.serverLevel();
        }
        restoreBody(player, home);
        player.teleportTo(target, home.getDouble(HOME_X), home.getDouble(HOME_Y), home.getDouble(HOME_Z),
                home.getFloat(HOME_YAW), home.getFloat(HOME_PITCH));
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        if (home.contains(HOME_HEALTH)) {
            float health = Mth.clamp(home.getFloat(HOME_HEALTH), 1.0f, player.getMaxHealth());
            player.setHealth(health);
        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    24, 0.4d, 0.6d, 0.4d, 0.1d);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        player.displayClientMessage(Component.translatable("jutsu.senrigan.return")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /**
     * Gives the caster their own inventory and effects back.
     *
     * <p>Anything picked up while wearing the borrowed body sits in slots 2 and up and is carried
     * home; the clone's own gear in the body slots is discarded, because it was a copy to begin
     * with. Also called on death, before vanilla drops the inventory, so the real items are the ones
     * that fall - never a pile of clone copies while the original is stuck in a tag.</p>
     */
    private static void restoreBody(Player player, CompoundTag home) {
        if (home.contains(HOME_INVENTORY, Tag.TAG_LIST)) {
            Inventory inventory = player.getInventory();
            List<ItemStack> carried = new ArrayList<>();
            for (int slot = FIRST_PICKUP_SLOT; slot < FIRST_ARMOUR_SLOT; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    carried.add(stack.copy());
                }
            }
            inventory.clearContent();
            inventory.load(home.getList(HOME_INVENTORY, Tag.TAG_COMPOUND));
            inventory.selected = Mth.clamp(home.getInt(HOME_SELECTED), 0, 8);
            for (ItemStack stack : carried) {
                if (!inventory.add(stack)) {
                    player.drop(stack, false);
                }
            }
            player.inventoryMenu.broadcastChanges();
        }
        if (home.contains(HOME_EFFECTS, Tag.TAG_LIST)) {
            player.removeAllEffects();
            ListTag effects = home.getList(HOME_EFFECTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < effects.size(); i++) {
                MobEffectInstance effect = MobEffectInstance.load(effects.getCompound(i));
                if (effect != null) {
                    player.addEffect(effect);
                }
            }
        }
        // Fire, frost and breath belong to the body that lived through them, so they come back with
        // it: burning while wearing a clone does not carry home, and the caster's own fire was put
        // aside when they left.
        player.setRemainingFireTicks(home.getInt(HOME_FIRE));
        player.setTicksFrozen(home.getInt(HOME_FROZEN));
        player.setAirSupply(home.getInt(HOME_AIR));
    }

    /** Drops the remembered body, restoring what it carried, for a caster who died while away. */
    public static void clear(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(HOME_TAG)) {
            return;
        }
        CompoundTag home = data.getCompound(HOME_TAG);
        data.remove(HOME_TAG);
        restoreBody(player, home);
    }

    /** @return whether this caster is currently standing in a borrowed body. */
    public static boolean isPossessing(Player player) {
        return player.getPersistentData().contains(HOME_TAG);
    }

    /** The caster's own shadow clone under the crosshair, or null when they are not looking at one. */
    private static ShadowCloneEntity lookedAtClone(Player player) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof ShadowCloneEntity clone)) {
            return null;
        }
        if (!clone.isOwnedBy(player) || !clone.isAlive()) {
            return null;
        }
        return clone;
    }
}
