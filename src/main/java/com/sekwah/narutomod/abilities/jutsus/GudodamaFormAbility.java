package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * A Truth-Seeking Orb folded into a weapon and worn as a second skin.
 *
 * <p>The black matter of an orb is shaped around the caster's arms: every blow lands with the weight
 * of it, and a share of whatever comes back is swallowed before it reaches the body. It is the
 * aggressive half of the three orb techniques - the throw is range, the guard is defence, this is
 * the brawl - and it is paid for in orbs every five seconds it is held, so a caster who keeps it on
 * through a long fight will run the mode dry.</p>
 *
 * <p>The damage bonus is a transient attribute modifier with a stable id, so re-applying it every
 * tick while the stance is up can never stack it; the twenty-percent damage reduction is applied by
 * {@code events/SixPathsEvents}, which sees the hits.</p>
 */
public class GudodamaFormAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** Holding shaped nothing is quiet work. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C C C C B - the shaping seal. */
    public static final long COMBO = 2113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gudodama_form");

    /** Chakra paid per tick to keep the shape. */
    private static final float COST_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** Extra melee damage the shape adds while it is held. */
    private static final double DAMAGE_BONUS = 6.0d;

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("4a7c2e91-6b3d-4f58-8e21-9c5d0a7f3b64");

    /** One orb is consumed for every five seconds the shape is worn. */
    private static final int ORB_INTERVAL = 100;

    /** Tick the next orb is due. Written on the caster so the cost follows the stance, not the tick count. */
    public static final String NEXT_ORB_TAG = "narutomod:gudodama_form_next";

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SixPathsModeAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.six_paths_mode.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (SixPathsModeAbility.orbs(player) <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gudodama.no_orbs",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
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
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        applyShape(player);
        payOrb(player);
        if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SQUID_INK, player.getX(),
                    player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                    3, 0.35d, 0.5d, 0.35d, 0.01d);
        }
    }

    /** Re-applied every tick, so a modifier lost to a reload or a respawn comes back with the stance. */
    private static void applyShape(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.getModifier(DAMAGE_MODIFIER_ID) == null) {
            attack.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID,
                    "narutomod:gudodama_form", DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * Takes an orb every hundred ticks. The due tick lives on the caster, so the first orb is paid
     * after five seconds of holding the shape and the next is never paid early.
     */
    private static void payOrb(Player player) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        if (!data.contains(NEXT_ORB_TAG)) {
            data.putLong(NEXT_ORB_TAG, now + ORB_INTERVAL);
            return;
        }
        if (now < data.getLong(NEXT_ORB_TAG)) {
            return;
        }
        SixPathsModeAbility.spendOrb(player);
        data.putLong(NEXT_ORB_TAG, now + ORB_INTERVAL);
    }

    /** The shape is mastered by the time it is worn, so the tick credit is paid from the upkeep. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attribute is synced from the server; there is nothing to shape on the client.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(DAMAGE_MODIFIER_ID);
        }
        player.getPersistentData().remove(NEXT_ORB_TAG);
    }

    /** @return whether the shape is currently worn, asked by the damage hook in {@code SixPathsEvents}. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
