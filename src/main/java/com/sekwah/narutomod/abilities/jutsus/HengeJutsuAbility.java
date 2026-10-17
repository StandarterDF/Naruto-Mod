package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Henge: the transformation technique, used to wear somebody else's face.
 *
 * <p>Cast at the player you are looking at, it copies their skin and their name onto you for a
 * minute. Nothing about the caster changes underneath - the same inventory, the same health, the same
 * reach - so it is a disguise rather than a shape change, and its whole value is that other players
 * see whoever you picked instead of you. The rendering side of that lives in {@code HengeRenderEvents}
 * on the client; this class only records who is being copied.</p>
 *
 * <p>Casting it again while disguised takes the disguise off, whichever way the ninja is looking, so
 * a henge can always be dropped without needing a target. It is worthless in a fight and deliberately
 * so: the disguise does not change what mobs think of the caster.</p>
 */
public class HengeJutsuAbility extends Ability implements Ability.Cooldown {

    /** V C V */
    public static final long COMBO = 212L;

    /** Chakra for one cast. */
    public static final float CHAKRA_COST = 30.0f;

    /** How far the ninja can reach to copy a face. */
    public static final double RANGE = 16.0d;

    /** How long a disguise lasts, in ticks. */
    public static final int DURATION = 20 * 60;

    private static final int COOLDOWN = 20 * 5;

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
        if (!NarutoConfig.hengeEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.henge.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Already wearing a face: this cast takes it off, and needs neither a target nor chakra.
        if (ninjaData.isHenged()) {
            return true;
        }
        if (findTarget(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.henge.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (ninjaData.getChakra() < CHAKRA_COST) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_COST, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ninjaData.isHenged()) {
            clear(player, ninjaData);
            return;
        }
        Player target = findTarget(player);
        if (target == null) {
            // The check in handleCost already passed; nothing to copy, so nothing happens.
            return;
        }
        ninjaData.setHengeTarget(target.getUUID().toString());
        ninjaData.setHengeName(target.getName().getString());
        ninjaData.setHengeTicks(DURATION);

        player.displayClientMessage(Component.translatable("jutsu.henge.start",
                target.getName().copy().withStyle(ChatFormatting.LIGHT_PURPLE))
                .withStyle(ChatFormatting.DARK_PURPLE), true);

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                    player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                    20, 0.4d, 0.6d, 0.4d, 0.02d);
            level.playSound(null, player, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 1.3f);
        }
    }

    /** Takes the disguise off, whether it ran out or the ninja asked for it back. */
    public static void clear(Player player, INinjaData ninjaData) {
        boolean announced = ninjaData.isHenged();
        ninjaData.setHengeTarget("");
        ninjaData.setHengeName("");
        ninjaData.setHengeTicks(0);
        if (announced) {
            player.displayClientMessage(Component.translatable("jutsu.henge.end")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /**
     * Counts the disguise down, called every server tick from {@code PlayerEvents#onEntityUpdate}.
     * Running out is silent apart from the message: there is nothing to undo on the server.
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        if (!ninjaData.isHenged()) {
            return;
        }
        ninjaData.setHengeTicks(ninjaData.getHengeTicks() - 1);
        if (ninjaData.getHengeTicks() <= 0) {
            clear(player, ninjaData);
        }
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /** @return the player the ninja is looking at, or null when they are looking at anything else. */
    @Nullable
    public static Player findTarget(Player player) {
        HitResult hit = player.pick(RANGE, 0.0f, false);
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player target
                && target != player && target.isAlive()) {
            return target;
        }
        return null;
    }
}
