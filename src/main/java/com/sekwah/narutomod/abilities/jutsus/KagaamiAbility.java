package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Magen: Kagaami - the illusion that the caster is standing somewhere they are not.
 *
 * <p>Two mirror images step out of the caster and stay at their sides for as long as the stance is
 * held. They carry nothing, cast nothing and cannot be cut down: they are a smear of chakra shaped
 * like a person, and the only thing they do is take the attention of everything that was coming for
 * the caster. An archer that was drawing on the ninja finds itself aiming at one of the copies, and
 * every arrow spent on a picture is an arrow not spent on the body.</p>
 *
 * <p>The copies are bare {@link ShadowCloneEntity} bodies on purpose - the fighting-clone constructor
 * copies the caster's gear and their paper bomb, and an illusion holding a charge the caster never
 * spent would be a free second attack. The world hooks that keep mobs pointed at them and that take
 * the copies off the field live in {@code GenjutsuAdvancedEvents}; this class spawns them, holds them
 * at the caster's sides and stamps the mark that the hooks read.</p>
 */
public class KagaamiAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** An illusion is laid in the mind, not shouted into the air. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C C B C - the genjutsu combination (C B C), then the branch's seventh step. */
    public static final long COMBO = 13123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kagaami");

    /** Marks a clone body as one of the mirror images rather than a real copy. */
    public static final String PHANTOM_TAG = "narutomod:kagaami";

    /** Game time at which a mirror image comes apart. Refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:kagaami_until";

    /** UUID string of the ninja whose face the image is wearing. Read by the target hook. */
    public static final String CASTER_TAG = "narutomod:kagaami_caster";

    /** Which side of the caster the image belongs on: 0 is the right hand, anything else the left. */
    public static final String SIDE_TAG = "narutomod:kagaami_side";

    /** Two images: one would only be a clone, three would be a crowd. */
    private static final int PHANTOMS = 2;

    /** How far to the caster's side a mirror image stands. */
    private static final double SIDE_OFFSET = 2.5d;

    /** Chakra burnt for every tick the stance is up. */
    private static final float COST_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How long an image outlives the last tick that refreshed it. Long enough that a lost tick does
     * not make one flicker out mid-fight, short enough that a stale copy never lingers.
     */
    private static final int PHANTOM_TICKS = 100;

    /** The most illusion one body can carry: twenty-five seconds. */
    private static final int MAX_TICKS = 500;

    /**
     * The images of every caster holding the stance. The ability is a registry singleton, so this is
     * the one place a hold lives; it is emptied when the stance ends.
     */
    private final Map<UUID, Hold> holds = new HashMap<>();

    /** One caster's stance: when it began and which bodies are wearing their face. */
    private static final class Hold {
        private long startedAt = -1L;
        private final List<UUID> phantoms = new ArrayList<>(PHANTOMS);
    }

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
        // The stance has run its length: ending the upkeep stops before another tick is paid for
        // images that are about to be taken off the field.
        Hold hold = this.holds.get(player.getUUID());
        if (hold != null && hold.startedAt >= 0L
                && player.level().getGameTime() - hold.startedAt >= MAX_TICKS) {
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Hold hold = this.holds.computeIfAbsent(player.getUUID(), uuid -> new Hold());
        if (hold.startedAt < 0L) {
            hold.startedAt = level.getGameTime();
        }

        // An image removed by something outside the stance is replaced, so the caster is never left
        // with one shoulder bare while the illusion is still being paid for.
        hold.phantoms.removeIf(id -> resolve(level, id) == null);
        while (hold.phantoms.size() < PHANTOMS) {
            hold.phantoms.add(spawn(level, player, hold.phantoms.size()).getUUID());
        }

        long until = level.getGameTime() + PHANTOM_TICKS;
        for (int side = 0; side < hold.phantoms.size(); side++) {
            ShadowCloneEntity phantom = resolve(level, hold.phantoms.get(side));
            if (phantom == null) {
                continue;
            }
            CompoundTag data = phantom.getPersistentData();
            data.putLong(UNTIL_TAG, until);
            // Re-stamped because the list closes up when an image is replaced, and the side decides
            // which shoulder the body is kept at.
            data.putInt(SIDE_TAG, side);
            placePhantom(phantom, player, side);
            level.sendParticles(ParticleTypes.END_ROD,
                    phantom.getX(), phantom.getY() + phantom.getBbHeight() * 0.6d, phantom.getZ(),
                    2, 0.2d, 0.5d, 0.2d, 0.0d);
        }
    }

    /**
     * Builds one mirror image.
     *
     * <p>The bare entity constructor is used on purpose: the fighting-clone constructor copies the
     * caster's equipment, arrows, effects and paper bomb, and a picture in the air must carry none of
     * them. The goals are cleared and the body is made invulnerable for the same reason - an illusion
     * that swung at something or popped at the first arrow would be a clone, not a trick.</p>
     *
     * @param side 0 for the caster's right hand, anything else for their left
     */
    private static ShadowCloneEntity spawn(ServerLevel level, Player caster, int side) {
        ShadowCloneEntity phantom = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        phantom.setOwner(caster);
        phantom.goalSelector.removeAllGoals(goal -> true);
        phantom.targetSelector.removeAllGoals(goal -> true);
        phantom.setNoGravity(true);
        phantom.setInvulnerable(true);
        CompoundTag data = phantom.getPersistentData();
        data.putBoolean(PHANTOM_TAG, true);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        data.putInt(SIDE_TAG, side);
        // Placed before it enters the world, so it is never seen standing at the origin for a tick.
        placePhantom(phantom, caster, side);
        level.addFreshEntity(phantom);
        return phantom;
    }

    /**
     * Puts a mirror image at one of the caster's sides. Shared with the upkeep in
     * {@code GenjutsuAdvancedEvents}, which is what catches an image left behind by a teleport.
     *
     * @param side 0 for the caster's right hand, anything else for their left
     */
    public static void placePhantom(ShadowCloneEntity phantom, Player caster, int side) {
        Vec3 look = caster.getViewVector(1.0f);
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        double rightX;
        double rightZ;
        if (horizontal < 1.0e-4d) {
            // Staring straight up or down leaves the view with no left or right; the body still has
            // one, and the images belong at its shoulders rather than at nothing.
            rightX = 1.0d;
            rightZ = 0.0d;
        } else {
            rightX = -look.z / horizontal;
            rightZ = look.x / horizontal;
        }
        double offset = side == 0 ? SIDE_OFFSET : -SIDE_OFFSET;
        phantom.setPos(caster.getX() + rightX * offset, caster.getY(), caster.getZ() + rightZ * offset);
        phantom.setDeltaMovement(Vec3.ZERO);
        phantom.setYRot(caster.getYRot());
        phantom.setYHeadRot(caster.getYRot());
    }

    @Nullable
    private static ShadowCloneEntity resolve(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof ShadowCloneEntity phantom ? phantom : null;
    }

    /** A stance is mastered by the time it is held, not by switching it on. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The images are ordinary entities: the client sees them through the usual entity tracking.
    }

    /** Drops the stance: the images go with it rather than standing around as forgotten copies. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        Hold hold = this.holds.remove(player.getUUID());
        if (hold == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (UUID id : hold.phantoms) {
            ShadowCloneEntity phantom = resolve(level, id);
            if (phantom != null) {
                // Faded rather than popped: an illusion has no body to leave smoke behind.
                phantom.discard();
            }
        }
    }

    /** @return whether the caster is wearing this illusion right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }
}
