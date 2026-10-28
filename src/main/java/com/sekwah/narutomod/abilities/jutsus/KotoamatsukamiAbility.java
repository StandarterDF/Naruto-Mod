package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

/**
 * Kotoamatsukami: the gentlest of the eye's illusions, and the one that lasts.
 *
 * <p>It does not stun, blind or hurt. It simply tells a creature that the person in front of it is
 * on its own side, and a mind told that once does not go looking for a reason to disagree: the
 * creature drops whatever it was fighting and - for six seconds - refuses to take the caster for an
 * enemy again. The illusion is broken the moment the caster strikes it, which is the whole shape of
 * the technique: it buys a way past a guard rather than a way through one, and the first blow has
 * to be worth the six seconds it ends.</p>
 *
 * <p>An exceptionally strong mind cannot be told something it does not already believe, so creatures
 * with a hundred health or more are refused outright. The mark itself is a timestamp and the
 * caster's UUID in the creature's persistent data: the belief has to survive the creature walking
 * out of the loaded chunks and back, and no reference to a player object should be held for that.
 * {@code SharinganIllusionEvents} is what reads it on every target change.</p>
 */
public class KotoamatsukamiAbility extends Ability implements Ability.Cooldown {

    /** The illusion is planted quietly, face to face, and travels no further than the look. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C V V B B B - the Sharingan combo (C V V B), then the deepest suggestion on the branch. */
    public static final long COMBO = 111321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kotoamatsukami");

    /** Game time until which the creature believes the named caster is an ally. */
    public static final String UNTIL_TAG = "narutomod:koto_until";

    /** UUID of the caster the creature was made to trust. */
    public static final String SOURCE_TAG = "narutomod:koto_source";

    /** Above this much health a mind is too set in its ways to be talked around. */
    private static final float BOSS_HEALTH = 60.0f;

    /** How far down the look ray the suggestion can be planted. Walls stop it, as they stop every eye. */
    private static final double RANGE = 10.0d;

    /** What it costs and how long before the eye can plant another belief. */
    private static final float COST = 120.0f;
    private static final int COOLDOWN = 400;

    /** Six seconds of misplaced trust: enough to walk past a guard, not enough to fight beside one. */
    private static final int DURATION = 120;

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
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof Mob target) || !isValidTarget(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kotoamatsukami.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Checked before the chakra is taken: a player looking at a boss should lose nothing for
        // the attempt, and finding out afterwards that it was never possible is the worse surprise.
        if (isTooStrong(target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kotoamatsukami.boss",
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
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof Mob target)) {
            return;
        }
        // The look was paid for a moment ago; the body can have died or been swapped since.
        if (!isValidTarget(player, target) || isTooStrong(target)) {
            return;
        }

        // The mark is written before the target is dropped: a goal that puts the caster back into
        // the creature's sights later in the same tick then meets the mark and is refused, instead
        // of slipping through the gap between the two lines.
        target.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        target.getPersistentData().putString(SOURCE_TAG, player.getStringUUID());
        // The creature does not forget what it was doing, it is persuaded that the caster is not
        // worth doing it to. Clearing the target is what makes that visible at once; the mark is
        // what keeps it true when the next thing tries to become a target.
        target.setTarget(null);

        // Enchantment glyphs are the vanilla language for a mind being worked on, and the chime is
        // the one sound in the mod's register that reads as a thought arriving rather than a blow.
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                target.getY() + target.getBbHeight() * 0.8d, target.getZ(),
                30, 0.35d, 0.3d, 0.35d, 0.4d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** A belief needs a mind that chooses targets, and is never planted in an ally or a friend. */
    private boolean isValidTarget(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /**
     * A boss is not a mind that can be talked around: its will is the point of it. The health line
     * catches the strong ordinary creatures, and the two named bosses are refused outright even if
     * a datapack gives them a smaller pool.
     */
    private static boolean isTooStrong(LivingEntity target) {
        return target.getHealth() >= 100.0f
                || target instanceof EnderDragon
                || target instanceof WitherBoss;
    }
}
