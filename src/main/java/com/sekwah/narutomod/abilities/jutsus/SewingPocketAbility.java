package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * Jougan Sewing Pocket: a purse cut into the seam beside the caster, the size of an ender chest.
 *
 * <p>An ender chest is a place rather than an object - every one of them opens the same nine and
 * twenty slots, and the caster's own stash is already there. The eye only has to find the seam the
 * chest hangs on and hold it open, so nothing is carried and nothing is duplicated: the technique
 * opens the caster's own ender inventory, with the same contents as the chest at home, and closes it
 * again on its own.</p>
 *
 * <p>The pocket is a hole in the world and the world does not leave holes open, which is what the
 * timer is for. The window is closed from the outside by the tick pass in {@code JouganExtraEvents},
 * because a menu has no lifetime of its own in vanilla; see {@link #OPEN_UNTIL_TAG}.</p>
 */
public class SewingPocketAbility extends Ability implements Ability.Cooldown {

    /** Cutting a hole in space is heard, but briefly. */
    @Override
    public int noiseRadius() {
        return 4;
    }

    /** B V V V V V - the last of the Jougan's sewing branch. */
    public static final long COMBO = 322222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "sewing_pocket");

    private static final float COST = 30.0f;
    private static final int COOLDOWN = 600;

    /** How long the pocket stays open, in ticks. */
    public static final int OPEN_TICKS = 200;

    /** Three rows of nine, the shape every ender chest has. */
    private static final int ROWS = 3;

    /**
     * When the pocket falls shut, kept on the caster rather than in this class so the closing pass
     * can read it without knowing which cast opened the menu. Public: the event class owns the
     * closing, this class only writes the expiry.
     */
    public static final String OPEN_UNTIL_TAG = "narutomod:sewing_pocket_until";

    /**
     * The id of the menu that was opened, so the closing pass can tell the caster's pocket from any
     * other container they happened to open in the meantime.
     */
    public static final String OPEN_MENU_TAG = "narutomod:sewing_pocket_menu";

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                            Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inventory,
                        p.getEnderChestInventory(), ROWS),
                Component.translatable("container.enderchest")));

        // The menu id is only known once it is open, so the expiry is written afterwards; the closing
        // pass ignores a menu whose id has changed, which happens the moment the caster opens another.
        CompoundTag data = serverPlayer.getPersistentData();
        data.putLong(OPEN_UNTIL_TAG, serverPlayer.level().getGameTime() + OPEN_TICKS);
        data.putInt(OPEN_MENU_TAG, serverPlayer.containerMenu.containerId);

        if (serverPlayer.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.PORTAL,
                    serverPlayer.getX(), serverPlayer.getY() + serverPlayer.getBbHeight() * 0.6d,
                    serverPlayer.getZ(), 24, 0.4d, 0.5d, 0.4d, 0.05d);
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }
}
