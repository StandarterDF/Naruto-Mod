package com.sekwah.narutomod.abilities.utility;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shunshin: the body flicker.
 *
 * <p>The first movement every ninja learns after walking. It is not a teleport - the caster covers
 * the ground in a straight line and stops at the first thing solid enough to stop a body - and it is
 * deliberately cheap and short: a way to break a line of sight, close a gap or step out of a bad
 * angle without spending a real technique on it.</p>
 */
public class ChakraDashAbility extends Ability implements Ability.Cooldown {

    /** B V - short, because the flicker is used mid-combo. */
    public static final long COMBO = 21L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_dash");

    /** How far one flicker carries. */
    private static final double DISTANCE = 6.0d;

    /** The largest step that still counts as one body's worth of movement. */
    private static final double STEP = 0.45d;

    private static final float COST = 20.0f;
    private static final int COOLDOWN = 60;

    @Override
    public int noiseRadius() {
        return 6;
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
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 10);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        if (flat.lengthSqr() < 1.0e-4d) {
            return;
        }
        flat = flat.normalize();
        Vec3 start = player.position();
        Vec3 cursor = start;
        double travelled = 0.0d;
        while (travelled < DISTANCE) {
            Vec3 next = cursor.add(flat.scale(STEP));
            AABB box = player.getBoundingBox().move(next.subtract(start));
            if (!level.noCollision(player, box)) {
                break;
            }
            cursor = next;
            travelled += STEP;
        }
        if (travelled <= 0.0d) {
            return;
        }
        player.teleportTo(cursor.x, cursor.y, cursor.z);
        player.fallDistance = 0.0f;
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 0.4d, start.z, 12, 0.3d, 0.4d, 0.3d, 0.02d);
        level.sendParticles(ParticleTypes.CLOUD, cursor.x, cursor.y + 0.4d, cursor.z, 12, 0.3d, 0.4d, 0.3d, 0.02d);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5f, 1.7f);
    }
}
