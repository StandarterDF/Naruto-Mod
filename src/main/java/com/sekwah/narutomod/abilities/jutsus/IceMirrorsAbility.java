package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Haku's demonic ice mirrors: a ring of ice panes to stand between, and a body that is never where
 * the next blow lands.
 *
 * <p>A ninja with a water nature and the right blood can freeze the moisture in the air into
 * mirrors that are half ice and half trick of the light. The technique is a place rather than a
 * strike: five panes are raised in a ring around the caster, and while the hold lasts the caster
 * steps from one to the next every second, so anything aimed at them is aimed at where they were.
 * The tell is the brief shimmer of a body that has already gone - a few ticks of hardened skin, and
 * then the ring has moved again.</p>
 *
 * <p>The ring is made of {@link Display.BlockDisplay} entities rather than real ice blocks: a mirror
 * must not be minable, must not hold up water, and must vanish with the technique. They carry
 * {@link #MIRROR_TAG} and {@link #UNTIL_TAG} so that the support hooks can find them again - a hit
 * that lands on the caster, a logout or a server restart all have to be able to take the ring down
 * without holding a reference to the ability, see {@code ClanTechniqueEvents}.</p>
 */
public class IceMirrorsAbility extends Ability implements Ability.Channeled {

    /** A ring of cracking ice is heard about as far as a thrown kunai. */
    @Override
    public int noiseRadius() {
        return 12;
    }

    /** B V B A A A - the water release seal, then the three keys of the mirror ring. */
    public static final long COMBO = 231111L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "ice_mirrors");

    /** Set on every mirror entity, with the tick it melts at and the caster it belongs to. */
    public static final String MIRROR_TAG = "narutomod:ice_mirror";
    public static final String UNTIL_TAG = "narutomod:ice_mirror_until";
    public static final String OWNER_TAG = "narutomod:ice_mirror_owner";

    /** How many panes a ring holds, and how far out they stand from the caster. */
    private static final int MIRROR_COUNT = 5;
    private static final double RING_RADIUS = 5.0d;

    /** How long a ring lasts: fifteen seconds, whether or not the caster keeps holding the seal. */
    private static final int LIFETIME = 300;

    /** The caster steps to the next pane once a second. */
    private static final int HOP_INTERVAL = 20;

    /** The moment after a hop in which the caster is already somewhere else. */
    private static final int RESIST_TICKS = 6;
    private static final int RESIST_AMPLIFIER = 2;

    /** What raising the ring costs, and what every tick of standing in it costs on top. */
    private static final float COST = 30.0f;
    private static final float COST_PER_TICK = 0.3f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How many snowflakes are scattered when a pane appears, hops or shatters. */
    private static final int PLACE_PARTICLES = 40;
    private static final int HOP_PARTICLES = 16;

    /**
     * One pane of the ring: the level it stands in (the caster may leave it), the entity to take it
     * down with, and the spot to step onto. The spot is kept next to the id because a hop must still
     * work if the client never received the display.
     */
    private record Mirror(ResourceKey<Level> dimension, UUID entity, Vec3 pos) {
    }

    /**
     * The rings currently on the field, keyed by caster. The ability is a registry singleton, so
     * this is the one place the state of a hold lives; it is dropped the moment the ring goes down.
     */
    private static final Map<UUID, List<Mirror>> RINGS = new HashMap<>();

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A tap never has the time to raise a ring, let alone step through it. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The ring melts on its own after fifteen seconds; refusing the upkeep here is what makes
        // the channel close itself instead of charging for a ring that is already gone.
        if (chargeAmount >= LIFETIME) {
            return false;
        }
        // The seal is paid for once, on the tick the hold opens; after that only the upkeep is left.
        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeAmount == 0) {
            // A ring left behind by an interrupted hold is taken down before the new one goes up, so
            // a caster can never end up with two rings around them.
            breakMirrors(player);
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The ring is lived in, so the technique is mastered by the time it is held.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (ticksChanneled == 0) {
            raise(level, player);
            return;
        }
        List<Mirror> ring = RINGS.get(player.getUUID());
        // A missing ring means the support hooks took it down: a blow landed on the caster, or the
        // panes melted. Either way there is nothing left to stand in.
        if (ring == null || ring.isEmpty()) {
            this.finish(player, ninjaData);
            return;
        }
        if (ticksChanneled % HOP_INTERVAL != 0) {
            return;
        }
        hop(level, player, ring.get((ticksChanneled / HOP_INTERVAL - 1) % ring.size()));
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        breakMirrors(player);
    }

    /**
     * The ring is mastered by the time it is held, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** @return whether this caster still has a ring standing, asked by the hit hook. */
    public static boolean hasMirrors(Player caster) {
        return RINGS.containsKey(caster.getUUID());
    }

    /**
     * Takes a caster's ring off the field and forgets it. Called when the hold ends by any route: the
     * keys coming up, the chakra running out, the panes melting, a blow landing, or the caster
     * leaving the world.
     */
    public static void breakMirrors(Player caster) {
        List<Mirror> ring = RINGS.remove(caster.getUUID());
        if (ring == null) {
            return;
        }
        for (Mirror mirror : ring) {
            ServerLevel level = levelOf(caster, mirror);
            if (level == null) {
                continue;
            }
            Entity display = level.getEntity(mirror.entity());
            if (display != null) {
                display.discard();
            }
            shards(level, mirror.pos());
        }
        if (caster.level() instanceof ServerLevel level) {
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.5f);
        }
    }

    /** Closes the hold early, once the ring under the caster is gone. */
    private void finish(Player player, INinjaData ninjaData) {
        breakMirrors(player);
        player.displayClientMessage(Component.translatable("jutsu.ice_mirrors.broken",
                Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                true);
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    /**
     * Raises the ring: five panes spread evenly around the caster, the first of them straight ahead
     * so a caster always knows which way the first step goes.
     */
    private static void raise(ServerLevel level, Player caster) {
        List<Mirror> ring = new ArrayList<>(MIRROR_COUNT);
        long until = level.getGameTime() + LIFETIME;
        double step = Math.PI * 2.0d / MIRROR_COUNT;
        Vec3 look = caster.getLookAngle();
        double base = Math.atan2(look.x, look.z);

        for (int i = 0; i < MIRROR_COUNT; i++) {
            double angle = base + step * i;
            Vec3 pos = new Vec3(
                    caster.getX() + Math.sin(angle) * RING_RADIUS,
                    caster.getY(),
                    caster.getZ() + Math.cos(angle) * RING_RADIUS);
            Mirror mirror = pane(level, pos, until, caster);
            if (mirror != null) {
                ring.add(mirror);
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1.0d, pos.z,
                    HOP_PARTICLES, 0.4d, 0.8d, 0.4d, 0.01d);
        }
        // A ring that failed to raise anything is still a paid hold, so it is remembered as an empty
        // ring and closed on the next tick rather than silently leaking the channel.
        RINGS.put(caster.getUUID(), ring);

        level.sendParticles(ParticleTypes.SNOWFLAKE, caster.getX(), caster.getY() + 1.0d, caster.getZ(),
                PLACE_PARTICLES, RING_RADIUS * 0.7d, 0.9d, RING_RADIUS * 0.7d, 0.02d);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /**
     * Builds one pane. The block state goes in through NBT because 1.20.1 keeps the display's block
     * state behind a private setter; loading it is the same path a saved display takes on a reload.
     */
    @Nullable
    private static Mirror pane(ServerLevel level, Vec3 pos, long until, Player caster) {
        Display.BlockDisplay mirror = EntityType.BLOCK_DISPLAY.create(level);
        if (mirror == null) {
            return null;
        }
        CompoundTag state = new CompoundTag();
        state.put(Display.BlockDisplay.TAG_BLOCK_STATE, NbtUtils.writeBlockState(Blocks.ICE.defaultBlockState()));
        mirror.load(state);
        mirror.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);

        CompoundTag data = mirror.getPersistentData();
        data.putBoolean(MIRROR_TAG, true);
        data.putLong(UNTIL_TAG, until);
        data.putUUID(OWNER_TAG, caster.getUUID());

        level.addFreshEntity(mirror);
        return new Mirror(level.dimension(), mirror.getUUID(), pos);
    }

    /**
     * Steps the caster onto one pane and leaves them briefly hardened there. A hop into a wall is
     * skipped rather than shoved: the caster keeps the tick where they are and simply waits for the
     * next pane in the ring.
     */
    private static void hop(ServerLevel level, Player caster, Mirror mirror) {
        AABB arrival = caster.getBoundingBox().move(mirror.pos().subtract(caster.position()));
        if (level != levelOf(caster, mirror) || !level.noCollision(caster, arrival)) {
            return;
        }
        Vec3 from = caster.position();
        // A hop is deliberate travel, so the ground it passes over is not a fall.
        caster.teleportTo(mirror.pos().x, mirror.pos().y, mirror.pos().z);
        caster.fallDistance = 0.0f;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESIST_TICKS,
                RESIST_AMPLIFIER, false, false));

        level.sendParticles(ParticleTypes.SNOWFLAKE, from.x, from.y + 1.0d, from.z,
                HOP_PARTICLES, 0.3d, 0.8d, 0.3d, 0.01d);
        level.sendParticles(ParticleTypes.SNOWFLAKE, mirror.pos().x, mirror.pos().y + 1.0d, mirror.pos().z,
                HOP_PARTICLES, 0.3d, 0.8d, 0.3d, 0.01d);
        level.playSound(null, mirror.pos().x, mirror.pos().y, mirror.pos().z,
                SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.6f, 1.4f);
    }

    /** @return the level the pane stands in, or null if it is not loaded any more. */
    @Nullable
    private static ServerLevel levelOf(Player caster, Mirror mirror) {
        if (caster.getServer() == null) {
            return null;
        }
        return caster.getServer().getLevel(mirror.dimension());
    }

    /** A puff of ice dust where a pane was. */
    private static void shards(ServerLevel level, Vec3 pos) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1.0d, pos.z,
                PLACE_PARTICLES, 0.5d, 0.9d, 0.5d, 0.02d);
    }
}
