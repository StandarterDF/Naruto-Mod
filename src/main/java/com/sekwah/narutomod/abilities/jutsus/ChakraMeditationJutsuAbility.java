package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.capabilities.CooldownTickEvent;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Sits two shadow clones down to meditate, feeding chakra back into their caster.
 *
 * <p>The clones themselves are ordinary {@link ShadowCloneEntity clones} built from
 * {@link ShadowCloneJutsuAbility#CHAKRA_MEDITATION_SETTINGS}: unarmed, unable to move and with
 * nothing to do but sit where they were spawned. While the caster stays within
 * {@link #LINK_RANGE} of one of them, they get their chakra back far quicker than they would on
 * their own and any jutsu still on cooldown comes down twice as fast, which is what the technique
 * is really for. Walk away and the link simply stops, the clones are not dragged along.</p>
 *
 * <p>The trick lasts three minutes and then cannot be repeated for a minute, so it is a refill to
 * fall back on rather than something to keep running - though three minutes of doubled chakra and
 * halved cooldowns is a long time to make use of one.</p>
 */
public class ChakraMeditationJutsuAbility extends ShadowCloneJutsuAbility {

    /** How close the caster has to stay to a meditating clone to keep drawing on it. */
    public static final double LINK_RANGE = 8.0d;

    /** Chakra pushed into the caster every tick, per meditating clone in range. */
    private static final float CHAKRA_PER_CLONE = 0.2f;

    /**
     * Chakra capacity each meditating clone opens up while it is linked to its caster, on top of
     * the configured maximum. This is where most of the refill comes from: the caster can bank
     * chakra they would not otherwise be able to hold at all.
     */
    private static final float CAPACITY_PER_CLONE = 100.0f;

    public ChakraMeditationJutsuAbility() {
        super(CHAKRA_MEDITATION_SETTINGS);
    }

    /**
     * Runs every tick for every server side player, from {@code PlayerEvents#onEntityUpdate}.
     *
     * <p>Doing it here rather than in the clones keeps it in one place: the clones do not need to
     * know who they are feeding, they only have to exist, be meditating and be close enough.</p>
     */
    public static void tickAura(Player player) {
        int meditating = countMeditatingClones(player);
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            // The extra capacity goes up and down with the clones, whether or not there is any to
            // draw on right now.
            ninjaData.setChakraCapacityBonus(CAPACITY_PER_CLONE * meditating);
            if (meditating <= 0) {
                return;
            }
            // Sage mode runs on nature energy, not chakra, so while it is up the clones feed that
            // instead - otherwise meditating in sage mode would refill the wrong bar entirely.
            float refill = CHAKRA_PER_CLONE * meditating;
            if (ninjaData.isSageMode()) {
                ninjaData.addSeninMana(refill);
            } else {
                ninjaData.addChakra(refill);
            }
            fastForwardCooldowns(ninjaData);
        });
    }

    private static int countMeditatingClones(Player player) {
        return player.level().getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(LINK_RANGE),
                clone -> clone.isMeditating() && clone.isOwnedBy(player)).size();
    }

    /**
     * Ticks every cooldown a second time, so a jutsu left on a twenty second cooldown is ready again
     * in ten. The vanilla tick loop removes finished cooldowns as it goes, hence the copy of the
     * keys to walk through.
     */
    private static void fastForwardCooldowns(INinjaData ninjaData) {
        HashMap<String, CooldownTickEvent> cooldowns = ninjaData.getCooldownEvents();
        if (cooldowns.isEmpty()) {
            return;
        }
        for (String name : new ArrayList<>(cooldowns.keySet())) {
            CooldownTickEvent cooldown = cooldowns.get(name);
            if (cooldown == null) {
                continue;
            }
            cooldown.tick();
            if (cooldown.isComplete()) {
                cooldowns.remove(name);
            }
        }
    }
}
