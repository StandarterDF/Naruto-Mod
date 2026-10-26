package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Mud Clone: two heavy copies shaped out of the ground, built to be broken rather than to win.
 *
 * <p>The ground gives a ninja a body that is cheaper than chakra and heavier than a shadow clone: a
 * mud copy takes four hits' worth of punishment where a fighter would take one, but it does not move
 * well and it does not last. Its whole purpose is the moment it comes apart - the mass it was made
 * of is still there when the chakra lets go, and everything standing in it slows to a crawl. Two are
 * raised at once, a step out from the caster and anchored to the spot, so the copies read as having
 * grown out of the earth and stay where they were grown.</p>
 *
 * <p>The copies are marked below and given no equipment (the bare constructor is used for exactly
 * that). The death slow is applied by the world hook: this class only writes the marks and the
 * anchor the copies are told to hold.</p>
 */
public class MudCloneAbility extends Ability implements Ability.Cooldown {

    /** Mud coming apart is a heavy, wet sound. */
    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

    /** B A B A A C - the clone seal followed by the earth sign. */
    public static final long COMBO = 212113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mud_clone");

    /** Marks a clone as a mud clone. Read by the hook that expires it and slows the ground it dies on. */
    public static final String MUD_KEY = "narutomod:mud_clone";

    /** Game time at which the mud dries out and the copy falls apart. */
    public static final String MUD_UNTIL_KEY = "narutomod:mud_until";

    /** The point the copies were told to hold, as three doubles so the hook can measure against it. */
    public static final String MUD_ANCHOR_X_KEY = "narutomod:mud_anchor_x";
    public static final String MUD_ANCHOR_Y_KEY = "narutomod:mud_anchor_y";
    public static final String MUD_ANCHOR_Z_KEY = "narutomod:mud_anchor_z";

    /**
     * What a breaking mud clone leaves behind, exported because the hook applies it on death.
     */
    public static final int DEATH_SLOW_TICKS = 60;
    public static final int DEATH_SLOW_AMPLIFIER = 2;

    private static final float COST = 50.0f;
    private static final int COOLDOWN = 300;

    /** How long a copy holds its shape, in ticks; the hook reads the same number off the mark. */
    private static final int DURATION = 300;

    /**
     * Health a mud copy is given. The entity's own maximum is one point, so the maximum has to be
     * raised before the health is set or vanilla simply clamps it back down to a single hit.
     */
    private static final float MUD_HEALTH = 4.0f;

    /** Two bodies: one is a nuisance, three is a wall the caster did not pay for. */
    private static final int COPIES = 2;

    /** How far out from the caster the pair is raised, in blocks. */
    private static final double SPAWN_RADIUS = 1.8d;

    private static final int CHAKRA_REGEN_DELAY = 20;

    private static final int NOISE_RADIUS = 16;

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

        long until = level.getGameTime() + DURATION;
        boolean spawned = false;
        for (int i = 0; i < COPIES; i++) {
            spawned |= level.addFreshEntity(this.spawnMud(level, player, until, i));
        }
        if (!spawned) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mud_clone.failed",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                player.getX(), player.getY() + player.getBbHeight() * 0.4d, player.getZ(),
                40, 0.6d, 0.5d, 0.6d, 0.05d);
        level.playSound(null, player, SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 0.9f, 0.8f);
    }

    /**
     * Builds one copy: no gear, four points of health, and a note of where it was told to hold.
     *
     * <p>The anchor is the caster's feet at the moment of the cast rather than the copy's own spot:
     * the pair is raised a step away from the caster, and "stay near the cast point" has to mean the
     * same point for both of them.</p>
     */
    private ShadowCloneEntity spawnMud(ServerLevel level, Player player, long until, int index) {
        ShadowCloneEntity mud = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        mud.setOwner(player);

        CompoundTag data = mud.getPersistentData();
        data.putBoolean(MUD_KEY, true);
        data.putLong(MUD_UNTIL_KEY, until);
        data.putDouble(MUD_ANCHOR_X_KEY, player.getX());
        data.putDouble(MUD_ANCHOR_Y_KEY, player.getY());
        data.putDouble(MUD_ANCHOR_Z_KEY, player.getZ());

        AttributeInstance maxHealth = mud.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(MUD_HEALTH);
        }
        mud.setHealth(MUD_HEALTH);

        position(mud, level, player, index);
        return mud;
    }

    /** Puts the pair on opposite sides of the caster, closing in if either spot is taken. */
    private static void position(ShadowCloneEntity mud, ServerLevel level, Player player, int index) {
        double angle = Math.PI * index;
        for (double radius : new double[]{SPAWN_RADIUS, SPAWN_RADIUS / 2.0d, 0.0d}) {
            double x = player.getX() + Math.cos(angle) * radius;
            double z = player.getZ() + Math.sin(angle) * radius;
            mud.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
            if (level.noCollision(mud, mud.getBoundingBox())) {
                break;
            }
        }
    }
}
