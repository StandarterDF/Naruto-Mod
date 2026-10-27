package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dimensional Step: a step that does not cross the ground in between.
 *
 * <p>The Jougan sees the seams in space, and this is the plainest use of that sight: the caster
 * picks a point along their look ray and is simply there, wall or no wall. The distance is short and
 * fixed, so it is a repositioning tool and not a fast travel; the ray is walked back until there is
 * room for a body, so a step into solid rock puts the caster in front of it rather than inside it.</p>
 *
 * <p>It costs a deep bite of chakra and has a cooldown, because a step through a wall is exactly the
 * kind of thing that gets stronger the more often it can be repeated.</p>
 */
public class DimensionStepAbility extends Ability implements Ability.Cooldown {

    /** A blink is quiet in the chakra sense but unmistakable nearby. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** B V V C B - the Jougan, then one more key. */
    public static final long COMBO = 1321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "dimension_step");

    /** How far the step can carry the caster. */
    private static final double DISTANCE = 12.0d;

    /** What it costs and how long before the eye can do it again. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 100;

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
        if (findArrival(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.dimension_step.nospace",
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
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 arrival = findArrival(player);
        if (arrival == null) {
            return;
        }

        level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                32, 0.3d, 0.5d, 0.3d, 0.4d);
        player.teleportTo(arrival.x, arrival.y, arrival.z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                32, 0.3d, 0.5d, 0.3d, 0.4d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.3f);
        // A step does not fall: arriving a little above the ground should not cost health.
        player.fallDistance = 0.0f;
    }

    /**
     * Where the step would put the caster, or null when there is nowhere along the ray to stand.
     *
     * <p>The look ray is walked from the far end back towards the caster, and the first point with
     * room for the whole body wins. That ordering matters: walking forwards would stop at the near
     * side of the first wall, which reads as "the step failed" when the intent was clearly to get
     * past it.</p>
     */
    private static Vec3 findArrival(Player player) {
        Level level = player.level();
        Vec3 start = player.position();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 target = start.add(direction.scale(DISTANCE));

        for (double travelled = DISTANCE; travelled >= 1.0d; travelled -= 0.5d) {
            Vec3 candidate = start.add(direction.scale(travelled));
            if (level.noCollision(player, player.getBoundingBox().move(candidate.subtract(start)))) {
                return candidate;
            }
        }
        return null;
    }
}
