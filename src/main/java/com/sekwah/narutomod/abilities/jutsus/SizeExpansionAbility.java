package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Partial Multi-Size: the caster grows just enough to matter.
 *
 * <p>The full technique is a body the size of a house, which is not a thing a player can be given.
 * What a ninja can hold is the useful half of it: an arm that reaches two blocks further than it
 * should, and a weight that carries every step a little slow. That trade is the whole stance - a
 * reach nobody expects, paid for in pace and in chakra every tick, which is why it is something to
 * switch on for a moment rather than to leave running.</p>
 *
 * <p>Both halves ride on attributes rather than on effects because reach is not a potion in vanilla:
 * Forge's entity reach is the only figure that stretches an arm, and the movement speed penalty has
 * to counteract it on the same server-synced numbers. The marks are kept a little ahead of the tick
 * so that the support hooks can tell a giant from an ordinary body even on the frame the stance is
 * being switched off.</p>
 */
public class SizeExpansionAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** Growing is not a loud thing; the ground complaining under the weight is. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B C B B A B - the taijutsu branch's body seal with the shape key. */
    public static final long COMBO = 132212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "size_expansion");

    /** Read by anything that needs to know the body is stretched. */
    public static final String UNTIL_TAG = "narutomod:size_expansion_until";

    /** Stable ids, so the same two modifiers are added and taken off rather than stacking. */
    private static final UUID REACH_MODIFIER_ID = UUID.fromString("4f1c8b27-6a3d-4e95-b2f0-8c7a1d9e5b63");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("a9d2f5e1-3b74-4c08-9e6a-7d21b4c8f530");

    /** Two blocks of arm, and a third of the pace given up for it. */
    private static final double REACH_BONUS = 2.0d;
    private static final double SPEED_PENALTY = -0.35d;

    /** Chakra per tick while the shape is held. */
    private static final float COST_PER_TICK = 0.35f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How far ahead the mark is kept while the stance is held. It only has to outlive the tick it was
     * written on, but a small grace means a blow resolved a moment after the stance drops still
     * counts as coming from a stretched body.
     */
    private static final int MARK_GRACE = 5;

    /** Larger puffs at the feet: the ground is being pressed on by more weight than it expects. */
    private static final int PUFF_INTERVAL = 4;
    private static final int PUFF_COUNT = 10;

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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        apply(player);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.tickCount % PUFF_INTERVAL == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(),
                    player.getY() + 0.1d, player.getZ(),
                    PUFF_COUNT, 0.7d, 0.05d, 0.7d, 0.02d);
        }
    }

    /** Re-applied every tick so a lost modifier (a reload, a respawn) comes back with the stance. */
    private static void apply(Player player) {
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null && reach.getModifier(REACH_MODIFIER_ID) == null) {
            reach.addTransientModifier(new AttributeModifier(REACH_MODIFIER_ID,
                    "narutomod:size_expansion_reach", REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED_MODIFIER_ID) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
                    "narutomod:size_expansion_speed", SPEED_PENALTY,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** The stance is mastered by holding it, so the tick credit is paid from {@link #performServer}. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attributes are synced from the server, so there is nothing to grow on the client.
    }

    /** Dropping the stance takes the reach, the weight and the mark with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null) {
            reach.removeModifier(REACH_MODIFIER_ID);
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** Whether the shape is currently stretched, asked by anything that leans on the stance. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
