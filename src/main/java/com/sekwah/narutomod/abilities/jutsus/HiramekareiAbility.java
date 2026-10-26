package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Hiramekarei: the great hammer-blade held over the shoulder and brought down with the whole body.
 *
 * <p>The sword is not a sword in the usual sense. It is a slab of a blade wide enough to be a shield,
 * and what it is for is weight: everything the caster lands while it is up in both hands comes down
 * half again as hard, because the mass behind the blow is the technique. A ninja who can hold it up
 * long enough to matter can also swing it into the ground, and anything standing on stone or ore
 * loses the ground it was standing on.</p>
 *
 * <p>The hammer is paid for once, when it is raised, and held in the hands rather than worn: the
 * caster has to keep the channel up, and the mark it writes is what the melee hook reads. It is
 * deliberately heavy on chakra and light on finesse.</p>
 */
public class HiramekareiAbility extends Ability implements Ability.Channeled, Ability.Cooldown {

    /** A slab of a blade coming down is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 14;
    }

    /** 1 3 2 3 2 3 - the kenjutsu seals and the two keys of the hammer. */
    public static final long COMBO = 132323L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "hiramekarei");

    /** The mark the melee hook reads: kept a few ticks ahead for as long as the hammer is up. */
    public static final String UNTIL_TAG = "narutomod:hiramekarei_until";

    /** What the hammer does to a blow: the weight behind it. */
    public static final float DAMAGE_FACTOR = 1.5f;

    /** What raising it costs. */
    private static final float OPEN_COST = 50.0f;

    /**
     * What holding it costs. The hammer is paid for once, in the raising; the hold itself is the
     * arm doing the work, and a caster who is out of chakra still has to be able to swing.
     */
    private static final float UPKEEP = 0.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the arm needs before the blade can be shouldered again. */
    private static final int COOLDOWN = 20;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the hammer drops still land heavy.
     */
    private static final int MARK_GRACE = 5;

    private static final int PARTICLE_INTERVAL = 4;
    private static final int PARTICLE_COUNT = 4;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
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
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.hiramekarei.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The first tick of the hold is the raising, and it is the one that carries the price.
        boolean opening = chargeAmount <= 0;
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST + UPKEEP : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (cost > 0.0f) {
            ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        }
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        // A hold is mastered by the time it is held, not by the act of raising it.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.level() instanceof ServerLevel level && ticksChanneled % PARTICLE_INTERVAL == 0) {
            Vec3 hand = JutsuEffects.handPosition(player);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z,
                    PARTICLE_COUNT, 0.2d, 0.25d, 0.2d, 0.01d);
        }
    }

    /** The channel ends: the hammer comes down and the mark goes with it. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        if (player.level() instanceof ServerLevel level) {
            Vec3 hand = JutsuEffects.handPosition(player);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y - 0.5d, hand.z,
                    8, 0.3d, 0.1d, 0.3d, 0.02d);
        }
        // Only a hold that actually ran is worth a cooldown; a tap that never raised the blade is not.
        if (ticksActive > 0) {
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }
    }

    /** @return whether the hammer is up right now, asked by the melee hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** The hold is mastered by the time it is held, so raising it records nothing on its own. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * The hammer brought down into the ground under a target: the block a body is standing on is
     * broken out from beneath it, which is the second use of a blade this heavy.
     */
    public static void crushUnderfoot(Player caster, LivingEntity victim) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = victim.getOnPos();
        BlockState state = level.getBlockState(pos);
        if (!breakable(state) || !level.mayInteract(caster, pos)) {
            return;
        }
        level.destroyBlock(pos, true, caster);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5d, pos.getY() + 0.5d,
                pos.getZ() + 0.5d, 8, 0.3d, 0.2d, 0.3d, 0.02d);
    }

    /** @return whether the block is stone or ore, the two things a hammer of this size breaks. */
    private static boolean breakable(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.getBlock() instanceof DropExperienceBlock;
    }
}
