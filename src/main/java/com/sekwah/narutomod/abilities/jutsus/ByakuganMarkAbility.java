package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Byakugan: Mark, a fingertip of chakra pressed onto a body the eye has already found.
 *
 * <p>The circular vision is a bubble, and a bubble is short. What the eye needs against a hunt is
 * not a longer bubble but a way to keep one body in the picture after it has left: the mark is a
 * point of the caster's own chakra left under the skin, so the body glows for a minute and carries
 * a stamp that says how long it has left and who put it there.</p>
 *
 * <p>It is deliberately harmless - no damage, no drain, no slow. A mark is a promise to look at
 * somebody later, and the stamp is a plain game-time number in the body's persistent data, so it
 * survives a relog and dies with the body. The upkeep that keeps the glow honest lives in
 * {@code ByakuganAdvancedEvents}, the same way every other timed mark in the mod is enforced.</p>
 */
public class ByakuganMarkAbility extends Ability implements Ability.Cooldown {

    /** Pressing a mark is a flick of chakra, not a shout. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V B B C - the eye's seal, then two keys for the fingertip. */
    public static final long COMBO = 1123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "byakugan_mark");

    /** How far down the look ray the mark can be pressed: the far edge of the widened circle. */
    private static final double RANGE = 24.0d;

    /** What it costs and how long before the eye can mark again. */
    private static final float COST = 20.0f;
    private static final int COOLDOWN = 200;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long a mark lasts, in ticks, and the same number given to the glow it shows. */
    private static final int DURATION = 1200;

    /**
     * Game time at which the mark lapses. Public because the upkeep in {@code ByakuganAdvancedEvents}
     * reads the same stamp rather than spelling the string again.
     */
    public static final String UNTIL_TAG = "narutomod:byakugan_mark_until";

    /** The caster the mark belongs to, as the body's own copy of who is watching it. */
    public static final String SOURCE_TAG = "narutomod:byakugan_mark_source";

    /** How many motes are drawn between the fingertip and the marked body. */
    private static final int LINE_POINTS = 16;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)
                || !canMark(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan_mark.notarget",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target)
                || !canMark(player, target)) {
            return;
        }

        // The mark is a stamp, not a live reference: the body keeps the number and the eye keeps
        // nothing, so an unloaded chunk cannot hold a mark - or a caster - in memory.
        CompoundTag data = target.getPersistentData();
        data.putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        data.putUUID(SOURCE_TAG, player.getUUID());

        // The glow is the only part of the mark other players can see, which is the point of it:
        // the hunt knows it has been seen. It is renewed by the upkeep, not here.
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, DURATION, 0, false, false));

        drawLine(level, player, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    /** A line of pale chakra from the caster's hand to the body, so the mark is seen where it lands. */
    private static void drawLine(ServerLevel level, Player player, LivingEntity target) {
        Vec3 hand = JutsuEffects.handPosition(player);
        Vec3 chest = target.position().add(0.0d, target.getBbHeight() * 0.55d, 0.0d);
        Vec3 span = chest.subtract(hand);
        for (int point = 1; point <= LINE_POINTS; point++) {
            Vec3 at = hand.add(span.scale(point / (double) (LINE_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z,
                8, 0.2d, 0.25d, 0.2d, 0.01d);
    }

    /**
     * Anything alive is a fair read except the caster themselves and the untouchable. Allies are
     * left in on purpose: the mark is a way of keeping a body in the picture, and losing a teammate
     * behind a wall is exactly the problem it solves.
     */
    private static boolean canMark(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        return !(target instanceof Player other) || !other.isSpectator();
    }
}
