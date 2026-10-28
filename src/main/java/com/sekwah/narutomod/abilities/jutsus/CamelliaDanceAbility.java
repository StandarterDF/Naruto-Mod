package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.item.weapons.BoneBladeItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    public static final long COMBO = 211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "camellia_dance");

    /** The mark the hit hook reads: the tick the bone is drawn back in. */
    public static final String UNTIL_TAG = "narutomod:camellia_until";

    /** The bleed the dance leaves: a mark for whatever reads it, and a stand-in effect for the harm. */
    public static final String BLEED_TAG = "narutomod:bleed_until";

    /**
     * What a landed blow carries on top of the swing, and how much of the whole cuts past armour.
     * The bonus grows with mastery of the dance itself: a fresh cut is a shard, a mastered one is a
     * wound that goes through the body.
     */
    public static final float BONUS_DAMAGE = 2.0f;
    public static final float MASTERED_BONUS = 2.0f;
    public static final float ARMOUR_BYPASS = 0.25f;

    /** @return the extra cut of this caster, from the plain bonus to double it at full mastery. */
    public static float bonusDamage(INinjaData ninjaData) {
        return BONUS_DAMAGE + MASTERED_BONUS * Mastery.level(ninjaData, ID);
    }

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
        growBlade(player);

        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.CRIT, hand.x, hand.y, hand.z,
                DRAW_PARTICLES, 0.25d, 0.25d, 0.25d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /**
     * Puts the bone blade in the caster's hand. Anything already held is stowed in the inventory (or
     * dropped if there is no room), because the dance is a blade grown from the body, not a stance
     * that can be held alongside another weapon. A blade already in hand has its few strikes renewed
     * rather than a second one being stacked on top.
     */
    private static void growBlade(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof BoneBladeItem) {
            held.getOrCreateTag().putInt(BoneBladeItem.CHARGES_TAG, BoneBladeItem.MAX_CHARGES);
            return;
        }
        if (!held.isEmpty()) {
            ItemStack stowed = held.copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (!player.getInventory().add(stowed)) {
                player.drop(stowed, false);
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, BoneBladeItem.create());
    }
}
