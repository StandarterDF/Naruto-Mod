package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water Release: irrigation.
 *
 * <p>The other half of what Wood Release does for a field. The caster pulls water out of the air and
 * puts it into the soil around them, soaking every tilled block within a few paces. Nothing grows
 * from it directly - the ground is simply made wet, and whatever is planted there takes care of the
 * rest on its own.</p>
 *
 * <p>It is a farmer's technique with a farmer's price: no target, no range to aim, just a small pool
 * of chakra spent on the ground at the caster's feet. Casting it in a desert or on bare stone does
 * nothing and, since the soil is checked before the chakra is taken, costs nothing either.</p>
 */
public class WateringAbility extends Ability implements Ability.Cooldown {

    /** B A V A A - water release, the second of the basic elements. */
    public static final long COMBO = 12231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "suiton_watering");

    /** How far the water reaches, and how deep above and below the caster it soaks. */
    private static final int RADIUS = 4;
    private static final int VERTICAL = 2;

    private static final float COST = 15.0f;
    private static final int COOLDOWN = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    @Override
    public int noiseRadius() {
        return 12;
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
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!hasDryFarmland(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.suiton_watering.nofarmland",
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

        int watered = 0;
        BlockPos centre = player.blockPosition();
        for (BlockPos pos : area(centre)) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.FARMLAND) || state.getValue(FarmBlock.MOISTURE) >= FarmBlock.MAX_MOISTURE) {
                continue;
            }
            BlockPos target = pos.immutable();
            level.setBlockAndUpdate(target, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
            watered++;
            level.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5d,
                    target.getY() + 1.0d, target.getZ() + 0.5d, 4, 0.3d, 0.2d, 0.3d, 0.0d);
        }

        if (watered > 0) {
            level.playSound(null, player, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }

    /** The block range the water reaches, taken around the caster rather than around a target. */
    private static Iterable<BlockPos> area(BlockPos centre) {
        return BlockPos.betweenClosed(
                centre.offset(-RADIUS, -VERTICAL, -RADIUS),
                centre.offset(RADIUS, VERTICAL, RADIUS));
    }

    /** Whether any tilled soil nearby is still dry, asked before the caster is charged. */
    private static boolean hasDryFarmland(Player player) {
        for (BlockPos pos : area(player.blockPosition())) {
            BlockState state = player.level().getBlockState(pos);
            if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                return true;
            }
        }
        return false;
    }
}
