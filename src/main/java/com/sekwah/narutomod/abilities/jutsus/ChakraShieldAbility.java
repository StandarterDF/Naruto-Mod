package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Burns every drop of chakra the caster has and shapes it into a shield, which shows up as armour
 * points and is eaten away hit by hit.
 *
 * <p>The shield is a second health bar in all but name: damage is taken out of the pool before it
 * reaches the caster at all, and the armour attribute is there so the caster can see how much is
 * left without any new interface. It cannot be stacked, one shield has to be used up before another
 * can be shaped, which is what stops it from being a free top-up between fights.</p>
 */
public class ChakraShieldAbility extends Ability {

    /** B V B */
    public static final long COMBO = 323L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_shield");

    /** Below this there is not enough chakra to be worth shaping at all. */
    public static final float MIN_CHAKRA = 20.0f;

    /** Armour points bought by one point of chakra. */
    public static final float ARMOUR_PER_CHAKRA = 1.0f;

    /**
     * Fixed id for the armour modifier, so the shield can be replaced or taken off again reliably
     * instead of piling up modifiers.
     */
    private static final UUID SHIELD_MODIFIER_ID = UUID.fromString("9d1d5f11-2e3b-4d0a-9c5e-6f4c7b8a2d31");

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (ninjaData.getShieldPoints() > 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.fail.shield",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getChakra() < MIN_CHAKRA) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        // Nothing is taken here: the whole bar is converted in performServer, and the minimum above
        // is only there so a nearly empty caster does not shape a useless sliver of a shield.
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        float points = ninjaData.getChakra() * ARMOUR_PER_CHAKRA;
        ninjaData.setChakra(0.0f);
        ninjaData.setShieldPoints(points);
        applyShield(player, points);

        level.sendParticles(ParticleTypes.ENCHANT, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                40, 0.5d, 0.7d, 0.5d, 0.05d);
        level.playSound(null, player, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.7f, 0.6f);
    }

    /**
     * Puts the current pool on the caster's armour, replacing whatever was there before.
     */
    public static void applyShield(Player player, float points) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour == null) {
            return;
        }
        armour.removeModifier(SHIELD_MODIFIER_ID);
        armour.addTransientModifier(new AttributeModifier(SHIELD_MODIFIER_ID, "narutomod:chakra_shield",
                points, AttributeModifier.Operation.ADDITION));
    }

    /**
     * Takes the shield off the armour display, used both when it is spent and when it is shattered.
     */
    public static void removeShield(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(SHIELD_MODIFIER_ID);
        }
    }
}
