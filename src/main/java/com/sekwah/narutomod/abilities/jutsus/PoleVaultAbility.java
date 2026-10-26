package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * The vault a ninja takes over a wall, a fence or a gap without breaking stride.
 *
 * <p>It is not a jump: there is no crouch and no hang time, just a hard shove off the ground and a
 * body carried forward over whatever is in the way. What it needs is something to push against, and
 * a ninja always has one of two: the blade in their hands, planted for a heartbeat, or the ground
 * itself. With neither - swimming, falling, hanging in the air - the technique has nothing to lever
 * against and refuses.</p>
 *
 * <p>All it does is move the caster. No damage, no guard, no trail to follow: the vault is meant to
 * be the cheap way past an obstacle so the chakra can be kept for the fight on the far side.</p>
 */
public class PoleVaultAbility extends Ability implements Ability.Cooldown {

    /** A vault is quiet: the landing is the only thing anyone hears. */
    @Override
    public int noiseRadius() {
        return 4;
    }

    /** B C B A A A - the taijutsu branch's movement seal. */
    public static final long COMBO = 132111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "pole_vault");

    /** Stamina paid for the push, and how long the body needs before another one. */
    private static final float COST = 15.0f;
    private static final int STAMINA_REGEN_DELAY = 20;
    private static final int COOLDOWN = 160;

    /** Forward speed along the look and the lift of the vault, in blocks per tick. */
    private static final double FORWARD = 0.55d;
    private static final double LIFT = 0.55d;

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
        if (!hasLeverage(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.pole_vault.noleverage",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 forward = horizontalLook(player);
        player.setDeltaMovement(forward.x * FORWARD, LIFT, forward.z * FORWARD);
        // The whole point of the vault is that the drop on the far side is not a fall.
        player.fallDistance = 0.0f;
        player.hurtMarked = true;

        level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1d, player.getZ(),
                16, 0.35d, 0.1d, 0.35d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * A vault needs a pole or a floor: a blade in either hand counts as the pole, and a solid block
     * underfoot as the floor. Both are checked on the server, where the cost is paid.
     */
    private static boolean hasLeverage(Player player) {
        if (player.getMainHandItem().getItem() instanceof SwordItem
                || player.getOffhandItem().getItem() instanceof SwordItem) {
            return true;
        }
        BlockPos below = player.blockPosition().below();
        return player.level().getBlockState(below).isFaceSturdy(player.level(), below, Direction.UP);
    }

    /** The look vector flattened onto the ground, so a glance at the sky still vaults forwards. */
    private static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        return flat.lengthSqr() < 1.0E-4d ? Vec3.ZERO : flat.normalize();
    }
}
