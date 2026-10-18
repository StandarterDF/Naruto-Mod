package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.CooldownTickEvent;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.capabilities.mastery.MeditationData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Sits two shadow clones down to meditate, feeding chakra back into their caster.
 *
 * <p>The clones themselves are ordinary {@link com.sekwah.narutomod.entity.ShadowCloneEntity clones}
 * built from {@link ShadowCloneJutsuAbility#CHAKRA_MEDITATION_SETTINGS}: unarmed, unable to move and
 * with nothing to do but sit where they were spawned. While the caster stays within range of one of
 * them, they get their chakra back far quicker than they would on their own and any jutsu still on
 * cooldown comes down twice as fast, which is what the technique is really for.</p>
 *
 * <p>That range is not fixed: it grows hard with mastery, from a few steps up to many chunks, so a
 * seasoned ninja can leave their clones behind and still draw on them. Past the passive threshold,
 * every meditating clone keeps feeding a little capacity even when it is out of range entirely.</p>
 *
 * <p>Nothing here scans the world for the clones - at a full radius that would be thousands of
 * chunks per tick. Each clone lists itself with its caster as it ticks, see {@link MeditationData}.</p>
 *
 * <p>The trick lasts three minutes and then cannot be repeated for a minute, so it is a refill to
 * fall back on rather than something to keep running - though three minutes of doubled chakra and
 * halved cooldowns is a long time to make use of one.</p>
 */
public class ChakraMeditationJutsuAbility extends ShadowCloneJutsuAbility {

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
     * know who they are feeding, they only have to list themselves with their caster.</p>
     */
    public static void tickAura(Player player) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            MeditationData links = ninjaData.getMeditationData();
            // Drop the clones whose time has run out, so a chunk that unloaded cannot feed forever.
            links.prune(player.level().getGameTime());

            Ability meditation = NarutoAbilities.CHAKRA_MEDITATION.get();
            double range = Mastery.meditationRadius(ninjaData, meditation);
            float mastery = Mastery.level(ninjaData, meditation);
            ResourceLocation here = player.level().dimension().location();

            int linked = 0;
            int distant = 0;
            for (MeditationData.Link link : links.getLinks().values()) {
                if (!here.equals(link.dimension())) {
                    continue;
                }
                if (player.position().distanceTo(link.position()) <= range) {
                    linked++;
                } else {
                    distant++;
                }
            }

            // Out of range clones still count for capacity once the technique is nearly mastered.
            float passive = mastery >= NarutoConfig.meditationPassiveThreshold
                    ? (float) NarutoConfig.meditationPassiveCapacity * distant
                    : 0f;
            // The extra capacity goes up and down with the clones, whether or not there is any to
            // draw on right now.
            ninjaData.setChakraCapacityBonus(CAPACITY_PER_CLONE * linked + passive);
            if (linked <= 0) {
                return;
            }
            // Sage mode runs on nature energy, not chakra, so while it is up the clones feed that
            // instead - otherwise meditating in sage mode would refill the wrong bar entirely.
            float refill = CHAKRA_PER_CLONE * linked;
            if (ninjaData.isSageMode()) {
                ninjaData.addSeninMana(refill);
            } else {
                ninjaData.addChakra(refill);
            }
            fastForwardCooldowns(ninjaData);
        });
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
