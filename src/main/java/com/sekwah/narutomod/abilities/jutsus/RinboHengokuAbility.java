package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Rinbo Hengoku: an echo of the caster that stands somewhere between the world and the grave.
 *
 * <p>What the echo does is not fight. Every blow that lands while it is out is struck twice - once
 * by the caster, and once a quarter of a second later by the echo, at half the weight. The second
 * blow is not a copy of the first: the target has had a moment to move, and the echo is invisible,
 * so there is nothing to read in the air before it lands.</p>
 *
 * <p>The echo is a bare shadow clone with its mind switched off: it does not walk, does not choose
 * targets and cannot be seen or heard. The clone is used only because it is already an entity that
 * looks like its caster and belongs to nobody else; all of the technique is in the timing done by
 * {@code RinneganExtraEvents}.</p>
 */
public class RinboHengokuAbility extends Ability implements Ability.Cooldown {

    /** An echo is silent; only the blow it repeats is heard. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 2 3 3 3 1 1 - the Rinnegan stance with a different tail to the outer paths. */
    public static final long COMBO = 2332L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinbo_hengoku");

    /** What the echo costs, and how long it takes for the caster to be able to call another. */
    private static final float COST = 85.0f;
    private static final int COOLDOWN = 640;

    /** How long the echo stays out. */
    public static final int LIFETIME = 160;

    /** How far from the caster the echo may be and still repeat a blow. */
    public static final double ECHO_RANGE = 32.0d;

    /** Ticks between the caster's blow and the echo's. */
    public static final int REPEAT_DELAY = 5;

    /** The share of the caster's blow the echo repeats. */
    public static final float REPEAT_SHARE = 0.5f;

    /** The mark that says a clone is an echo rather than a fighting clone. */
    public static final String ECHO_TAG = "narutomod:rinbo_echo";

    /** The game time at which the echo is taken back. */
    public static final String UNTIL_TAG = "narutomod:rinbo_until";

    /** Ticks before the caster's own regeneration resumes after paying for the echo. */
    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
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
        // One echo at a time: recasting dismisses the old one rather than stacking a second and
        // doubling every blow.
        discardEchoes(level, player);

        ShadowCloneEntity echo = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        echo.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
        echo.setOwner(player);
        echo.setInvisible(true);
        echo.setSilent(true);
        // The echo never acts on its own; every blow it lands is written by the event hook.
        echo.setNoAi(true);
        echo.getPersistentData().putBoolean(ECHO_TAG, true);
        echo.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + LIFETIME);
        level.addFreshEntity(echo);

        level.sendParticles(ParticleTypes.SOUL, player.getX(),
                player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                24, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.5f, 0.6f);
    }

    /** Removes the caster's old echoes, if any are still standing. */
    private static void discardEchoes(ServerLevel level, Player caster) {
        for (ShadowCloneEntity old : level.getEntitiesOfClass(ShadowCloneEntity.class,
                caster.getBoundingBox().inflate(ECHO_RANGE),
                clone -> clone.getPersistentData().getBoolean(ECHO_TAG)
                        && clone.getOwnerUUID().map(id -> id.equals(caster.getUUID())).orElse(false))) {
            old.discard();
        }
    }
}
