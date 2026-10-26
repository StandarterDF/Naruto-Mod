package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
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
 * The Camellia Dance: what a Kaguya does to a body that has come too close.
 *
 * <p>Their blood lets them grow bone out of their own body, and the dance is the simplest use of it -
 * a blade where there was an arm, and a cut that keeps working after the blade is gone. For ten
 * seconds every blow the caster lands carries a little more than the swing, and a quarter of it cuts
 * straight through armour, because no plate stops a shard of bone that grew inside the wound.</p>
 *
 * <p>The technique is a mark rather than a state: nothing about it runs, and the blows are
 * ordinary blows. It is the hit itself that reads {@link #UNTIL_TAG} and does the extra cutting, so
 * the ability stays a small class with no listeners, see {@code ClanTechniqueEvents}.</p>
 */
public class CamelliaDanceAbility extends Ability implements Ability.Cooldown {

    /** A blade of bone growing out of a body is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B C B B A C - the Kaguya seal, then the two keys of the dance. */
    public static final long COMBO = 132213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "camellia_dance");

    /** The mark the hit hook reads: the tick the bone is drawn back in. */
    public static final String UNTIL_TAG = "narutomod:camellia_until";

    /** The bleed the dance leaves: a mark for whatever reads it, and a stand-in effect for the harm. */
    public static final String BLEED_TAG = "narutomod:bleed_until";

    /** What a landed blow carries on top of the swing, and how much of the whole cuts past armour. */
    public static final float BONUS_DAMAGE = 2.0f;
    public static final float ARMOUR_BYPASS = 0.25f;

    /** How long a cut keeps bleeding, in ticks. */
    public static final int BLEED_TICKS = 60;

    /** Ten seconds with the blade out. */
    private static final int DURATION = 200;

    private static final float COST = 20.0f;
    private static final int COOLDOWN = 300;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How much bone dust is scattered when the blade comes out. */
    private static final int DRAW_PARTICLES = 18;

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
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
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
        player.getPersistentData().putLong(UNTIL_TAG, level.getGameTime() + DURATION);

        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.CRIT, hand.x, hand.y, hand.z,
                DRAW_PARTICLES, 0.25d, 0.25d, 0.25d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }
}
