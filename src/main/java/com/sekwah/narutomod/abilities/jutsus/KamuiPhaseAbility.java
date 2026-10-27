package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Obito's half-in-the-other-world trick, on its own key: while the stance is up the caster's body is
 * smoke, passing through walls and through anything that swings at them.
 *
 * <p>This used to be welded onto {@link KamuiJutsuAbility}, which made the eye do two unrelated
 * things at once and left no way to walk through a wall without also risking a trip into the pocket.
 * It is its own toggle now. Turning it on costs chakra every tick, so it is a stance to hold through
 * a wall and not a way to live.</p>
 *
 * <p>Two things have to be true on <b>both</b> sides for a walk through a wall to work. Collisions
 * are dropped ({@code noPhysics}) so the client stops pushing the caster back out of the block the
 * server is letting them pass through, and gravity is dropped so they do not sink through the floor
 * on the way. Both are applied from {@link #apply}, which is driven every tick before the player
 * moves - it has to be, because vanilla resets {@code noPhysics} at the top of {@code Player#tick}.</p>
 *
 * <p>Gravity being off would otherwise leave the caster unable to go up or down, only sideways, so
 * while phasing they are also given flight. The flight state they had before is written onto the
 * player and put back when the body turns solid again, so a creative caster keeps their wings and a
 * survival one does not suddenly keep them either.</p>
 */
public class KamuiPhaseAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** V C B - deliberately not a prefix of the Kamui eye, so the two never trip over each other. */
    public static final long COMBO = 2222L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kamui_phasing");

    /** Chakra burnt for every tick the body is not solid. Kept low: the stance is a long walk, not a blink. */
    private static final float CHAKRA_PER_TICK = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** NBT on the player holding the flight state to put back when the stance ends. */
    private static final String FLIGHT_TAG = "narutomod:kamuiPhaseFlight";
    private static final String MAYFLY_TAG = "mayfly";
    private static final String FLYING_TAG = "flying";

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
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
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
        // Mastery is paid for the time the body stays smoke, not for switching the stance on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        // The body is being kept out of the way by KamuiEvents#onLivingHurt, so there is nothing to
        // look after here beyond the odd wisp so the caster can see the stance is up.
        if (player.level() instanceof ServerLevel level && player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    3, 0.3d, 0.5d, 0.3d, 0.05d);
        }
    }

    /**
     * A stance is mastered by holding it, not by switching it on, so the credit is paid per active
     * tick from {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The toggle state is synced; the client only has to keep not colliding, see apply().
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        release(player, ninjaData);
    }

    /** @return whether the caster's body is currently smoke. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * Applies, or takes away, the phasing body. Called every tick on both sides, before the player
     * moves.
     *
     * <p>It has to run that early: {@code Player#tick} starts with {@code noPhysics = isSpectator()},
     * so anything written after the player has moved is wiped again before the next step and the
     * caster walks into walls like anybody else. The flight half is server side only, since player
     * abilities are synced from there.</p>
     */
    public static void apply(Player player) {
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .ifPresent(ninjaData -> {
                    boolean phasing = isActive(ninjaData);
                    // A spectator walks through walls by vanilla rule, and that has to survive this
                    // hook: writing noPhysics = false under them put the spectator back into
                    // collision with the world, which is exactly what a spectator must never have.
                    boolean passThrough = phasing || player.isSpectator();
                    if (player.noPhysics != passThrough) {
                        player.noPhysics = passThrough;
                    }
                    if (phasing) {
                        player.setNoGravity(true);
                    } else if (!player.isSpectator()) {
                        // Spectators keep the vanilla floating state; only a caster who just turned
                        // solid gets gravity back.
                        player.setNoGravity(false);
                    }
                    if (!player.level().isClientSide) {
                        applyFlight(player, phasing);
                    }
                });
    }

    /**
     * Gives the caster control in all three directions while they are phasing, and puts back the
     * flight they had before once they are solid again.
     */
    private static void applyFlight(Player player, boolean phasing) {
        CompoundTag tag = player.getPersistentData();
        if (phasing) {
            if (!tag.contains(FLIGHT_TAG)) {
                CompoundTag saved = new CompoundTag();
                saved.putBoolean(MAYFLY_TAG, player.getAbilities().mayfly);
                saved.putBoolean(FLYING_TAG, player.getAbilities().flying);
                tag.put(FLIGHT_TAG, saved);
            }
            if (!player.getAbilities().mayfly || !player.getAbilities().flying) {
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            }
            return;
        }
        if (!tag.contains(FLIGHT_TAG)) {
            return;
        }
        CompoundTag saved = tag.getCompound(FLIGHT_TAG);
        tag.remove(FLIGHT_TAG);
        player.getAbilities().mayfly = saved.getBoolean(MAYFLY_TAG);
        player.getAbilities().flying = saved.getBoolean(FLYING_TAG);
        player.onUpdateAbilities();
    }

    /**
     * Puts the caster down safely and gives them their body back, used both when the stance is
     * switched off and when death or a logout should have ended it.
     */
    public static void release(Player player, INinjaData ninjaData) {
        applyFlight(player, false);
        // A spectator is allowed to stand inside walls; restoring collisions there would be the bug,
        // not the fix.
        player.noPhysics = player.isSpectator();
        if (!player.isSpectator()) {
            player.setNoGravity(false);
        }
        unstick(player);
    }

    /**
     * Puts a caster who switched the stance off while standing inside a wall back into open air, so
     * phasing is never a way to be buried alive.
     */
    public static void unstick(Player player) {
        Level level = player.level();
        if (level.noCollision(player, player.getBoundingBox())) {
            return;
        }
        BlockPos origin = player.blockPosition();
        for (int dy = 0; dy <= 4; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    Vec3 spot = Vec3.atBottomCenterOf(pos);
                    if (level.noCollision(player, player.getBoundingBox().move(spot.subtract(player.position())))) {
                        player.teleportTo(spot.x, spot.y, spot.z);
                        return;
                    }
                }
            }
        }
    }
}
