package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The ninja's call: every clone that can hear it snaps back to the caster's side.
 *
 * <p>Clones are meant to be left behind - a worker posted at a mine or a treeline - and the one thing
 * that makes leaving them behind annoying is not knowing where they wandered off to. This pulls them
 * all home in one cast, fighters and workers alike. Meditating clones are deliberately left where they
 * are: they are sitting still by design, and dragging them across the map would only undo the
 * meditation.</p>
 *
 * <p>A clone in a chunk nobody has loaded is not reachable - there is no entity there to move, it is
 * only remembered by the save - so the call reaches as far as the loaded world does. Anything nearer
 * than that answers at once.</p>
 */
public class CloneRecallAbility extends Ability implements Ability.Cooldown {

    /** V C C */
    public static final long COMBO = 211L;

    /** Chakra for one call. */
    private static final float CHAKRA_COST = 30.0f;

    private static final int COOLDOWN = 20 * 5;

    /** How far away a clone can be and still hear the call, in blocks. */
    private static final double CALL_RANGE = 256.0d;

    /** How far out from the caster a returning clone is placed, in blocks. */
    private static final double ARRIVE_RADIUS = 2.5d;

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
        float cost = this.masteryCost(ninjaData, CHAKRA_COST);
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
        Level level = player.level();
        List<ShadowCloneEntity> clones = level.getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(CALL_RANGE),
                clone -> clone.isOwnedBy(player) && !clone.isMeditating());
        if (clones.isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.recall.none")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int index = 0;
        for (ShadowCloneEntity clone : clones) {
            Vec3 arrival = findArrival(level, player, clone, index, clones.size());
            clone.getNavigation().stop();
            clone.setTarget(null);
            clone.teleportTo(arrival.x, arrival.y, arrival.z);
            clone.setDeltaMovement(Vec3.ZERO);
            clone.fallDistance = 0.0f;
            index++;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2d, player.getZ(),
                    20, ARRIVE_RADIUS, 0.3d, ARRIVE_RADIUS, 0.02d);
        }
        player.displayClientMessage(Component.translatable("jutsu.recall.count", clones.size())
                .withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Finds an open spot for a returning clone: a ring around the caster, widening out until somewhere
     * the clone's own body fits is found, so a recalled clone is not dropped inside a wall to pop.
     */
    private static Vec3 findArrival(Level level, Player player, ShadowCloneEntity clone, int index, int total) {
        int steps = Math.max(1, total);
        for (int ring = 1; ring <= 3; ring++) {
            double radius = ARRIVE_RADIUS * ring;
            for (int step = 0; step < steps; step++) {
                double angle = (Math.PI * 2.0d / steps) * (index + step);
                Vec3 candidate = new Vec3(player.getX() + Math.cos(angle) * radius, player.getY(),
                        player.getZ() + Math.sin(angle) * radius);
                AABB box = clone.getBoundingBox().move(candidate.subtract(clone.position()));
                if (level.noCollision(clone, box)) {
                    return candidate;
                }
            }
        }
        return player.position();
    }
}
