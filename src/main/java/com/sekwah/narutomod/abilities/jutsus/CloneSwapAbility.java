package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Clone Swap: the caster and one of their own copies trade places outright.
 *
 * <p>A clone is already a second body, and this is the moment that pays for it. The caster is caught,
 * cornered or simply on the wrong side of a fight, and instead of walking out of it they are suddenly
 * where the copy was standing while the copy takes the spot they just left. Nothing is hidden and
 * nothing is thrown: both bodies are standing in plain sight before and after, only the two places
 * have changed hands.</p>
 *
 * <p>Because the copy is the one left in the danger, both bodies arrive braced for the first half
 * second - the caster so the trick is not simply a way to eat a free hit, the clone so it survives
 * long enough for the enemy to realise it was cheated. The caster has to be able to feel the copy -
 * thirty-two blocks is the whole of it - and the copy has to be far enough away that there is a place
 * to trade into.</p>
 */
public class CloneSwapAbility extends Ability implements Ability.Cooldown {

    /** Louder than a step, quieter than a summon: two bodies pass each other in the air. */
    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    /** B A B A A A - the clone seal, then the two keys that turn it on its head. */
    public static final long COMBO = 1231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "clone_swap");

    /** A cheap trick compared with summoning a fresh copy: the body is already paid for. */
    private static final float COST = 20.0f;

    private static final int COOLDOWN = 160;

    /** How far away a copy can be and still be the other end of the swap, in blocks. */
    private static final double RANGE = 32.0d;

    /**
     * Closer than this and there is no swap to make - the caster is already standing where the copy
     * is, so the cast would only spend chakra and change nothing.
     */
    private static final double MIN_DISTANCE = 2.0d;

    /** Both bodies land braced, so trading places is not the same as offering a free hit. */
    private static final int GUARD_TICKS = 10;
    private static final int GUARD_AMPLIFIER = 4;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** A body appearing out of nowhere is heard even if it never crossed the ground. */
    private static final int NOISE_RADIUS = 12;

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
        ShadowCloneEntity clone = nearestClone(player);
        if (clone == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.clone_swap.noclone",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (player.distanceToSqr(clone) < MIN_DISTANCE * MIN_DISTANCE) {
            player.displayClientMessage(Component.translatable("jutsu.fail.clone_swap.tooclose",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The copy has had a moment to move since the cost was paid, so the other end of the swap is
        // looked up again here rather than remembered from the check.
        ShadowCloneEntity clone = nearestClone(player);
        if (clone == null || player.distanceToSqr(clone) < MIN_DISTANCE * MIN_DISTANCE) {
            return;
        }

        // Both ends are read before either body moves, or the second read would find the first body
        // already standing on it and the two would end up in the same place.
        Vec3 casterFrom = player.position();
        Vec3 cloneFrom = clone.position();

        // A copy in the middle of a chase is not left walking a path towards a place it no longer is.
        clone.getNavigation().stop();
        clone.setTarget(null);

        brace(player);
        brace(clone);

        level.sendParticles(ParticleTypes.PORTAL, casterFrom.x,
                casterFrom.y + player.getBbHeight() * 0.5d, casterFrom.z,
                24, 0.3d, 0.5d, 0.3d, 0.3d);
        level.sendParticles(ParticleTypes.PORTAL, cloneFrom.x,
                cloneFrom.y + clone.getBbHeight() * 0.5d, cloneFrom.z,
                24, 0.3d, 0.5d, 0.3d, 0.3d);

        // teleportTo keeps each body's own facing, which is the point of a swap: the caster arrives
        // looking the way they were already looking, not turned to match the copy.
        player.teleportTo(cloneFrom.x, cloneFrom.y, cloneFrom.z);
        clone.teleportTo(casterFrom.x, casterFrom.y, casterFrom.z);

        // Neither body crossed the ground, so neither of them fell to get there.
        player.fallDistance = 0.0f;
        clone.fallDistance = 0.0f;

        level.playSound(null, casterFrom.x, casterFrom.y, casterFrom.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.0f);
        level.playSound(null, cloneFrom.x, cloneFrom.y, cloneFrom.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f, 1.2f);
    }

    /** Both ends of the swap come out of it with their guard up. */
    private static void brace(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, GUARD_TICKS, GUARD_AMPLIFIER));
    }

    /**
     * The copy this cast would trade with: the nearest of the caster's own, inside {@link #RANGE}.
     *
     * <p>Meditating clones are left out on purpose. They are sitting still by design, and dragging
     * one across the room to use it as an escape hatch would undo the meditation it exists for.</p>
     */
    private static ShadowCloneEntity nearestClone(Player player) {
        List<ShadowCloneEntity> clones = player.level().getEntitiesOfClass(ShadowCloneEntity.class,
                player.getBoundingBox().inflate(RANGE),
                clone -> clone.isOwnedBy(player) && !clone.isMeditating());
        ShadowCloneEntity nearest = null;
        double closest = Double.MAX_VALUE;
        for (ShadowCloneEntity clone : clones) {
            double distance = player.distanceToSqr(clone);
            if (distance < closest) {
                closest = distance;
                nearest = clone;
            }
        }
        return nearest;
    }
}
