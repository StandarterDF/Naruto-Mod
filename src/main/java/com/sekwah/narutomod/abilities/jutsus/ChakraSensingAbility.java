package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.s2c.ClientboundSensingPacket;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.world.WorldEnvironment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chakra sensing: closing one's eyes to the world and feeling what is out there instead.
 *
 * <p>The technique is a channelled one with two readings, told apart by how the combo is entered. Held
 * down it is the sense itself: chakra is poured out tick by tick and everything living within reach is
 * outlined to the caster, through walls and at a glance. Tapped it is the night vision trick instead -
 * a cheaper, self-sustaining channel of chakra through the eyes that lets a ninja see in the dark for
 * as long as they keep paying for it.</p>
 *
 * <p>What the sense shows grows with mastery, and it grows in kinds: a beginner feels only ordinary
 * creatures, and only as a sparse, flickering shell of particles; as they get the hang of it that
 * shell thickens until the creature itself stands out through the walls in full, and only then do
 * hostile mobs begin to appear the same way, and after them other people. A master also feels the
 * land, with markers for the structures around them. The reach widens all the while and the upkeep
 * falls away almost to nothing, so a finished sensor can keep the whole world open for next to no
 * chakra. The picture itself is drawn on the caster's own client - the shell and the revealed bodies
 * are a client side render and the structure markers arrive over {@link ClientboundSensingPacket} -
 * so the technique is invisible to everyone else.</p>
 */
public class ChakraSensingAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. Silent: nothing here disturbs the world.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }


    /** C B C C */
    public static final long COMBO = 32L;

    /** The registry id of this technique, which is also the key its mastery is stored under. */
    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_sensing");

    /** Chakra burnt for every tick the sense is held open. */
    private static final float SENSE_PER_TICK = 0.3f;

    /** A short regen delay while sensing, so the technique is not immediately refilled. */
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** Reach of an untrained sense, in blocks. Mastery widens it up to the configured ceiling. */
    public static final float BASE_RADIUS = 16f;

    /** How much wider a full moon opens the sense. */
    private static final float MOON_RADIUS_BONUS = 0.25f;

    /**
     * The sense is taught in steps, and each kind of creature has two of them: a mastery at which it
     * first shows as a sparse, dim shell of particles, and a later one at which the shell fills in
     * and resolves into the creature's own body seen through the walls. Between the two the shell
     * thickens on a plain linear ramp, so the reveal is one continuous thing rather than a jump. The
     * bands are spread so that finishing one creature leaves a stretch where the sense feels settled
     * before the next kind starts to appear.
     */
    public static final float ANIMALS_OUTLINE_AT = 0.0f;
    public static final float ANIMALS_ESP_AT = 0.15f;
    public static final float HOSTILE_OUTLINE_AT = 0.30f;
    public static final float HOSTILE_ESP_AT = 0.45f;
    public static final float PLAYERS_OUTLINE_AT = 0.60f;
    public static final float PLAYERS_ESP_AT = 0.80f;

    /** Mastery at which structure markers start turning up; a late reward of a finished sensor. */
    public static final float STRUCTURES_AT = 0.90f;

    /**
     * The faintest a creature can show: the moment its band opens it is already a few dim motes, not
     * nothing at all, so the very first sense of a brand new ninja still has something in it.
     */
    private static final float MIN_VISIBLE = 0.08f;

    /**
     * The extra share of the sense's upkeep that falls away by full mastery, on top of the generic
     * mastery discount: a beginner burns a lot of chakra to hold the sense open, a master barely
     * notices it.
     */
    private static final float SENSE_COST_MASTERY_DISCOUNT = 0.8f;

    /** How often the server rescans the loaded chunks for structures, in ticks. */
    private static final int STRUCTURE_SCAN_INTERVAL = 20;

    /** At most this many structure markers are sent at once. */
    private static final int MAX_MARKERS = 32;

    /** A hard ceiling on the tapped night vision, so a full bar cannot buy it forever. */
    private static final int NIGHT_VISION_MAX_TICKS = 20 * 180;

    /**
     * How long the night vision effect is handed out for. It is refreshed every tick so the exact
     * value only matters if a refresh is ever missed - but a value under ten seconds makes the
     * effect icon blink on the HUD, so it is kept comfortably above that.
     */
    private static final int NIGHT_VISION_EFFECT_TICKS = 20 * 20;

    /** Share of the night vision upkeep that falls away by full mastery, so a master's is nearly free. */
    private static final float NIGHT_VISION_MASTERY_DISCOUNT = 0.9f;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            // The tap is the night vision trick, and the tree has to have taught it: a caster who
            // has only bought the sense itself can still hold to look around, but tapping does
            // nothing until the night vision node is theirs.
            if (!nightVisionUnlocked(ninjaData)) {
                player.displayClientMessage(Component.translatable("jutsu.fail.sensing.nightvision_locked",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            // Its upkeep is paid tick by tick in maintain, so all that is checked here is that there
            // is a bar to draw on at all.
            if (ninjaData.getChakra() <= 0f) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            return true;
        }
        float cost = this.masteryCost(ninjaData, SENSE_PER_TICK);
        // The sense is steep to hold at first and nearly free once it is lived in, on top of the
        // generic mastery discount every technique gets.
        cost *= 1f - SENSE_COST_MASTERY_DISCOUNT * Mastery.level(ninjaData, ID);
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
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        ninjaData.setSensingRadius(radius(ninjaData, player));
        // Sensing is lived in, not cast: its mastery is paid for the time the sense is actually
        // open, so a long look is worth more than a tapped-on and tapped-off one. See recordMastery.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (player.level() instanceof ServerLevel level && ticksChanneled % 5 == 0) {
            // A faint ring at the caster's feet, so it is obvious the sense is running.
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1d, player.getZ(),
                    4, 0.5d, 0.05d, 0.5d, 0.0d);
        }
        if (structuresUnlocked(ninjaData) && ticksChanneled % STRUCTURE_SCAN_INTERVAL == 0
                && player instanceof ServerPlayer serverPlayer) {
            sendStructures(serverPlayer, ninjaData);
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive != -1) {
            // The sense is a held thing: when the keys are let go the picture goes with them.
            ninjaData.setSensingRadius(0f);
            if (player instanceof ServerPlayer serverPlayer) {
                PacketHandler.sendToPlayer(ClientboundSensingPacket.empty(), serverPlayer);
            }
            return;
        }
        // A tap: the night vision trick, toggled off again by a second tap.
        if (ninjaData.getNightVisionTicks() > 0) {
            stopNightVision(player, ninjaData);
        } else {
            ninjaData.setNightVisionTicks(NIGHT_VISION_MAX_TICKS);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_EFFECT_TICKS, 0, true, false));
            player.displayClientMessage(Component.translatable("jutsu.sensing.nightvision")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /**
     * Sensing is not mastered by a bare cast but by the time it is actually used, so the credit is
     * paid tick by tick from {@link #handleChannelling} and {@link #maintain} instead and this
     * deliberately records nothing - otherwise flicking the tap on and off would master it fastest.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Keeps a tapped night vision fed and lit, called every server tick from
     * {@code PlayerEvents#onEntityUpdate} the same way sage mode is. It is on the pool, not a timer:
     * the moment the chakra runs dry the trick closes on its own.
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        int ticks = ninjaData.getNightVisionTicks();
        if (ticks <= 0) {
            return;
        }
        // If the node was somehow lost (a reset tree, an old save), the trick goes off rather than
        // keeping the caster lit for free.
        if (!nightVisionUnlocked(ninjaData)) {
            stopNightVision(player, ninjaData);
            return;
        }
        float base = (float) (NarutoConfig.sensingNightVisionChakraPerSecond / 20.0d);
        float cost = base * (1.0f - NIGHT_VISION_MASTERY_DISCOUNT * Mastery.level(ninjaData, ID));
        if (ninjaData.getChakra() < cost) {
            stopNightVision(player, ninjaData);
            return;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        ninjaData.setNightVisionTicks(ticks - 1);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_EFFECT_TICKS, 0, true, false));
        // The night vision half of the technique is a use too, so it also pays mastery by the time
        // it stays lit.
        NarutoAbilities.CHAKRA_SENSING.get().recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    private static void stopNightVision(Player player, INinjaData ninjaData) {
        ninjaData.setNightVisionTicks(0);
        player.removeEffect(MobEffects.NIGHT_VISION);
        player.displayClientMessage(Component.translatable("jutsu.sensing.nightvision.off")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** Reach of the sense at the caster's mastery, from {@link #BASE_RADIUS} up to the config ceiling. */
    public static float radius(INinjaData ninjaData) {
        float level = Mastery.level(ninjaData, ID);
        double max = Math.max(BASE_RADIUS, NarutoConfig.sensingRadiusMax);
        return (float) (BASE_RADIUS + (max - BASE_RADIUS) * level);
    }

    /**
     * The same reach after the night has had its say: under a full moon the sense opens wider, while
     * by day and on a new moon it is the plain radius.
     */
    public static float radius(INinjaData ninjaData, Player player) {
        return radius(ninjaData) * (1.0f + MOON_RADIUS_BONUS * WorldEnvironment.moonClarity(player));
    }

    /**
     * How far a creature of one kind has resolved in the sense at the given mastery: 0 while it is
     * still hidden, 1 once it is seen in full, and a plain ramp between the two thresholds that the
     * renderer reads as particle density.
     */
    public static float intensity(float level, float outlineAt, float espAt) {
        if (level < outlineAt) {
            return 0f;
        }
        if (level >= espAt) {
            return 1f;
        }
        float t = (level - outlineAt) / (espAt - outlineAt);
        return MIN_VISIBLE + (1f - MIN_VISIBLE) * t;
    }

    /** How resolved ordinary creatures (anything living that is not hostile and not a player) are. */
    public static float animalIntensity(float level) {
        return intensity(level, ANIMALS_OUTLINE_AT, ANIMALS_ESP_AT);
    }

    /** How resolved hostile mobs are. */
    public static float hostileIntensity(float level) {
        return intensity(level, HOSTILE_OUTLINE_AT, HOSTILE_ESP_AT);
    }

    /** How resolved other players are. */
    public static float playerIntensity(float level) {
        return intensity(level, PLAYERS_OUTLINE_AT, PLAYERS_ESP_AT);
    }

    /** @return whether structures have started showing up in the sense. */
    public static boolean structuresUnlocked(INinjaData ninjaData) {
        return Mastery.level(ninjaData, ID) >= STRUCTURES_AT;
    }

    /** @return whether the tree has taught the tap - the night vision half of the technique. */
    public static boolean nightVisionUnlocked(INinjaData ninjaData) {
        return SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "mechanic:night_vision");
    }

    /**
     * Feels out every structure in the loaded chunks around the caster and sends the nearest handful
     * over. Only starts that were generated here are seen - an unexplored stretch of map has nothing
     * for the sense to find, which is fair enough.
     */
    private static void sendStructures(ServerPlayer player, INinjaData ninjaData) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        float radius = radius(ninjaData, player);
        double radiusSqr = (double) radius * radius;
        BlockPos origin = player.blockPosition();
        int chunkRadius = Mth.ceil(radius / 16f);
        ChunkPos center = player.chunkPosition();
        Set<Long> seen = new HashSet<>();
        List<ClientboundSensingPacket.Marker> markers = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = center.x + dx;
                int cz = center.z + dz;
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
                    StructureStart start = entry.getValue();
                    if (start == null || !start.isValid()) {
                        continue;
                    }
                    BlockPos mid = start.getBoundingBox().getCenter();
                    if (origin.distSqr(mid) > radiusSqr) {
                        continue;
                    }
                    if (!seen.add(mid.asLong())) {
                        continue;
                    }
                    ResourceLocation structureId = level.registryAccess()
                            .registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                    if (structureId == null) {
                        continue;
                    }
                    markers.add(new ClientboundSensingPacket.Marker(structureId, mid));
                }
            }
        }
        markers.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(origin)));
        if (markers.size() > MAX_MARKERS) {
            markers = markers.subList(0, MAX_MARKERS);
        }
        PacketHandler.sendToPlayer(new ClientboundSensingPacket(markers), player);
    }
}
