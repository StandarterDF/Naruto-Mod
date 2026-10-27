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
import net.minecraft.world.entity.player.Player;

/**
 * Eight Trigrams: Circular Block, the guard the eye keeps while the hands are busy.
 *
 * <p>Rotation answers everything at once and is gone in a moment; a fight is not one moment. This is
 * the cheap version kept open instead: chakra is walked around the body in a tight circle, and when
 * something crosses it - an arrow, a blade, a fist - the circle is likely to have read the line
 * before it arrived. Two times in five the blow simply does not land, and whatever threw it is
 * shoved back off the circle.</p>
 *
 * <p>It is a chance, not a wall: the guard costs a trickle per tick, breaks nothing, and leaves the
 * caster's own hands free for the palms, which is the whole reason a Hyuga would hold it. The mark
 * in the caster's persistent data is what the refusal hook in {@code ByakuganAdvancedEvents} reads,
 * so it lapses with the stance even if the client never sends a stop.</p>
 */
public class CircularBlockAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A circle of chakra moving around the body is not heard through the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V B V B V - the eye's seal, then the four keys of the circle. */
    public static final long COMBO = 11112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "circular_block");

    /**
     * Game time at which the guard lapses if it stops being fed. Public because the refusal hook in
     * {@code ByakuganAdvancedEvents} reads it rather than spelling the string again.
     */
    public static final String UNTIL_TAG = "narutomod:circular_block_until";

    /** Chakra burnt for every tick the circle is being walked. */
    private static final float CHAKRA_PER_TICK = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How far ahead of the current tick the mark is kept. A couple of ticks of slack means a lost
     * packet or a lagging tick cannot open a hole in a guard that is still being paid for.
     */
    private static final int MARK_GRACE = 10;

    /** The circle itself: its radius, how many points are drawn, and how fast it turns. */
    private static final double RING_RADIUS = 1.3d;
    private static final int RING_POINTS = 8;
    private static final int RING_INTERVAL = 3;
    private static final double RING_SPIN = 0.25d;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The guard is a use of the eye's picture, not a separate gift: the eye has to be open.
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A stance is mastered by holding it, not by flicking it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.level() instanceof ServerLevel level && player.tickCount % RING_INTERVAL == 0) {
            drawRing(level, player);
        }
    }

    /** @return whether the circle is being walked around this caster right now. */
    public static boolean isActive(Player player) {
        long until = player.getPersistentData().getLong(UNTIL_TAG);
        return until > 0L && until > player.level().getGameTime();
    }

    /**
     * A stance is mastered by the time it is up, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The ring is drawn by the server and the refusal is the server's roll; nothing to keep here.
    }

    /** Dropping the guard takes the mark with it, so no blow is refused after the stance has ended. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** The turning circle of pale chakra, drawn around the caster's chest. */
    private static void drawRing(ServerLevel level, Player player) {
        double phase = player.tickCount * RING_SPIN;
        double y = player.getY() + player.getBbHeight() * 0.55d;
        for (int point = 0; point < RING_POINTS; point++) {
            double angle = phase + Math.PI * 2.0d * point / RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX() + Math.sin(angle) * RING_RADIUS,
                    y,
                    player.getZ() + Math.cos(angle) * RING_RADIUS,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }
}
