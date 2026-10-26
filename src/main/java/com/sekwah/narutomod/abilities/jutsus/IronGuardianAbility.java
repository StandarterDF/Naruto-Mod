package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An iron guardian called up out of the ground where the caster is looking.
 *
 * <p>A summon in the plain sense: a body that is not a clone and does not need chakra to keep
 * breathing, put between the caster and whatever is coming at them. It is not a soldier - it does
 * not follow, it does not take orders and it will not chase - it holds the spot it was called to and
 * hits what comes close that is hostile. That is the whole trade of the technique: a wall that can
 * swing back, for as long as the summoning lasts.</p>
 *
 * <p>The guardian is a vanilla golem carrying the summon marks in its persistent data. Living the
 * marks out, and refusing to pick a target that is not a monster, are consequences handled by
 * {@code SummonMedicineEvents} - this class only puts the body down.</p>
 */
public class IronGuardianAbility extends Ability implements Ability.Cooldown {

    /** V C V C B C - the clone seal walked up into the summoning keys. */
    public static final long COMBO = 212131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "iron_guardian");

    /** Marks the golem as a summon rather than a village golem. Shared with the summon hooks. */
    public static final String SUMMON_KEY = "narutomod:summon_golem";

    /** Game time at which the summon goes back to the ground. Shared with the summon hooks. */
    public static final String SUMMON_UNTIL_KEY = "narutomod:summon_until";

    /** Chakra for the summoning. */
    private static final float COST = 80.0f;

    /** Long enough that the guardian is a decision rather than a spammable wall. */
    private static final int COOLDOWN = 4800;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the guardian stands. */
    private static final int LIFETIME = 1200;

    /**
     * What the guardian is worth in health. Less than a village golem's hundred, so a summoned one
     * is a shield with a timer rather than a permanent upgrade to a village's defences.
     */
    private static final float HEALTH = 60.0f;

    /** How far the caster may aim the summoning. */
    private static final double RANGE = 16.0d;

    /** How far in front the guardian appears when the caster is looking at nothing solid. */
    private static final double SPAWN_DISTANCE = 3.0d;

    /** A summoning is heard. */
    private static final int NOISE_RADIUS = 24;

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

        IronGolem guardian = new IronGolem(EntityType.IRON_GOLEM, level);
        guardian.setPlayerCreated(true);
        this.position(guardian, level, player);
        guardian.setPersistenceRequired();
        guardian.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        guardian.setHealth(HEALTH);
        guardian.setTarget(null);

        guardian.getPersistentData().putBoolean(SUMMON_KEY, true);
        guardian.getPersistentData().putLong(SUMMON_UNTIL_KEY, level.getGameTime() + LIFETIME);
        level.addFreshEntity(guardian);

        level.sendParticles(ParticleTypes.CLOUD, guardian.getX(),
                guardian.getY() + guardian.getBbHeight() * 0.5d, guardian.getZ(),
                40, 0.7d, 0.9d, 0.7d, 0.02d);
        level.playSound(null, guardian.getX(), guardian.getY(), guardian.getZ(),
                SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /**
     * Puts the guardian where the caster aimed: the face of the block under the crosshair, or just in
     * front of the caster if they are aiming at open air. A body that would be standing in a wall is
     * pulled back towards the caster until it fits, ending on the caster's own spot as a last resort.
     */
    private void position(IronGolem guardian, ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        BlockHitResult aimed = Targeting.lookedAtBlock(player, RANGE);
        Vec3 first = aimed != null
                ? aimed.getLocation()
                : player.position().add(look.scale(SPAWN_DISTANCE));

        for (Vec3 spot : new Vec3[]{first, player.position().add(look.scale(SPAWN_DISTANCE)), player.position()}) {
            guardian.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0f);
            if (level.noCollision(guardian, guardian.getBoundingBox())) {
                return;
            }
        }
    }
}
