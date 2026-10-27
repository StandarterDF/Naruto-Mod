package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Mist Sight: chakra seen through the one thing that hides a body best - a body that is pretending
 * not to be there.
 *
 * <p>The Byakugan's bubble already reaches every living thing nearby, but two kinds of target slip
 * through the reading the caster actually cares about: something invisible, and a person wearing
 * somebody else's face with a henge. Both are hiding a chakra network that the eye can see perfectly
 * well, so the stance simply stops the body from being hidden and lets the caster's own eyes find it
 * through the walls.</p>
 *
 * <p>It hands out nothing to the caster and touches nobody else. What it does is mark, faintly, the
 * bodies that were trying to be unremarkable - and it only marks bodies inside the eye's own reach,
 * so it is a way of reading a room, not a way of hunting across a field.</p>
 */
public class ByakuganMistSightAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** An eye that only looks is quiet. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V B C C - the needles' seal and the palm's, doubled at the end. */
    public static final long COMBO = 11213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "byakugan_mist_sight");

    /** Chakra burnt for every tick the shroud is being read through. */
    private static final float CHAKRA_PER_TICK = 0.1f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far the marking reaches, in blocks. Inside the eye's own bubble, deliberately. */
    private static final double RADIUS = 16.0d;

    /** How often the marks are renewed, and how long each lasts - twice the interval, so none flicker. */
    private static final int MARK_INTERVAL = 10;
    private static final int MARK_TICKS = 20;

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
        // This one is a reading of the eye's own picture, not a separate gift: the eye must be open.
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
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
        if (player.tickCount % MARK_INTERVAL != 0) {
            return;
        }
        for (LivingEntity hidden : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS), entity -> entity != player && isHidden(entity))) {
            if (hidden.distanceToSqr(player) > RADIUS * RADIUS) {
                continue;
            }
            hidden.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false));
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
        // Only the stance is synced; the marks are given as ordinary effects by the server.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // The marks were given short durations and fade on their own within a second of letting go.
    }

    /** @return whether this body is trying not to be seen: invisible, or wearing a henge. */
    private static boolean isHidden(LivingEntity entity) {
        if (entity.isRemoved() || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        if (entity.isInvisible()) {
            return true;
        }
        if (!(entity instanceof Player player)) {
            return false;
        }
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .map(data -> data.getHengeTicks() > 0)
                .orElse(false);
    }
}
