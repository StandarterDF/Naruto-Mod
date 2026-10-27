package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.events.GenjutsuEvents;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Kai: the release word that breaks an illusion, for the ninja and for whoever is standing with them.
 *
 * <p>Genjutsu is the one thing a ninja cannot simply outfight: the body is fine and the mind is not.
 * Kai is the answer to that - a hard snap of chakra that wakes the senses, clears the fog out of the
 * eyes and leaves nothing of the illusion behind. It is cast on the caster's own side of a fight,
 * because freeing the enemy's victims would only undo the caster's work; a creature that has taken
 * the caster for an enemy is left to dream.</p>
 *
 * <p>The freed bodies are marked with a short immunity, so a second illusion cannot simply be laid
 * on them again the next tick. That check is maintained by the genjutsu code, not here: this class
 * only breaks what is already there and stamps the window that follows.</p>
 */
public class KaiAbility extends Ability implements Ability.Cooldown {

    /** The release word is heard as far as a shout. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** C B C C C - the genjutsu combination (C B C) and two more keys. */
    public static final long COMBO = 2213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kai");

    /** What breaking an illusion costs, and how long before it can be done again. */
    private static final float COST = 25.0f;
    private static final int COOLDOWN = 160;

    /** Who is close enough to be woken with the caster. */
    private static final double RANGE = 8.0d;

    /** Two seconds in which a freed mind cannot be taken again. */
    public static final int IMMUNITY_TICKS = 40;

    /**
     * The tag the genjutsu side reads before it lays a new illusion down. Public because the check
     * lives outside this class.
     */
    public static final String IMMUNITY_TAG = "narutomod:kai_immunity_until";

    /** Radius of the ring drawn on the ground, and how many motes go into it. */
    private static final double RING_RADIUS = 2.5d;
    private static final int RING_POINTS = 28;

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
        // No eye and no target: Kai is a shout, and a shout that only works when something is there
        // to hear it is no answer to being caught. The price is the only condition.
        float cost = this.masteryCost(ninjaData, COST);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        long immunityUntil = level.getGameTime() + IMMUNITY_TICKS;

        // The caster is their own first patient: whether or not anything tracked them as under an
        // illusion, their own sight is cleared and their own mind is closed to the next one.
        clearSenses(player, immunityUntil);

        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            if (!isOnCastersSide(player, living)) {
                continue;
            }
            if (living instanceof Mob mob && GenjutsuEvents.isUnderGenjutsu(mob)) {
                GenjutsuEvents.clear(mob);
                clearSenses(living, immunityUntil);
            }
        }

        drawRing(level, player);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /**
     * Opens a body's eyes again and shuts the door behind it for a moment. The immunity is stored on
     * the body rather than in a map so it survives a chunk unload, exactly as the illusion it is
     * guarding against does.
     */
    private static void clearSenses(LivingEntity body, long immunityUntil) {
        body.getPersistentData().putLong(IMMUNITY_TAG, immunityUntil);
        body.removeEffect(MobEffects.BLINDNESS);
        body.removeEffect(MobEffects.DARKNESS);
        body.removeEffect(MobEffects.CONFUSION);
    }

    /**
     * @return whether the creature is one the caster would wake: themselves, an ally, or something
     *         that has no quarrel with them. A hostile creature is deliberately left under whatever
     *         holds it - an enemy's illusion is still the caster's ally.
     */
    private static boolean isOnCastersSide(Player caster, LivingEntity candidate) {
        if (candidate == caster || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate.isAlliedTo(caster) || Friends.areFriends(caster, candidate)) {
            return true;
        }
        if (candidate instanceof Enemy) {
            return false;
        }
        return !(candidate instanceof Mob mob) || mob.getTarget() != caster;
    }

    /** A ring of light snapped out along the ground, so the release is visible to everyone present. */
    private static void drawRing(ServerLevel level, Player caster) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0d * i / RING_POINTS;
            level.sendParticles(ParticleTypes.END_ROD,
                    caster.getX() + Math.cos(angle) * RING_RADIUS,
                    caster.getY() + 0.2d,
                    caster.getZ() + Math.sin(angle) * RING_RADIUS,
                    1, 0.0d, 0.02d, 0.0d, 0.0d);
        }
    }
}
