package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.JouganExtraEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Jougan Seal Portal: the eye sees the seam a portal is cut along and holds it shut.
 *
 * <p>A portal is a wound in space with obsidian holding its edges apart. The eye does not break the
 * frame - the obsidian stays exactly where the builder left it - it simply sews the opening closed
 * for a while, which is why the technique is worth using instead of a bucket of water. Everything
 * that would have stepped through finds nothing to step through, and an ender pearl thrown at the
 * sealed spot is refused for as long as the seam holds.</p>
 *
 * <p>The seal is temporary by design: a door that could be closed permanently from a distance would
 * be a griefing tool, and the eye is a scout's tool, not a lock. Nether portals, the End's own
 * gateway and the exit portal are all seams and all answer to it, which is what makes the technique
 * useful in a chase rather than only at home.</p>
 */
public class SealPortalAbility extends Ability implements Ability.Cooldown {

    /** A portal going quiet is audible, though not loud. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B V V V V C - the Jougan's stasis branch, one key further on. */
    public static final long COMBO = 322221L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "seal_portal");

    /** How far from the caster a seam can be sewn shut. */
    private static final int RANGE = 12;

    private static final float COST = 30.0f;
    private static final int COOLDOWN = 400;

    /** How long the seam stays sewn, in ticks. */
    public static final int SEAL_TICKS = 600;

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
        if (!(player.level() instanceof ServerLevel level) || !hasPortalNear(level, player.blockPosition())) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_portal.nothing",
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long until = level.getGameTime() + SEAL_TICKS;
        List<BlockPos> sewn = JouganExtraEvents.sealPortals(level, player.blockPosition(), RANGE, until);
        if (sewn.isEmpty()) {
            return;
        }

        // The closing itself is drawn where the opening was, one puff per block, so the caster can
        // see how much of the portal the eye actually reached.
        for (BlockPos pos : sewn) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                    6, 0.35d, 0.45d, 0.35d, 0.02d);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /** @return whether any portal block sits inside the reach of a cast from {@code center}. */
    private static boolean hasPortalNear(ServerLevel level, BlockPos center) {
        BlockPos min = center.offset(-RANGE, -RANGE, -RANGE);
        BlockPos max = center.offset(RANGE, RANGE, RANGE);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.isOutsideBuildHeight(pos)) {
                continue;
            }
            if (JouganExtraEvents.isPortal(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }
}
