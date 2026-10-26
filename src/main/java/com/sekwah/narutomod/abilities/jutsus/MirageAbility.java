package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Magen: Mirage - the shape a hunted ninja leaves where they were standing.
 *
 * <p>Not a clone: nothing is copied and nothing is carried. It is a picture of the caster, held for
 * a few seconds, that a frightened animal or a simple soldier will spend those seconds on. Anything
 * that can see through an illusion - another eye, a boss with a mind of its own - ignores it, which
 * is the whole difference between a mirage and a decoy.</p>
 */
public class MirageAbility extends Ability implements Ability.Cooldown {

    /** C B C C B - next to the other illusions. */
    public static final long COMBO = 13134L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mirage");

    private static final float COST = 25.0f;
    private static final int COOLDOWN = 200;
    private static final int DURATION = 160;

    @Override
    public int noiseRadius() {
        return 0;
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
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "jutsu.fail.notenoughchakra",
                    net.minecraft.network.chat.Component.translatable(this.getTranslationKey(ninjaData))
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)), true);
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
        ShadowCloneEntity mirage = new ShadowCloneEntity(level, player, DURATION, false);
        mirage.setInvisible(true);
        mirage.setSilent(true);
        mirage.getPersistentData().putBoolean("narutomod:mirage", true);
        mirage.getPersistentData().putLong("narutomod:mirage_until", level.getGameTime() + DURATION);
        level.addFreshEntity(mirage);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5d, player.getZ(),
                16, 0.4d, 0.5d, 0.4d, 0.02d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.0f);
    }
}
