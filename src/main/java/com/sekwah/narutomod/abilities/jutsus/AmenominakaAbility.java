package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.events.SixPathsEsotericEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Amenominaka: the caster takes the ground out from under everyone and puts them somewhere else.
 *
 * <p>The technique does not target a body, it targets a <em>place</em>. Everything living that
 * stands near the caster - friends and enemies alike, which is what makes it a last resort rather
 * than a duel opener - is dropped into the same pocket of nothing the Kamui eye opens, and held
 * there for eight seconds. The way back is written onto each of them when they go, so the return
 * costs the caster nothing and works for a mob just as well as for a player.</p>
 *
 * <p>It is the one power of the Six Paths that cannot be paid for by chakra alone: the eye has to be
 * open <em>and</em> the mode has to be up, because the mode is what gives the caster the authority
 * to move a piece of the world. Both gates are checked on the server, see {@link #handleCost}.</p>
 *
 * <p>The return is run from the world hook (see {@link SixPathsEsotericEvents}): players are caught
 * on the player tick and everything else on the living tick, so a body that was dragged in comes
 * back even if the caster is long gone or logged out.</p>
 */
public class AmenominakaAbility extends Ability implements Ability.Cooldown {

    /**
     * How far a cast of this technique carries as a game event. Moving part of the world is heard a
     * long way off.
     */
    @Override
    public int noiseRadius() {
        return 40;
    }

    /** C C C C C C - the full Six Paths seal. */
    public static final long COMBO = 111112L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "amenominaka");

    /** Game time at which a body dragged in is put back where it came from. */
    public static final String RETURN_UNTIL_TAG = "narutomod:amenominaka_until";

    /** The place a body was taken from, as a compound of {@code x}, {@code y}, {@code z} and a dimension id. */
    public static final String RETURN_POS_TAG = "narutomod:amenominaka_return";

    /** How far around the caster the sweep reaches. */
    public static final double RADIUS = 6.0d;

    /** How long everything caught stays in the void, in ticks. */
    public static final int DURATION = 160;

    /** Chakra for the whole sweep, and how long before the eye can do it again. */
    private static final float COST = 80.0f;
    private static final int COOLDOWN = 1200;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /**
     * The nine spots a sweep puts bodies on: the arrival pillar is five blocks wide, so three rows
     * of three fit on it without anyone landing in the void.
     */
    private static final double[] ARRIVAL_SLOTS = {1.0d, 2.5d, 4.0d};

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
        if (!SixPathsEsotericEvents.isSixPathsActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sixpaths.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (KamuiJutsuAbility.isInKamui(player)) {
            // There is nothing to swap: the caster is already standing in the place everyone would
            // be sent to, and dragging them into it again would only lose the way home.
            player.displayClientMessage(Component.translatable("jutsu.fail.amenominaka.inside",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || KamuiJutsuAbility.kamuiLevel(level) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kamui.nodimension",
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
        ServerLevel kamui = KamuiJutsuAbility.kamuiLevel(level);
        if (kamui == null) {
            return;
        }
        // The sweep is measured before anybody moves: once the caster is in the void their position
        // is on the arrival pillar, and the bodies around the old spot would no longer be found.
        List<LivingEntity> caught = nearby(level, player);
        Vec3 origin = player.position();
        markReturn(player, origin, level.dimension().location(), DURATION);
        KamuiJutsuAbility.transfer(player, kamui, arrival(0));

        int slot = 1;
        for (LivingEntity body : caught) {
            markReturn(body, body.position(), level.dimension().location(), DURATION);
            KamuiJutsuAbility.transfer(body, kamui, arrival(slot++));
        }

        // The ring and the sound stay where the sweep happened rather than following the caster
        // through the door, or nobody left behind would see what took their neighbours.
        sendRing(level, origin);
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.55f);
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.35f, 1.2f);
    }

    /** Everything living close enough to be caught by the sweep, the caster excluded. */
    private static List<LivingEntity> nearby(ServerLevel level, Player caster) {
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RADIUS),
                body -> canTake(caster, body));
    }

    /**
     * A spectator is already nowhere and a body that is in the void has nowhere to be taken. The
     * rest comes along, allies included: the technique is not aimed.
     */
    private static boolean canTake(Player caster, LivingEntity body) {
        if (body == caster || !body.isAlive() || body.isSpectator() || body.isRemoved()) {
            return false;
        }
        return !KamuiJutsuAbility.isInKamui(body);
    }

    /** One of the nine spots on the arrival pillar, wrapping round when more bodies are caught. */
    private static Vec3 arrival(int slot) {
        double x = ARRIVAL_SLOTS[Math.floorMod(slot, ARRIVAL_SLOTS.length)];
        double z = ARRIVAL_SLOTS[Math.floorMod(slot / ARRIVAL_SLOTS.length, ARRIVAL_SLOTS.length)];
        return new Vec3(x, KamuiJutsuAbility.CASTER_ARRIVAL.y, z);
    }

    /** The ring of portal light that marks how far the sweep reached. */
    private static void sendRing(ServerLevel level, Vec3 origin) {
        final int points = 48;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0d * i / points;
            level.sendParticles(ParticleTypes.PORTAL,
                    origin.x + Math.cos(angle) * RADIUS,
                    origin.y + 0.7d,
                    origin.z + Math.sin(angle) * RADIUS,
                    1, 0.02d, 0.25d, 0.02d, 0.02d);
        }
        level.sendParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1.0d, origin.z,
                40, 0.5d, 0.9d, 0.5d, 0.35d);
    }

    /**
     * Writes down where a body is to be put back, and when.
     *
     * <p>It rides on the entity's own persistent data, so it works for a mob as well as for a
     * player and survives the world being saved.</p>
     */
    public static void markReturn(Entity entity, Vec3 position, ResourceLocation dimension, int ticks) {
        CompoundTag tag = entity.getPersistentData();
        tag.putLong(RETURN_UNTIL_TAG, entity.level().getGameTime() + ticks);
        CompoundTag back = new CompoundTag();
        back.putDouble("x", position.x);
        back.putDouble("y", position.y);
        back.putDouble("z", position.z);
        back.putString("dimension", dimension.toString());
        tag.put(RETURN_POS_TAG, back);
    }

    /** @return whether this body has served its time in the void and is due to be put back. */
    public static boolean isDue(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.contains(RETURN_UNTIL_TAG) && entity.level().getGameTime() >= tag.getLong(RETURN_UNTIL_TAG);
    }

    /** Takes the marks off a body, used both when it goes home and when it found its own way out. */
    public static void clearReturn(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        tag.remove(RETURN_UNTIL_TAG);
        tag.remove(RETURN_POS_TAG);
    }

    /**
     * Puts a body back where the sweep picked it up.
     *
     * <p>A body that is no longer in the void has already left by other means - a caster's own Kamui
     * door, a death, a teleport - and is only relieved of its marks; yanking it back across the
     * world would undo a journey the player made on purpose.</p>
     */
    public static void returnHome(Entity entity) {
        if (!(entity.level() instanceof ServerLevel from)) {
            clearReturn(entity);
            return;
        }
        CompoundTag tag = entity.getPersistentData();
        CompoundTag back = tag.getCompound(RETURN_POS_TAG);
        Vec3 position = new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
        ResourceLocation dimension = ResourceLocation.tryParse(back.getString("dimension"));
        clearReturn(entity);

        if (!KamuiJutsuAbility.isInKamui(entity)) {
            return;
        }
        ServerLevel destination = destination(from, dimension);
        if (destination == null) {
            return;
        }
        KamuiJutsuAbility.playWarp(from, entity.position());
        KamuiJutsuAbility.transfer(entity, destination, position);
        KamuiJutsuAbility.playWarp(destination, position);
    }

    /** The world a body came from, or null if that world is no longer loaded. */
    @Nullable
    private static ServerLevel destination(ServerLevel from, @Nullable ResourceLocation dimension) {
        if (dimension == null || from.getServer() == null) {
            return null;
        }
        return from.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }
}
