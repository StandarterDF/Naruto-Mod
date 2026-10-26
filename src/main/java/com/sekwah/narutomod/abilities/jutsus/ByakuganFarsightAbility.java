package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Far Sight: the Byakugan stops looking at the field and starts looking at the horizon.
 *
 * <p>The eye already shows every body in a bubble around the caster through the walls. This is the
 * other half of the same gift: instead of widening the bubble, it narrows the caster's own view onto
 * one distant thing, so a ridge, a road or a village can be read from where they stand.</p>
 *
 * <p>The stance is deliberately empty on the server. It grants no resistance, no damage and no
 * effect a dispel could take away; the narrowing itself is done by the client from the synced toggle
 * set, so the only work here is keeping the stance fed and paid for. That is also why it asks for the
 * Byakugan to have been learned rather than to be open - the far sight is a way of using the eye, and
 * the caster is free to have it closed while they look.</p>
 */
public class ByakuganFarsightAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** An eye that only looks is quiet. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V B B - the needles' seal with the last key swapped for the eye's own. */
    public static final long COMBO = 232222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "byakugan_farsight");

    /** Chakra burnt for every tick the view is pulled in. */
    private static final float CHAKRA_PER_TICK = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How often the faint ring around the head is drawn while the stance is up, in ticks. */
    private static final int AURA_INTERVAL = 8;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Reading far away is still reading with the Byakugan, so the eye has to be known first. The
        // stance itself does not need the eye open; it is the eye's use, not another eye.
        if (!AbilityUnlocks.isUnlocked(ninjaData, ByakuganAbility.ID)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.notlearned",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Mastery is earned by holding the stance, not by flicking it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (player.level() instanceof ServerLevel level && player.tickCount % AURA_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + player.getBbHeight() * 0.92d, player.getZ(),
                    1, 0.12d, 0.04d, 0.12d, 0.002d);
        }
    }

    /**
     * A stance is mastered by holding it, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The narrowing is drawn by the client from the synced toggle set; there is no body to keep.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // The stance gave the body nothing, so there is nothing to hand back when it ends.
    }
}
