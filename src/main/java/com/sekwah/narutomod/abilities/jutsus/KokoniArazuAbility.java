package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Magen: Kokoni Arazu - the illusion that there is nothing here worth looking at.
 *
 * <p>It is a room with no ninja in it. Every creature close to the caster is shown an empty stretch of
 * ground where the caster is standing: whatever it was fighting is forgotten, and the caster cannot
 * be picked out of the scene again while the stance lasts. It hurts nothing and holds nobody - it
 * simply takes the caster out of the fight for as long as they can keep paying for it, which is what
 * a genjutsu ninja wants when the fight is not one they intend to have.</p>
 *
 * <p>The technique is quiet, needs no eye and no target, and is capped at twelve seconds: an illusion
 * that could be worn forever would be an invisibility spell, and this is meant to be a way past a
 * room, not a way to live in one. The upkeep itself - dropping targets and refusing to let a marked
 * creature choose the caster - lives in {@code GenjutsuAdvancedEvents}; this class only holds the
 * stance and stamps the marks.</p>
 */
public class KokoniArazuAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** Nothing here disturbs the world: an illusion is laid in the mind, not into the air. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C C B B - the genjutsu combination (C B C), then the branch's sixth step. */
    public static final long COMBO = 13122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kokoni_arazu");

    /** Game time at which a marked creature stops being fooled. Refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:kokoni_until";

    /** UUID string of the ninja whose presence is being hidden. Read by the target hook. */
    public static final String CASTER_TAG = "narutomod:kokoni_caster";

    /** How far the illusion is laid around the caster. */
    private static final double RANGE = 12.0d;

    /** Chakra burnt for every tick the stance is up. */
    private static final float COST_PER_TICK = 0.3f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How long a mark outlives the last tick that refreshed it. A couple of seconds, so a creature
     * that walks out of range settles back into its own mind rather than staying blind forever.
     */
    private static final int MARK_TICKS = 40;

    /** The most illusion one body can carry without working at it: twelve seconds. */
    private static final int MAX_TICKS = 240;

    /** How often the witch-light around a caught creature is redrawn. */
    private static final int PARTICLE_INTERVAL = 5;

    /**
     * When each caster's stance began. The ability is a registry singleton and its casters are not,
     * so the cap has to live per caster; it is dropped when the stance ends.
     */
    private final Map<UUID, Long> startedAt = new HashMap<>();

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
        // The stance has run its length: ending it here stops the upkeep before another tick is
        // paid for an illusion that is about to fall away anyway.
        Long started = this.startedAt.get(player.getUUID());
        if (started != null && player.level().getGameTime() - started >= MAX_TICKS) {
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.startedAt.putIfAbsent(player.getUUID(), level.getGameTime());

        long until = level.getGameTime() + MARK_TICKS;
        boolean draw = level.getGameTime() % PARTICLE_INTERVAL == 0L;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE))) {
            // The bounding box is a cube, so its corners reach further than the technique does.
            if (!mob.isAlive() || mob.isSpectator() || mob.distanceToSqr(player) > RANGE * RANGE) {
                continue;
            }
            // The caster's own side is already in on the trick: a clone that lost its target would
            // be the caster fooling themselves.
            if (Friends.isOwnSide(player, mob)) {
                continue;
            }
            mob.getPersistentData().putLong(UNTIL_TAG, until);
            mob.getPersistentData().putString(CASTER_TAG, player.getUUID().toString());
            if (draw) {
                level.sendParticles(ParticleTypes.WITCH, mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.9d, mob.getZ(),
                        4, 0.3d, 0.25d, 0.3d, 0.0d);
            }
        }
    }

    /** A stance is mastered by the time it is held, not by switching it on. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Only the marks are synced, through the creatures that carry them.
    }

    /**
     * The marks are short and expire on their own within {@link #MARK_TICKS}; all that is left is to
     * forget the cap this caster was running on.
     */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        this.startedAt.remove(player.getUUID());
    }

    /** @return whether the caster is hiding their presence behind this illusion right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
