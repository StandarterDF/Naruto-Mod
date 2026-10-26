package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Illusory double: a clone with no body to hit, thrown in front of the caster's own.
 *
 * <p>The copy wheel already reads a fight; this is the first thing it writes into one. The caster
 * steps out of their own silhouette and leaves it standing, and what is left behind is not a shadow
 * clone - there is no chakra in it to cut, no gear to swing, and nothing to see with an eye that is
 * not a Sharingan. It is a smear of chakra shaped like a person, and the only thing it does is take
 * the attention of everything that was about to look at the caster.</p>
 *
 * <p>That is why it is invisible: an illusion that was drawn would be an ordinary clone. The copy
 * exists for the mobs' eyes only, and the caster's own eye sees it for what it is - a grey label and
 * nothing else, see {@code SharinganCloneOverlay}. It never hurts anything, and the first body that
 * reaches it finds nothing there: the mark left here is what the world hooks read, both for its
 * lifetime and for the blows it is not allowed to land.</p>
 */
public class IllusoryDoubleAbility extends Ability implements Ability.Cooldown {

    /** C V V B C C C - the Sharingan (C V V B) and the cheap end of the eye's tricks. */
    public static final long COMBO = 1223111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "illusory_double");

    /**
     * Marks a clone as an illusion rather than a body. Read by {@code SharinganAdvancedEvents} for
     * its lifetime and its harmlessness, and by the client overlay that only a Sharingan draws.
     */
    public static final String MARK_TAG = "narutomod:illusory_double";

    /** Game time at which the illusion comes apart. */
    public static final String UNTIL_TAG = "narutomod:illusory_double_until";

    /** What the trick costs, and how long the eye needs before it can be repeated. */
    private static final float COST = 40.0f;
    private static final int COOLDOWN = 240;

    /** Six seconds of standing where the caster is not: long enough to break a chase. */
    private static final int DURATION = 120;

    /** How far in front of the caster the double steps out. */
    private static final double SPAWN_OFFSET = 1.6d;

    /** How far in front and behind the caster the copy can be put down if the first spot is taken. */
    private static final double SPAWN_BACKOFF = 0.8d;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** An illusion is a misdirection, not a battle cry. */
    private static final int NOISE_RADIUS = 10;

    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
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

        ShadowCloneEntity phantom = this.spawnDouble(level, player);
        long until = level.getGameTime() + DURATION;
        phantom.getPersistentData().putBoolean(MARK_TAG, true);
        phantom.getPersistentData().putLong(UNTIL_TAG, until);
        level.addFreshEntity(phantom);

        level.sendParticles(ParticleTypes.CLOUD, phantom.getX(),
                phantom.getY() + phantom.getBbHeight() / 2.0d, phantom.getZ(),
                28, 0.45d, 0.55d, 0.45d, 0.02d);
        level.playSound(null, player, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /**
     * Builds the copy.
     *
     * <p>The bare entity constructor is used on purpose: the fighting-clone constructor copies the
     * caster's gear, arrows, effects and paper bomb, and an illusion must carry none of them - an
     * illusion holding a bomb the caster did not spend would be a free second cast.</p>
     */
    private ShadowCloneEntity spawnDouble(ServerLevel level, Player player) {
        ShadowCloneEntity phantom = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        phantom.setOwner(player);
        phantom.setHealth(1.0f);
        // Nothing is drawn of the illusion: it exists to be looked at by something that cannot tell
        // the difference between a smear of chakra and a person, and to be labelled by an eye that can.
        phantom.setInvisible(true);
        place(phantom, level, player);
        return phantom;
    }

    /** Puts the copy in front of the caster, closing in to the caster's own feet if that spot is blocked. */
    private static void place(ShadowCloneEntity phantom, ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        double dirX = horizontal < 0.001d ? 0.0d : look.x / horizontal;
        double dirZ = horizontal < 0.001d ? 1.0d : look.z / horizontal;

        for (double offset : new double[]{SPAWN_OFFSET, SPAWN_BACKOFF, 0.0d}) {
            phantom.moveTo(player.getX() + dirX * offset, player.getY(), player.getZ() + dirZ * offset,
                    player.getYRot(), 0.0f);
            if (level.noCollision(phantom, phantom.getBoundingBox())) {
                return;
            }
        }
    }
}
