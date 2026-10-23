package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.jutsuprojectile.ChibakuTenseiEntity;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chibaku Tensei: the Rinnegan's answer to a battlefield, shaped as a ball of its own ground.
 *
 * <p>It is channelled because the ball has to be built. The longer the seals are held the heavier
 * the thing that comes down; a quick tap still builds the smallest ball rather than fizzling, so
 * the technique always does something. Where it lands is where the caster was looking - the ground
 * under the crosshair is the ground that gets taken - so the seals are aimed, not dropped on the
 * caster's own feet.</p>
 *
 * <p>The technique itself ends here: the ball is a {@link ChibakuTenseiEntity}, which climbs, pulls,
 * shakes and finally drops on its own, whether the caster stays to watch or not. The eye has to be
 * open for it, see {@link RinneganAbility}.</p>
 */
public class ChibakuTenseiAbility extends Ability implements Ability.Channeled {

    /**
     * How far a cast of this technique carries as a game event. The ball landing is heard across a
     * village.
     */
    @Override
    public int noiseRadius() {
        return 64;
    }

    /** V B B C V B C - the long seal chain, as it is in 1.7.10. */
    public static final long COMBO = 2331231L;

    /** Ticks of channelling before the ball is worth making, and where it stops growing. */
    private static final int MIN_CHARGE = 40;
    private static final int FULL_CHARGE = 80;

    /** Chakra burnt for every tick it is held, and the lump paid when the ball is released. */
    private static final float CHAKRA_PER_TICK = 1.2f;
    private static final float RELEASE_COST = 40.0f;

    /** How far away the ground under the crosshair can be. */
    private static final double AIM_RANGE = 32.0d;

    /** How far ahead the ball is built when the caster is looking at open sky. */
    private static final double AIM_FALLBACK = 12.0d;

    /** What the charge is worth to the ball, from a barely held one up to a full one. */
    private static final float MIN_POWER = 0.7f;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.chibakuTenseiEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chibaku.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    /** The ball forming above the caster's hands while the seals are held. */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 4, 0.2d, 0.2d, 0.2d, 0.02d);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                player.getY() + player.getBbHeight() * 1.1d, player.getZ(),
                3, 0.6d, 0.2d, 0.6d, 0.05d);
        if (ticksChanneled % 5 == 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                    player.getY() + player.getBbHeight(), player.getZ(),
                    1, 0.4d, 0.2d, 0.4d, 0.0d);
        }
        if (ticksChanneled > 0 && ticksChanneled % 10 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.25f, 0.5f);
        }
    }

    /**
     * Builds the ball: how big it ends up is decided here from how long the seals were held, and
     * where it is built is the ground the caster was looking at.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A tap arrives as -1 (and a stop may arrive a tick or two into the hold). Both are still
        // a cast: build the smallest ball instead of returning early, which is what made the
        // technique look like it did nothing when it was only tapped.
        int held = Math.max(0, ticksActive);
        float releaseCost = this.masteryCost(ninjaData, RELEASE_COST);
        if (ninjaData.getChakra() < releaseCost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return;
        }
        ninjaData.useChakra(releaseCost, 20 * 2);

        float power = Mth.clamp((held - MIN_CHARGE) / (float) (FULL_CHARGE - MIN_CHARGE), 0.0f, 1.0f);
        BlockPos ground = this.aimPoint(level, player);
        ChibakuTenseiEntity ball = new ChibakuTenseiEntity(player,
                ground.getX() + 0.5d, ground.getY(), ground.getZ() + 0.5d);
        ball.setPower(MIN_POWER + (1.0f - MIN_POWER) * power);
        level.addFreshEntity(ball);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 0.45f);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                player.getY() + player.getBbHeight(), player.getZ(),
                40, 0.8d, 0.5d, 0.8d, 0.2d);
    }

    /**
     * The first free block above the ground under the crosshair, or a point a little ahead when the
     * caster is looking at the sky.
     */
    private BlockPos aimPoint(ServerLevel level, Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, AIM_RANGE);
        int x;
        int z;
        if (hit != null) {
            x = hit.getBlockPos().getX();
            z = hit.getBlockPos().getZ();
        } else {
            Vec3 ahead = player.position().add(player.getLookAngle().scale(AIM_FALLBACK));
            x = Mth.floor(ahead.x);
            z = Mth.floor(ahead.z);
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
