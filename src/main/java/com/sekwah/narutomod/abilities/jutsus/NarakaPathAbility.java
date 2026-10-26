package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The king of hell's court: a patch of ground where the living are weighed.
 *
 * <p>The Rinnegan is not only a hand that takes. This is the one path that gives back: a court
 * raised around a point the caster can see, inside which the wounded are mended and the sickness
 * of the battlefield is lifted, while everyone the caster is fighting is made too weak to swing
 * properly. It is a place, not a blast - the caster chooses where the fight is allowed to happen
 * and the court decides who benefits from standing in it.</p>
 *
 * <p>The court outlives the cast by a handful of seconds, so it cannot simply be an effect on the
 * caster: the zone is written into a server-side record keyed by its centre and its expiry, and
 * {@code RinneganExtraEvents} ticks it. Nothing of the zone is stored on the client - the player
 * only sees the ring of soul fire the server keeps drawing.</p>
 */
public class NarakaPathAbility extends Ability implements Ability.Cooldown {

    /** A judgement this size is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** 2 3 3 2 3 1 - the Rinnegan stance (2 3 3 2) with two more keys on the end. */
    public static final long COMBO = 233231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "naraka_path");

    /** What the court costs to raise, and how long it takes before it can be raised again. */
    private static final float COST = 60.0f;
    private static final int COOLDOWN = 700;

    /** How far away the court may be placed, measured along the caster's gaze. */
    private static final double PLACE_RANGE = 16.0d;

    /** The court itself: how wide it reaches and how long it stands. */
    public static final double RADIUS = 5.0d;
    public static final int DURATION = 160;

    /** How often the court judges whoever is standing in it. */
    public static final long JUDGEMENT_INTERVAL = 20L;

    /** Health restored to each ally on every judgement. */
    public static final float HEAL_AMOUNT = 2.0f;

    /** How long the weakness laid on an enemy lasts; it is refreshed by every judgement. */
    public static final int WEAKNESS_DURATION = 40;

    /** Ticks before the caster's own regeneration resumes after paying for the court. */
    private static final int CHAKRA_REGEN_DELAY = 20;

    /**
     * The courts currently standing. The ability is a registry singleton, so this is where the
     * state of a placed court lives; entries are removed by the event class once they expire, and
     * only ever touched from the server thread.
     */
    private static final List<NarakaZone> ZONES = new ArrayList<>();

    /**
     * One standing court: where it is, who raised it, and the window in which it judges.
     *
     * @param level     the level the court was raised in
     * @param centre    the point the court is centred on
     * @param caster    the player whose side the court favours
     * @param startedAt the game time the court was raised
     * @param expiresAt the game time the court closes
     */
    public record NarakaZone(ServerLevel level, Vec3 centre, UUID caster, long startedAt, long expiresAt) {
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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = centreFor(player);
        long now = level.getGameTime();
        ZONES.add(new NarakaZone(level, centre, player.getUUID(), now, now + DURATION));

        drawRing(level, centre);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /**
     * Where the court is raised. The caster chooses a surface if they are looking at one, otherwise
     * it opens where they stand; a court in mid-air would be a slow fall for everyone in it.
     */
    private static Vec3 centreFor(Player player) {
        BlockHitResult looked = Targeting.lookedAtBlock(player, PLACE_RANGE);
        if (looked != null) {
            return looked.getLocation();
        }
        return player.position();
    }

    /**
     * A ring of soul fire on the ground, which is all the court ever shows of itself.
     */
    public static void drawRing(ServerLevel level, Vec3 centre) {
        for (int i = 0; i < 32; i++) {
            double angle = (Math.PI * 2.0d / 32.0d) * i;
            double x = centre.x + Math.cos(angle) * RADIUS;
            double z = centre.z + Math.sin(angle) * RADIUS;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, centre.y + 0.1d, z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        level.sendParticles(ParticleTypes.SOUL, centre.x, centre.y + 0.5d, centre.z,
                4, RADIUS * 0.4d, 0.3d, RADIUS * 0.4d, 0.01d);
    }

    /**
     * The courts currently standing. The list is live on purpose: the ticking event removes a court
     * from it the moment it closes, so there is one record rather than a copy that can disagree.
     */
    public static List<NarakaZone> zones() {
        return ZONES;
    }
}
