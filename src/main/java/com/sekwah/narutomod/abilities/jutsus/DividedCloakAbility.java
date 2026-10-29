package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The vessel's chakra pulled out thin and shared: a cloak that covers the caster's side rather than
 * the caster alone.
 *
 * <p>It does very little for one body - two points of armour, one point of force and a little speed -
 * and that is the point. What it is for is a squad: everybody standing close enough to be under the
 * caster's shadow is under the same cloak, and the chakra of a sealed beast is enough to keep a line
 * of ninja standing. The threads are visible from the caster's hand to each body they reach, so it is
 * always clear who is being carried and who has walked out of reach.</p>
 *
 * <p>It is the cheapest form of the branch: nothing in it spends the vessel beyond the wear that time
 * puts on any vessel, so it is the one form a caster can keep up through a long fight.</p>
 */
public class DividedCloakAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A cloak makes no more noise than a coat. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B B A B - the seal, then one more key for the sharing. */
    public static final long COMBO = 21131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "divided_cloak");

    @Override
    protected float sageModeCostMultiplier() {
        return 0.5f;
    }

    /** The mark that says the cloak is up, refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:divided_cloak_until";

    /** Stable ids, so the two modifiers are re-applied rather than stacked. */
    private static final UUID ARMOUR_MODIFIER_ID = UUID.fromString("6b1d3e52-0a94-4c77-b2f8-51ce9d0a7f33");
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("a09c7f14-2e58-4b31-9d06-3f7b81c25e10");

    /** Two points of armour - a piece's worth - and one point of bare-handed force. */
    private static final double ARMOUR_BONUS = 4.0d;
    private static final double DAMAGE_BONUS = 2.0d;

    /** How far the cloak reaches: close enough to be a fighting line, not a field. */
    private static final double SHARE_RANGE = 6.0d;

    /** How long a share lasts. Renewed every tick, so this is only how long it lingers after it ends. */
    private static final int EFFECT_DURATION = 60;

    /** Chakra paid to pull the cloak open, before mastery, and every tick it is worn. */
    private static final float OPEN_COST = 20.0f;
    private static final float UPKEEP = 0.2f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept while the cloak is worn. */
    private static final int MARK_GRACE = 10;

    /** How often the threads are redrawn, and how finely each one is dotted. */
    private static final int THREAD_INTERVAL = 4;
    private static final int MAX_THREAD_POINTS = 8;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * Refuses the switch-on before the stance joins the set when there is no vessel to share.
     * Without this the cloak flicks on and straight back off on the next upkeep tick.
     */
    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        if (SealVesselAbility.integrity(player) <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (SealVesselAbility.integrity(player) <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.seal_vessel.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= player.level().getGameTime();
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
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
        // A stance is mastered by the time it is worn, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        long now = player.level().getGameTime();
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;
        applyModifiers(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false));
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (opening) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    30, 0.6d, 0.7d, 0.6d, 0.02d);
            level.playSound(null, player, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else if (player.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.8d, player.getZ(),
                    2, 0.35d, 0.5d, 0.35d, 0.005d);
        }
        share(level, player);
    }

    /**
     * Hands the cloak to everybody on the caster's side within reach. The gifts are given as short
     * effects rather than attribute modifiers: an effect expires on its own, so an ally who walks out
     * of range or a caster who dies does not leave a body carrying somebody else's chakra for ever.
     */
    private static void share(ServerLevel level, Player caster) {
        List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(SHARE_RANGE),
                target -> target != caster && target.isAlive() && !target.isSpectator()
                        && Friends.isOwnSide(caster, target));
        boolean drawThreads = caster.tickCount % THREAD_INTERVAL == 0;
        for (LivingEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, EFFECT_DURATION, 0, true, false));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 0, true, false));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0, true, false));
            if (drawThreads) {
                drawThread(level, caster, ally);
            }
        }
    }

    /** A thin thread of soul-motes from the caster's hand to the body it is carrying. */
    private static void drawThread(ServerLevel level, Player caster, LivingEntity ally) {
        Vec3 hand = JutsuEffects.handPosition(caster);
        Vec3 target = ally.position().add(0.0d, ally.getBbHeight() * 0.5d, 0.0d);
        Vec3 span = target.subtract(hand);
        int points = Math.max(2, Math.min(MAX_THREAD_POINTS, (int) (span.length() * 1.5d)));
        for (int i = 1; i <= points; i++) {
            Vec3 at = hand.add(span.scale(i / (double) (points + 1)));
            level.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applyModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null && armour.getModifier(ARMOUR_MODIFIER_ID) == null) {
            armour.addTransientModifier(new AttributeModifier(ARMOUR_MODIFIER_ID, "narutomod:divided_cloak",
                    ARMOUR_BONUS, AttributeModifier.Operation.ADDITION));
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(DAMAGE_MODIFIER_ID) == null) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID,
                    "narutomod:divided_cloak_damage", DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Takes the cloak off the caster's attributes and drops its mark and its speed with it. */
    public static void clear(Player player) {
        player.getPersistentData().remove(UNTIL_TAG);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(ARMOUR_MODIFIER_ID);
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }

    /** @return whether the cloak is currently worn, asked by anything that leans on the stance. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** A stance is mastered by the time it is up, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The effects and attributes are synced from the server, so there is nothing to do here.
    }

    /** Dropping the cloak takes its mark and its gifts with it; the allies' shares expire on their own. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        clear(player);
    }
}
