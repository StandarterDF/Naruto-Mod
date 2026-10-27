package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sparring with a copy of yourself: a shadow clone is taken aside and both bodies train against each
 * other.
 *
 * <p>A clone is the cheapest training partner a ninja can have and the only one that never gets
 * tired, so this hold does the bookkeeping of a spar rather than the fighting: the copy is marked as
 * sparring for as long as the seal is kept, both halves of the pair learn from the exchange, and the
 * copy's blows are pulled (see {@code SummonMedicineEvents}) so a training bout cannot turn into a
 * funeral. The mark is what other code reads; nothing here decides how hard a clone hits.</p>
 *
 * <p>The partner is caught once, when the seal is made, and held for the rest of the channel - a
 * caster cannot swap partners mid-exchange to farm credit from a whole squad. It has to be one of the
 * caster's own clones: training against somebody else's copy is not training your technique.</p>
 */
public class CloneSparringAbility extends Ability implements Ability.Channeled {

    /** V C V V - the shadow clone seal (C B B V) bent towards the training branch. */
    public static final long COMBO = 1222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clone_sparring");

    /**
     * Marks a clone as sparring, with the game time it stops being a spar. The damage hook in
     * {@code SummonMedicineEvents} reads this, so the name is shared rather than private.
     */
    public static final String SPARRING_UNTIL_KEY = "narutomod:sparring_until";

    /** How far a partner can be picked up from. */
    private static final double RANGE = 8.0d;

    /**
     * How far the partner may drift before the spar is broken off. Wider than the pick-up range so a
     * clone that steps back from a blow does not end the session.
     */
    private static final double LEASH = 12.0d;

    /** Chakra burnt for every tick the spar is kept up. */
    private static final float CHAKRA_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How long one session may run before it has to be started again. */
    private static final int MAX_TICKS = 400;

    /**
     * How long the mark lasts past the last tick of the channel. Long enough to cover the gap
     * between two server ticks, short enough that the copy stops pulling its blows the moment the
     * caster lets go.
     */
    private static final int MARK_HOLD_TICKS = 40;

    /** Ticks between the two mastery payments of a spar. */
    private static final int MASTERY_INTERVAL = 20;

    /** A spar is quiet work, not a battle cry. */
    private static final int NOISE_RADIUS = 8;

    /**
     * The partner of every caster currently holding the seal. The ability is a registry singleton,
     * so this is the one place that state lives; it is dropped when the channel ends.
     */
    private final Map<UUID, UUID> partners = new HashMap<>();

    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A spar has to be held: a tap would mark a clone for a single exchange and cost the same. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0 || chargeAmount >= MAX_TICKS) {
            return false;
        }

        ShadowCloneEntity partner;
        if (chargeAmount == 0) {
            partner = this.findPartner(player);
            if (partner == null) {
                player.displayClientMessage(Component.translatable("jutsu.fail.sparring.noclone",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            this.partners.put(player.getUUID(), partner.getUUID());
        } else {
            partner = this.partner(player);
            if (partner == null) {
                // The copy popped or wandered off: end the spar quietly rather than charging for a
                // partner that is no longer there.
                return false;
            }
        }

        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        ShadowCloneEntity partner = this.partner(player);
        if (partner == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        // The mark is refreshed rather than written once, so the spar ends by itself when the seal
        // does instead of leaving a copy stuck in a training stance it was never released from.
        partner.getPersistentData().putLong(SPARRING_UNTIL_KEY, level.getGameTime() + MARK_HOLD_TICKS);

        if (ticksChanneled % MASTERY_INTERVAL == 0) {
            this.credit(player, ninjaData);
            // A spar is two bodies learning. The copy has no pool of its own, so its half is paid
            // into the caster's mastery - which is exactly who benefits from a better trained copy.
            Player owner = partner.getOwner();
            if (owner != null) {
                owner.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                        .ifPresent(ownerData -> this.credit(owner, ownerData));
            }
        }

        if (ticksChanneled % 4 == 0) {
            level.sendParticles(ParticleTypes.CRIT, partner.getX(),
                    partner.getY() + partner.getBbHeight() * 0.6d, partner.getZ(),
                    6, 0.35d, 0.45d, 0.35d, 0.05d);
            level.sendParticles(ParticleTypes.CRIT, player.getX(),
                    player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                    4, 0.3d, 0.4d, 0.3d, 0.05d);
        }
    }

    /**
     * The work happens tick by tick, so letting go has nothing left to do; the mark runs out on its
     * own within {@link #MARK_HOLD_TICKS}.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.partners.remove(player.getUUID());
    }

    /**
     * One half of a spar's mastery: a fifteenth of a cast, paid every {@link #MASTERY_INTERVAL}
     * ticks. Called by this class for both bodies and by the damage hook when the copy lands a blow,
     * which is why it is public and not the raw credit method.
     */
    public void credit(Player player, INinjaData ninjaData) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK * MASTERY_INTERVAL);
    }

    /** A spar is mastered by the time spent in it, not by the act of starting it. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * The partner the caster is already sparring with, or null once it has popped, changed hands or
     * been left too far behind. The search is by id rather than by distance so the pair never swaps
     * to whichever copy happens to be closest.
     */
    private ShadowCloneEntity partner(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID partnerId = this.partners.get(player.getUUID());
        if (partnerId == null) {
            return null;
        }
        List<ShadowCloneEntity> found = level.getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(LEASH),
                clone -> clone.getUUID().equals(partnerId) && clone.isAlive() && clone.isOwnedBy(player));
        if (found.isEmpty()) {
            this.partners.remove(player.getUUID());
            return null;
        }
        return found.get(0);
    }

    /**
     * Picks the partner for a fresh spar: the copy under the crosshair if it is one of the caster's,
     * otherwise the closest of their own copies in reach.
     */
    private ShadowCloneEntity findPartner(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        if (Targeting.lookedAtEntity(player, RANGE) instanceof ShadowCloneEntity aimed
                && isTrainable(player, aimed)) {
            return aimed;
        }
        List<ShadowCloneEntity> own = level.getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(RANGE), clone -> isTrainable(player, clone));
        return own.stream()
                .min(Comparator.comparingDouble(clone -> clone.distanceToSqr(player)))
                .orElse(null);
    }

    /** A sparring partner has to be the caster's own fighting copy, not a worker or a meditation. */
    private static boolean isTrainable(Player caster, ShadowCloneEntity clone) {
        return clone.isAlive() && clone.isOwnedBy(caster) && !clone.isMeditating();
    }
}
