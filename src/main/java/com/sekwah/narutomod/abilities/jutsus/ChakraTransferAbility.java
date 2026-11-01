package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rinnegan handing chakra across a gap.
 *
 * <p>The eye of the sage is not only a hand that takes. This is the same path turned outwards: a
 * thread of chakra from the caster's pool to every ally close enough to be connected, paid for tick
 * by tick and delivered at the same rate it is drawn. Pools are never averaged or levelled: the
 * allies are simply fed, and the caster keeps what they had. The beam holds only while both bodies
 * stay close enough to be connected.</p>
 */
public class ChakraTransferAbility extends Ability implements Ability.Cooldown {

    /** A shared thread of chakra is quiet work, not a shout. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V V C B B - the Rinnegan stance (V V C V) with the last key changed. */
    public static final long COMBO = 11231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_transfer");

    /** The list of allies the seal currently feeds, stored on the caster. */
    public static final String ALLIES_TAG = "narutomod:chakra_seal_allies";

    /** How far the beam can reach before mastery extends it. */
    public static final double RANGE = 12.0d;

    /** Chakra fed to each sealed ally per second. Pools are not averaged. */
    public static final float ALLY_REGEN_PER_SECOND = 0.5f;

    /** Chakra the caster regains per second while the seal holds. Pools are not averaged. */
    public static final float CASTER_REGEN_PER_SECOND = 0.25f;

    /** Chakra drawn per tick; the allies receive their share from the seal every second. */
    private static final float PER_TICK = 8.0f;

    /** How long one cast may run before the caster has to start it again. */
    private static final int MAX_TICKS = 240;

    /** Ticks before the caster's own regeneration resumes. */
    private static final int REGEN_DELAY = 20;

    @Override
    public int getCooldown() {
        return 40;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** Reach of the seal, widened by mastery. */
    public static double range(INinjaData ninjaData) {
        return RANGE + 24.0d * Mastery.level(ninjaData, ID);
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
        if (chargeAmount >= MAX_TICKS) {
            return false;
        }
        if (allies(player, ninjaData).isEmpty()) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.chakra_transfer.noally",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        float cost = this.masteryCost(ninjaData, PER_TICK);
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Player> allies = allies(player, ninjaData);
        if (allies.isEmpty()) {
            return;
        }
        // Every ally in reach is latched onto the caster as a list of UUIDs, so the seal keeps
        // feeding all of them from one place. The threads are drawn once, on the cast.
        ListTag stored = new ListTag();
        for (Player ally : allies) {
            stored.add(StringTag.valueOf(ally.getUUID().toString()));
            drawThread(level, player, ally);
        }
        player.getPersistentData().put(ALLIES_TAG, stored);
        this.recordMastery(player, ninjaData);
    }

    /**
     * Every player in reach that the caster counts as an ally: teammates and friends, never the
     * caster themselves and never a spectator.
     */
    private static List<Player> allies(Player caster, INinjaData ninjaData) {
        List<Player> found = new ArrayList<>();
        if (caster.level().isClientSide) {
            return found;
        }
        double reach = range(ninjaData);
        double reachSqr = reach * reach;
        for (Player other : caster.level().players()) {
            if (other == caster || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (!isAlly(caster, other)) {
                continue;
            }
            if (other.distanceToSqr(caster) > reachSqr) {
                continue;
            }
            found.add(other);
        }
        return found;
    }

    private static boolean isAlly(Player caster, Player other) {
        return other.isAlliedTo(caster) || Friends.areFriends(caster, other);
    }

    /** One thread of motes from the caster to an ally, so every connection is visible. */
    private static void drawThread(ServerLevel level, Player caster, Player ally) {
        List<Step> steps = Step.between(caster, ally);
        for (Step step : steps) {
            level.sendParticles(ParticleTypes.END_ROD, step.x(), step.y(), step.z(),
                    1, 0.01d, 0.01d, 0.01d, 0.0d);
        }
    }

    /** One point along the beam, used to place the particles. */
    private record Step(double x, double y, double z) {

        private static final int POINTS = 10;

        static List<Step> between(Player from, Player to) {
            Vec3 start = new Vec3(from.getX(), from.getY() + from.getBbHeight() * 0.6d, from.getZ());
            Vec3 end = new Vec3(to.getX(), to.getY() + to.getBbHeight() * 0.6d, to.getZ());
            List<Step> steps = new ArrayList<>(POINTS);
            for (int i = 1; i <= POINTS; i++) {
                Vec3 at = start.add(end.subtract(start).scale((double) i / POINTS));
                steps.add(new Step(at.x, at.y, at.z));
            }
            return steps;
        }
    }
}
