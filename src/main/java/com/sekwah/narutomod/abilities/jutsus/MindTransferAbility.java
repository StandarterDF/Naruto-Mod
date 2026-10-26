package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Yamanaka mind transfer: the caster's mind goes into a creature and leaves the body behind.
 *
 * <p>There is no blast and no blade in it. What the technique does is take the body of an animal or
 * a person away from the mind that was using it: the creature stops dead, its eyes cloud, and the
 * caster's own body stands slack where it was, empty and defenceless. A body nobody is in takes
 * twice the harm it should, which is the whole price of the technique.</p>
 *
 * <p>The mod has no second camera to move, so the transfer is told with the tools it has: the
 * caster is folded into the taken creature's spot, made invisible for as long as the hold lasts, and
 * their body is pinned there - so from outside there is one creature standing still and nobody
 * beside it. The original body's spot and the creature's id are written to persistent data rather
 * than kept in a field, because the body has to be brought back by whichever hook notices the hold
 * ending, including after a relog, see {@code ClanTechniqueEvents}.</p>
 */
public class MindTransferAbility extends Ability implements Ability.Channeled, Ability.Cooldown {

    /** A mind leaving its body is a quiet thing; only the body it leaves behind is worth noticing. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** B C B B A A - the Yamanaka seal, then two keys for the transfer. */
    public static final long COMBO = 132211L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mind_transfer");

    /**
     * The marks of a hold. On the caster's body: the abandoned body flag, the id of the creature
     * that was taken, the tick the mind has to come back by, and the spot to come back to. On the
     * creature: who is inside it, the same deadline, and whether it had a mind of its own to give
     * back.
     */
    public static final String BODY_TAG = "narutomod:mind_transfer_body";
    public static final String MOB_TAG = "narutomod:mind_transfer_mob";
    public static final String UNTIL_TAG = "narutomod:mind_transfer_until";
    public static final String HOME_TAG = "narutomod:mind_transfer_home";
    public static final String OWNER_TAG = "narutomod:mind_transfer_owner";
    public static final String HAD_AI_TAG = "narutomod:mind_transfer_had_ai";

    /** How far the caster can reach a mind they can see. */
    private static final double RANGE = 16.0d;

    private static final float COST = 35.0f;
    private static final int COOLDOWN = 600;

    /** Twenty seconds inside another body, then the mind is pulled home whether it wants to be or not. */
    private static final int DURATION = 400;

    /**
     * Anything this sturdy is not a mind a genin can walk into: a tailed beast, a boss. The line is
     * health rather than a list of names so that datapack mobs are covered too.
     */
    private static final float TOO_STRONG_HEALTH = 100.0f;

    /** The empty body is made too heavy to walk off while its mind is elsewhere. */
    private static final int BODY_SLOWNESS_AMPLIFIER = 6;
    private static final int BODY_SLOWNESS_TICKS = 10;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How often the pale light of the transfer is drawn while it lasts. */
    private static final int PARTICLE_INTERVAL = 5;
    private static final int PARTICLE_COUNT = 8;

    private static final UUID NO_ENTITY = new UUID(0L, 0L);

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

    /** The seal has to be held to settle into the other mind; a tap does not get that far. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The transfer is paid for once, when the seal opens. Holding it afterwards costs nothing:
        // the price of it is the body left standing empty, not chakra.
        if (chargeAmount > 0) {
            return true;
        }
        if (this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        // A body still wearing the marks of a hold that never ended is brought home before a new
        // one starts, so two holds can never overwrite each other's way back.
        release(player);
        Mob target = lookedAtMob(player, ninjaData);
        if (target == null) {
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        // The one thing this technique has to remember between the cost and the first tick.
        player.getPersistentData().putUUID(MOB_TAG, target.getUUID());
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ticksChanneled == 0) {
            Mob target = taken(player, level);
            if (target == null) {
                end(player, ninjaData);
                return;
            }
            take(level, player, target);
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
            return;
        }

        Mob target = taken(player, level);
        // The deadline, the creature dying, the creature being taken away by something else: all of
        // them end the hold the same way, with the mind pulled back to the body it left.
        if (target == null || level.getGameTime() >= player.getPersistentData().getLong(UNTIL_TAG)) {
            end(player, ninjaData);
            return;
        }
        maintain(level, player, target, ticksChanneled);
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    /** However the hold ended, the body has to go home; see {@link #release(Player)}. */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        release(player);
    }

    /**
     * The transfer is mastered by the time it is lived in, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Puts the caster's mind back where its body was left and gives the creature its own mind back.
     *
     * <p>This is the one place a hold is undone, and it is written to be safe to call twice: a hold
     * that already ended has cleared its marks, so the second call finds nothing to undo. It is
     * called by the ability when the channel closes and by the support hooks when the creature dies
     * or the caster leaves, so none of those routes can leave a body stranded or a mob frozen.</p>
     */
    public static void release(Player caster) {
        CompoundTag body = caster.getPersistentData();
        UUID mobId = body.getUUID(MOB_TAG);
        CompoundTag home = body.getCompound(HOME_TAG);
        boolean transferred = body.getBoolean(BODY_TAG);

        body.remove(BODY_TAG);
        body.remove(MOB_TAG);
        body.remove(UNTIL_TAG);
        body.remove(HOME_TAG);
        if (!transferred) {
            return;
        }

        caster.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        caster.setInvisible(false);
        if (caster.level() instanceof ServerLevel level) {
            giveBack(level, mobId);
        }
        goHome(caster, home);
    }

    /** @return whether this caster is currently inside another body, asked by the damage hook. */
    public static boolean isTransferred(Player caster) {
        return caster.getPersistentData().getBoolean(BODY_TAG);
    }

    /** Ends a hold that cannot go on, on the server's own initiative rather than from a key release. */
    private static void end(Player player, INinjaData ninjaData) {
        release(player);
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    /** Settles the mind into the creature: the body pins down, the creature goes quiet. */
    private static void take(ServerLevel level, Player caster, Mob target) {
        CompoundTag body = caster.getPersistentData();
        CompoundTag home = new CompoundTag();
        home.putDouble("x", caster.getX());
        home.putDouble("y", caster.getY());
        home.putDouble("z", caster.getZ());
        home.putString("dimension", caster.level().dimension().location().toString());

        long until = level.getGameTime() + DURATION;
        body.putBoolean(BODY_TAG, true);
        body.putUUID(MOB_TAG, target.getUUID());
        body.putLong(UNTIL_TAG, until);
        body.put(HOME_TAG, home);

        CompoundTag data = target.getPersistentData();
        data.putUUID(OWNER_TAG, caster.getUUID());
        data.putLong(UNTIL_TAG, until);
        data.putBoolean(HAD_AI_TAG, !target.isNoAi());
        target.setNoAi(true);

        caster.teleportTo(target.getX(), target.getY(), target.getZ());
        caster.setInvisible(true);
        pin(caster);
        wreath(level, caster, target);
    }

    /** Keeps the two bodies on the same spot for as long as the hold lasts. */
    private static void maintain(ServerLevel level, Player caster, Mob target, int ticksChanneled) {
        target.setNoAi(true);
        target.moveTo(caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), caster.getXRot());
        target.setDeltaMovement(0.0d, 0.0d, 0.0d);
        target.fallDistance = 0.0f;
        pin(caster);

        if (ticksChanneled % PARTICLE_INTERVAL == 0) {
            wreath(level, caster, target);
        }
    }

    /** A body with nobody in it does not walk, and does not look like it is standing on its own. */
    private static void pin(Player caster) {
        caster.setDeltaMovement(0.0d, 0.0d, 0.0d);
        caster.fallDistance = 0.0f;
        caster.setInvisible(true);
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BODY_SLOWNESS_TICKS,
                BODY_SLOWNESS_AMPLIFIER, false, false));
    }

    /** The pale light of a mind that is not in its own body. */
    private static void wreath(ServerLevel level, Player caster, Mob target) {
        level.sendParticles(ParticleTypes.WITCH, caster.getX(), caster.getY() + 1.2d, caster.getZ(),
                PARTICLE_COUNT, 0.35d, 0.7d, 0.35d, 0.01d);
        level.sendParticles(ParticleTypes.WITCH, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                PARTICLE_COUNT, 0.35d, 0.5d, 0.35d, 0.01d);
    }

    /** The creature whose spot the caster's mind is standing in, if it is still there and still alive. */
    @Nullable
    private static Mob taken(Player caster, ServerLevel level) {
        UUID id = caster.getPersistentData().getUUID(MOB_TAG);
        if (id.equals(NO_ENTITY)) {
            return null;
        }
        Entity entity = level.getEntity(id);
        if (entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()) {
            return mob;
        }
        return null;
    }

    /** Gives a creature back the mind it had, but only if the transfer is what took it. */
    public static void releaseMob(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.getUUID(OWNER_TAG).equals(NO_ENTITY)) {
            return;
        }
        if (data.getBoolean(HAD_AI_TAG)) {
            mob.setNoAi(false);
        }
        data.remove(OWNER_TAG);
        data.remove(UNTIL_TAG);
        data.remove(HAD_AI_TAG);
    }

    /** The same, for a creature found by id rather than in hand. */
    private static void giveBack(ServerLevel level, UUID mobId) {
        Entity entity = level.getEntity(mobId);
        if (entity instanceof Mob mob) {
            releaseMob(mob);
        }
    }

    /** Returns the caster to the spot the body was left on, changing world if it has to. */
    private static void goHome(Player caster, CompoundTag home) {
        if (!home.contains("dimension")) {
            return;
        }
        double x = home.getDouble("x");
        double y = home.getDouble("y");
        double z = home.getDouble("z");
        ServerLevel destination = levelOf(caster, home.getString("dimension"));
        if (destination == null) {
            return;
        }
        if (destination == caster.level()) {
            caster.teleportTo(x, y, z);
            return;
        }
        if (caster instanceof ServerPlayer player) {
            player.teleportTo(destination, x, y, z, player.getYRot(), player.getXRot());
        }
    }

    /** @return the saved level of a hold, or null if that world is gone. */
    @Nullable
    private static ServerLevel levelOf(Entity entity, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        MinecraftServer server = entity.getServer();
        if (id == null || server == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** @return the creature the caster may settle into, or null when nothing they own is in sight. */
    @Nullable
    private Mob lookedAtMob(Player player, INinjaData ninjaData) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mind_transfer.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        if (!mob.isAlive() || mob.isSpectator() || mob.isRemoved()) {
            return null;
        }
        if (mob.isAlliedTo(player) || Friends.areFriends(player, mob)) {
            return null;
        }
        if (mob.getMaxHealth() >= TOO_STRONG_HEALTH) {
            player.displayClientMessage(Component.translatable("jutsu.fail.mind_transfer.toostrong",
                    Component.translatable(mob.getType().getDescriptionId()).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return null;
        }
        return mob;
    }
}
