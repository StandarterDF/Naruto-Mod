package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Susanoo: the ribcage of a god, worn as a stance.
 *
 * <p>The Mangekyo's answer to being hit. While the eye holds it the caster is wrapped in the first,
 * bare stage of the construct - nothing but ribs of chakra and a will in front of them - and blows
 * land on that instead of on the body. It is a guard, not a weapon: this stage has no arms, takes no
 * step and strikes nothing, which is what keeps it a decision to stand in one place rather than a
 * second way to fight.</p>
 *
 * <p>It is priced in two parts. Opening the ribcage costs a deep breath of chakra, and every tick
 * after that costs a trickle, so it can be held through a fight but not through a war. On top of that
 * it is paid for in the eye itself: {@link #MAX_TICKS} of unbroken use burns the eye out, and the
 * stance closes the Sharingan for the caster rather than letting them decide to keep going. The
 * damage the ribs soak up is enforced in {@code SharinganAdvancedEvents}, which reads the mark left
 * here, in the same place the rest of the eye's consequences live.</p>
 */
public class SusanooAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A cage of chakra this size is felt before it is seen. */
    @Override
    public int noiseRadius() {
        return 16;
    }

    /** C V V B V C C - the Sharingan (C V V B), then the heavy end of the Mangekyo branch. */
    public static final long COMBO = 111122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "susanoo");

    /**
     * Game time at which the ribs stop catching blows. Refreshed every active tick and read by
     * {@code SharinganAdvancedEvents}; the two ticks of slack mean a missed tick does not drop the
     * guard for a moment.
     */
    public static final String UNTIL_TAG = "narutomod:susanoo_until";

    /** Game time the ribcage was opened, which is what the burnout is counted from. */
    public static final String SINCE_TAG = "narutomod:susanoo_since";

    /** What it costs to raise the ribs, and what they cost for every tick they stand. */
    private static final float OPENING_COST = 60.0f;
    private static final float CHAKRA_PER_TICK = 0.6f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the ribs may be held before the eye that shapes them gives out. */
    private static final int MAX_TICKS = 600;

    /** What the burnt eye pays afterwards: ten seconds of seeing nothing at all. */
    private static final int BURNOUT_BLIND_TICKS = 200;

    /**
     * How long each gift lasts. It is renewed every tick, so this is only how long it lingers after
     * the stance ends - long enough not to drop the guard between two hits, short enough that a
     * dropped stance is not still protecting the caster five seconds later.
     */
    private static final int EFFECT_DURATION = 40;

    /** The ribs as a stack of rings: how many bands, and how far apart they sit. */
    private static final int RIB_BANDS = 5;
    private static final double RIB_SPACING = 0.22d;

    /** The waist of the construct, and how much the bands above and below it close in. */
    private static final double RIB_RADIUS = 0.95d;
    private static final double RIB_TAPER = 0.12d;

    /** Where the lowest band sits, as a share of the caster's height. */
    private static final double RIB_BASE = 0.3d;

    /** One band is drawn every this many ticks, cycling, so the whole cage reads without a flood. */
    private static final int BAND_INTERVAL = 3;

    /** How many points of flame go into one band of the ribs. */
    private static final int BAND_POINTS = 6;

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
        if (!NarutoConfig.sharinganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SharinganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sharingan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // A stance is paid for on the first tick it is up, before anything has been written. After
        // that the mark from the previous tick is still fresh, which is what tells the two apart.
        CompoundTag data = player.getPersistentData();
        boolean opening = !data.contains(SINCE_TAG)
                || data.getLong(UNTIL_TAG) < player.level().getGameTime();
        float cost = this.masteryCost(ninjaData, opening ? OPENING_COST : CHAKRA_PER_TICK);
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
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();

        if (!data.contains(SINCE_TAG)) {
            data.putLong(SINCE_TAG, now);
            level.playSound(null, player, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.9f, 0.6f);
        }
        data.putLong(UNTIL_TAG, now + 2L);

        // Mastery is earned by standing in the ribs, not by flicking them up.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        // The other half of the guard: chakra standing in for armour. Short and refreshed, so the
        // two halves of the reduction go away together when the stance does.
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                EFFECT_DURATION, 1, true, false, false));

        this.drawRibs(level, player);

        if (now - data.getLong(SINCE_TAG) >= MAX_TICKS) {
            this.burnout(player, ninjaData);
        }
    }

    /**
     * One band of the ribcage, cycling around and up the caster's body so the stack reads as a cage
     * rather than as a torch. The particles are stale for a moment after they are sent, which is what
     * makes one band a tick enough to see the whole thing.
     */
    private void drawRibs(ServerLevel level, Player player) {
        if (player.tickCount % BAND_INTERVAL != 0) {
            return;
        }
        int band = (player.tickCount / BAND_INTERVAL) % RIB_BANDS;
        double y = player.getY() + player.getBbHeight() * RIB_BASE + band * RIB_SPACING;
        // The waist is the middle of the stack and the widest band of it.
        double radius = RIB_RADIUS * (1.0d - Math.abs(band - (RIB_BANDS - 1) / 2.0d) * RIB_TAPER);
        double spin = player.tickCount * 0.05d;
        for (int point = 0; point < BAND_POINTS; point++) {
            double angle = spin + point * (Math.PI * 2.0d / BAND_POINTS);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX() + Math.cos(angle) * radius, y, player.getZ() + Math.sin(angle) * radius,
                    1, 0.01d, 0.02d, 0.01d, 0.0d);
        }
    }

    /**
     * The eye that holds the ribs gives out: the construct goes, the caster is left blind and the
     * Sharingan is shut for them. The stance is ended here rather than left for the next upkeep to
     * discover, so the mark that soaks damage is gone in the same tick the eye closes.
     */
    private void burnout(Player player, INinjaData ninjaData) {
        CompoundTag data = player.getPersistentData();
        data.remove(SINCE_TAG);
        data.remove(UNTIL_TAG);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                BURNOUT_BLIND_TICKS, 0, false, true));
        player.displayClientMessage(Component.translatable("jutsu.susanoo.burnout")
                .withStyle(ChatFormatting.RED), true);
        closeEye(player, ninjaData, SharinganAbility.ID);
        ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, this);
    }

    /** Shuts one stance on the caster's own data, whichever system asked for it. */
    private static void closeEye(Player player, INinjaData ninjaData, ResourceLocation abilityId) {
        if (!ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(abilityId)) {
            return;
        }
        Ability ability = NarutoRegistries.ABILITIES.getValue(abilityId);
        if (ability != null) {
            ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, ability);
        }
    }

    /** @return whether the caster is standing inside the ribcage right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * A stance is mastered by the time it is held, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The ribs are drawn from the server and the guard is read from the mark it leaves.
    }

    /**
     * The ribs are gone, so nothing may keep catching blows for a caster who is no longer inside
     * them. The resistance was given a short duration and expires on its own, which leaves a potion
     * the caster drank themselves alone.
     */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        CompoundTag data = player.getPersistentData();
        data.remove(SINCE_TAG);
        data.remove(UNTIL_TAG);
    }
}
