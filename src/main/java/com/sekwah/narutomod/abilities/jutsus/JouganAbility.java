package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * The Jougan: the pure eye, opened as a stance.
 *
 * <p>Where the Sharingan reads a fight and the Byakugan reads a room, the Jougan reads the world
 * itself. Chakra is visible to it the way heat is visible to an eye built for it: every living body
 * near the caster is marked through whatever stands between them, mundane darkness does not blind
 * it, and a creature that has decided to attack is already legible enough for the caster to strike
 * its weak point. The eye also carries a share of the caster's own reserves while it stays open - it
 * is a strain to hold, and a body that can carry it holds more for the trouble.</p>
 *
 * <p>The eye is also the only one that sees the seams in space, which is what
 * {@link DimensionStepAbility} and {@link ChakraSeverAbility} are built on: a step that does not
 * cross the ground between two points, and a look that cuts the chakra running through a body.</p>
 */
public class JouganAbility extends AbstractDojutsuAbility {

    /** Opening the eye is quiet, though holding it is not. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V V C - free, and one key away from both of its techniques. */
    public static final long COMBO = 322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "jougan");

    /** Chakra burnt for every tick the eye stays open. The most expensive of the perception eyes. */
    private static final float CHAKRA_PER_TICK = 0.45f;

    /** Extra chakra and nature energy the open eye adds to the caster's pools. */
    private static final float CHAKRA_BONUS = 40.0f;
    private static final float SENIN_BONUS = 25.0f;

    /** How far the eye picks out chakra. */
    private static final double SIGHT_RANGE = 24.0d;

    /** How long each mark lasts, refreshed while the eye is open. */
    private static final int MARK_TICKS = 60;

    /**
     * The eye reads intent as well as chakra: a creature that has decided to attack the caster is
     * wrapped in slow chakra for a moment, which is what makes the eye worth its upkeep in a fight
     * rather than only a lantern for hostile outlines.
     */
    private static final int FORESIGHT_TICKS = 30;

    /** Bonus damage a melee hit lands on a marked hostile while the eye is open, see JouganEvents. */
    public static final float WEAK_POINT_DAMAGE = 2.0f;

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    protected ResourceLocation eyeId() {
        return ID;
    }

    @Override
    protected boolean enabled() {
        return NarutoConfig.jouganEnabled;
    }

    @Override
    protected String disabledKey() {
        return "jutsu.fail.jougan.disabled";
    }

    @Override
    protected float chakraPerTick() {
        return CHAKRA_PER_TICK;
    }

    @Override
    protected void applyEyeBonus(INinjaData ninjaData) {
        ninjaData.setEyeChakraBonus(CHAKRA_BONUS);
        ninjaData.setEyeSeninBonus(SENIN_BONUS);
    }

    @Override
    protected void aura(ServerLevel level, Player player, INinjaData ninjaData) {
        if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(),
                    player.getY() + player.getBbHeight() * 0.9d, player.getZ(),
                    3, 0.2d, 0.1d, 0.2d, 0.01d);
        }

        // Chakra is lit up for as long as it stays in sight, which is the passive the eye is bought
        // for. Every living body counts, not just the hostile ones: the eye sees chakra, and a
        // player standing behind a wall is chakra too. Refreshing the effect rather than applying it
        // once means it fades on its own the moment the eye closes or the creature leaves the bubble.
        if (player.tickCount % 20 == 0) {
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(SIGHT_RANGE))) {
                if (living == player || !living.isAlive()) {
                    continue;
                }
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, false, false));
            }
            // The eye reads chakra, not light, so mundane darkness does not blind it.
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.BLINDNESS);
        }

        // Foresight: a creature that has taken the caster for a target is already moving in the eye's
        // picture, so its body answer lagging behind is what the stance reads. Slowing it is not a
        // free stun - a short, refreshed mark that only helps the caster break away or strike first.
        if (player.tickCount % 10 == 0) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(SIGHT_RANGE))) {
                if (!mob.isAlive() || mob.getTarget() != player) {
                    continue;
                }
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                        FORESIGHT_TICKS, 0, false, false));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        mob.getX(), mob.getY() + mob.getBbHeight() * 0.6d, mob.getZ(),
                        2, 0.2d, 0.2d, 0.2d, 0.0d);
            }
        }
    }

    /** @return whether the caster has the Jougan open right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return isEyeActive(ninjaData, ID);
    }
}
