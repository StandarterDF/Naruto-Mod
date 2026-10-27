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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;

/**
 * The animal path: the caster calls hunters out of the ground and lets them do the running.
 *
 * <p>Three wolves answer, tamed to the caster and bound to them for a minute. They are not a
 * permanent menagerie - they are a technique, so they are marked on the way in, kept close on a
 * leash the caster does not have to hold, and taken back when the technique's time runs out. They
 * leave nothing behind when they go, the same way a clone does not, because nothing about them was
 * ever real.</p>
 *
 * <p>Vanilla wolves are used rather than a new creature on purpose: the path is about command, not
 * about a new monster, and a tamed wolf already knows how to fight beside its owner.</p>
 */
public class AnimalPathAbility extends Ability implements Ability.Cooldown {

    /** A pack answering the call carries some distance. */
    @Override
    public int noiseRadius() {
        return 14;
    }

    /** 2 3 3 2 3 2 - the Rinnegan stance plus two keys, one short of the Asura path. */
    public static final long COMBO = 11211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "animal_path");

    /** What the pack costs to call, and how long the caster waits before calling another. */
    private static final float COST = 70.0f;
    private static final int COOLDOWN = 600;

    /** How many wolves answer, and for how long they stay. */
    public static final int SUMMON_COUNT = 3;
    public static final int LIFETIME = 1200;

    /**
     * How far a wolf may stray before the technique drags it back to its owner. Wide enough that a
     * wolf can chase, narrow enough that it never has to path back across a ravine.
     */
    public static final double LEASH = 24.0d;

    /** The mark that says a wolf belongs to this technique rather than to a player. */
    public static final String SUMMON_TAG = "narutomod:summon_wolf";

    /** The game time at which the summoned wolf is taken back. */
    public static final String UNTIL_TAG = "narutomod:summon_until";

    /** Where the wolves are placed around the caster. */
    private static final double SPAWN_RADIUS = 2.0d;

    /** Ticks before the caster's own regeneration resumes after paying for the pack. */
    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
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
        long until = level.getGameTime() + LIFETIME;
        for (int i = 0; i < SUMMON_COUNT; i++) {
            double angle = (Math.PI * 2.0d / SUMMON_COUNT) * i + player.getYRot();
            double x = player.getX() + Math.cos(angle) * SPAWN_RADIUS;
            double z = player.getZ() + Math.sin(angle) * SPAWN_RADIUS;
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) {
                continue;
            }
            wolf.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            // The wolf has to know whose side it is on before it is tamed, or the taming looks for
            // an owner and finds nobody.
            wolf.setOwnerUUID(player.getUUID());
            wolf.setTame(true);
            wolf.setPersistenceRequired();
            // Nothing of a summoned wolf is real loot, so no slot may ever drop.
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                wolf.setDropChance(slot, 0.0f);
            }
            wolf.getPersistentData().putBoolean(SUMMON_TAG, true);
            wolf.getPersistentData().putLong(UNTIL_TAG, until);
            level.addFreshEntity(wolf);

            level.sendParticles(ParticleTypes.CLOUD, x, player.getY() + 0.4d, z,
                    12, 0.3d, 0.3d, 0.3d, 0.02d);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 1.0f, 0.8f);
    }
}
