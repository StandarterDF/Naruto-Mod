package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Water Clone: a copy made of water, held together just long enough to be worth confusing someone
 * with.
 *
 * <p>Where a shadow clone is a second fighter, this one is a body that will not hold a blade and
 * cannot take a beating: it is water wearing the caster's face. What it does better than anything
 * else is be in the way - it stays close to the caster instead of wandering, it gives up only a part
 * of every blow that reaches it, and it puts out fire by standing in it. Everything lethal about it
 * is downstream of how cheap it is to make another one.</p>
 *
 * <p>The body itself is a plain {@link ShadowCloneEntity} with no equipment copied into it (the bare
 * constructor is used for exactly that reason) and the marks below written into its persistent data.
 * The marks are the contract: the world hook that expires the copy, keeps it on its leash, dampens
 * the blows it takes and smothers flame reads them, and this class only puts the water down.</p>
 */
public class WaterCloneAbility extends Ability implements Ability.Cooldown {

    /** Water falling into being is louder than a shadow clone's puff, and wetter. */
    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    /** B A B A A B - the clone seal followed by the water sign. */
    public static final long COMBO = 212112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "water_clone");

    /** Marks a clone as a water clone. Read by the hook that runs the body, so the name is a contract. */
    public static final String WATER_KEY = "narutomod:water_clone";

    /** Game time at which the water gives out and the copy collapses. */
    public static final String WATER_UNTIL_KEY = "narutomod:water_clone_until";

    /**
     * The caster the copy belongs to, written out beside the entity's own owner field so the hook can
     * follow the leash without resolving a player from the server list.
     */
    public static final String WATER_OWNER_KEY = "narutomod:water_clone_owner";

    /**
     * How far the copy may stray from its caster before the leash snaps, in blocks. Exported because
     * the leash is enforced by the world hook, not here.
     */
    public static final double LEASH_RANGE = 24.0d;

    /**
     * What is left of a blow that lands on a water clone. Water has no bones to break, but it does
     * scatter, so a hit still costs the body something.
     */
    public static final float DAMAGE_MULTIPLIER = 0.4f;

    private static final float COST = 35.0f;
    private static final int COOLDOWN = 160;

    /** How long the body holds together, in ticks; the same number the hook reads off the mark. */
    private static final int DURATION = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Just inside arm's reach, so the copy reads as having stepped out of the caster. */
    private static final double SPAWN_RADIUS = 1.2d;

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

        ShadowCloneEntity water = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        water.setOwner(player);
        // Nothing is copied into its hands or armour: the bare constructor leaves every slot empty,
        // and water has no business swinging the caster's sword.
        CompoundTag data = water.getPersistentData();
        data.putBoolean(WATER_KEY, true);
        data.putLong(WATER_UNTIL_KEY, level.getGameTime() + DURATION);
        data.putUUID(WATER_OWNER_KEY, player.getUUID());

        position(water, level, player);
        if (!level.addFreshEntity(water)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.water_clone.failed",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        level.sendParticles(ParticleTypes.SPLASH, water.getX(),
                water.getY() + water.getBbHeight() * 0.5d, water.getZ(),
                40, 0.5d, 0.6d, 0.5d, 0.1d);
        level.playSound(null, player, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /** Stands the copy beside the caster, closing in if that spot is taken. */
    private static void position(ShadowCloneEntity water, ServerLevel level, Player player) {
        double angle = Math.toRadians(player.getYRot());
        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() - Math.sin(angle) * radius;
            water.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            if (level.noCollision(water, water.getBoundingBox())) {
                break;
            }
        }
    }
}
