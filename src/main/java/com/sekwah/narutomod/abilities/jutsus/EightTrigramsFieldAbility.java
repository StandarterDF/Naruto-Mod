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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Eight Trigrams Field: the circle the Hyuga fight inside, kept open around the body.
 *
 * <p>A gentle fist does not step into a fight and swing; it stands in a circle and lets the other
 * body come to it. This is that circle made literal and permanent for as long as the chakra holds:
 * the caster's arms reach a block further than anyone's, and every palm that lands inside the field
 * goes through the tenketsu twice as hard, because the strike is no longer only the palm - the field
 * is behind it.</p>
 *
 * <p>The reach rides on Forge's entity attribute on a stable id, so it is added and removed rather
 * than stacked, and it is re-applied every tick so a respawn cannot lose it while the stance is
 * being paid for. The seal itself is the game-time stamp in {@link #UNTIL_TAG}: the parent's palm
 * hook multiplies its drain by {@link #tenketsuMultiplier(Player)}, so the whole technique is one
 * number written here and one hook that reads it.</p>
 */
public class EightTrigramsFieldAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A circle of chakra is not heard through the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V B V C - the eye's seal, then the palm's. */
    public static final long COMBO = 232321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "eight_trigrams_field");

    /**
     * The mark that says the field is up. Public because the parent's palm hook and the client's
     * overhead bars both read it rather than spelling the string again.
     */
    public static final String UNTIL_TAG = "narutomod:eight_trigrams_field_until";

    /** Stable id, so the reach is added and taken off rather than stacking every tick. */
    private static final UUID REACH_MODIFIER_ID = UUID.fromString("8c5a17d4-2e63-4f90-b71a-d4c93e08a256");

    /** One block of reach - the arms of the circle, not a spear. */
    private static final double REACH_BONUS = 1.0d;

    /** What the field does to a palm that lands while it is up. */
    private static final float TENKETSU_MULTIPLIER = 2.0f;

    /** Chakra paid to open the circle, and the trickle for every tick it stays open. */
    private static final float OPEN_COST = 50.0f;
    private static final float UPKEEP = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept while the field is held. */
    private static final int MARK_GRACE = 10;

    /** The circle of chakra drawn on the ground, and how often it is redrawn. */
    private static final double RING_RADIUS = 8.0d;
    private static final int RING_POINTS = 32;
    private static final int RING_INTERVAL = 10;

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
        // The field is a way of standing inside the eye, not a separate gift: the eye has to be open.
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean opening = !isActive(player);
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        applyReach(player);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.level() instanceof ServerLevel level && player.tickCount % RING_INTERVAL == 0) {
            drawRing(level, player);
        }
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applyReach(Player player) {
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null && reach.getModifier(REACH_MODIFIER_ID) == null) {
            reach.addTransientModifier(new AttributeModifier(REACH_MODIFIER_ID, "narutomod:eight_trigrams_field",
                    REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * How much a landed palm counts for while the field is up. One when the caster is not holding
     * the stance, so the parent's hook can multiply unconditionally.
     */
    public static float tenketsuMultiplier(Player player) {
        return isActive(player) ? TENKETSU_MULTIPLIER : 1.0f;
    }

    /** @return whether the field is up around this caster right now. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is running, read from the synced toggle set. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** A wide circle of pale chakra on the ground, drawn as the field is walked. */
    private static void drawRing(ServerLevel level, Player player) {
        for (int point = 0; point < RING_POINTS; point++) {
            double angle = Math.PI * 2.0d * point / RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD,
                    player.getX() + Math.sin(angle) * RING_RADIUS,
                    player.getY() + 0.1d,
                    player.getZ() + Math.cos(angle) * RING_RADIUS,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
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
        // The reach is synced as an attribute and the circle is drawn by the server; nothing to keep.
    }

    /** Dropping the field puts the arms back to their ordinary length and takes the mark with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null) {
            reach.removeModifier(REACH_MODIFIER_ID);
        }
    }
}
