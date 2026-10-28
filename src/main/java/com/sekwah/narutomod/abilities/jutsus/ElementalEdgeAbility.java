package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.Nature;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import java.util.UUID;

/**
 * The ninja's own element carried into the steel.
 *
 * <p>A sword cuts, and that is all it does; a ninja who was born to fire should not have to choose
 * between the blade and the element they understand best. Held up, the steel takes on the nature of
 * its owner: every blow carries a little of it past the plate, and whatever is struck is left in the
 * state that element makes - burning, soaked through, or dried out by the wind.</p>
 *
 * <p>The element is read from the caster every blow rather than fixed at the cast, so a ninja who
 * changes their nature keeps the stance and it follows them. Earth and wood have no state to leave
 * that the body does not already have, so those two cut with the chakra alone.</p>
 */
public class ElementalEdgeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded, Ability.Retoggle {

    /** The element on the blade is quiet; the blow that carries it is not part of this technique. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 5 2 in the brief; 1 3 2 3 1 2 1 here, because a combo has only three keys. */
    public static final long COMBO = 231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "elemental_edge");

    /** The mark the melee hook reads: kept a few ticks ahead while the stance is held. */
    public static final String UNTIL_TAG = "narutomod:elemental_edge_until";

    /** What the element adds to a blow, taken past the armour rather than into it. */
    public static final float ELEMENT_DAMAGE = 2.0f;

    /** How long the element's state is left on a body that has been struck. */
    private static final int BURN_SECONDS = 3;

    private static final UUID LIGHTNING_SPEED_ID = UUID.fromString("a7c3e1f2-9b4d-4a6e-8f1c-2d5b7e9a3c6f");
    private static final double LIGHTNING_SPEED_BONUS = 0.05d;

    /** Chakra paid for every tick the stance is up. */
    private static final float COST_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the stance drops still carry the element.
     */
    private static final int MARK_GRACE = 5;

    /** How often the blade is drawn shimmering, and how many motes. */
    private static final int PARTICLE_INTERVAL = 5;
    private static final int PARTICLE_COUNT = 2;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        // Sneaking while casting lets the caster pick which element the blade carries, instead of
        // being stuck with whatever the chakra paper once said (or nothing at all).
        if (player.isShiftKeyDown()) {
            Nature current = Nature.byId(ninjaData.getNatureAffinity());
            Nature next;
            if (current == null) {
                next = Nature.values()[0];
            } else {
                next = Nature.values()[(current.ordinal() + 1) % Nature.values().length];
            }
            ninjaData.setNatureAffinity(next.getSerializedName());
            player.displayClientMessage(Component.translatable("jutsu.elemental_edge.nature_set",
                    Component.translatable(next.translationKey()).withStyle(ChatFormatting.AQUA)), false);
        }
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.elemental_edge.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Nature.byId(ninjaData.getNatureAffinity()) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.elemental_edge.noaffinity",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }
    @Override
    public boolean onRetoggle(Player player, INinjaData ninjaData) {
        if (!player.isShiftKeyDown()) {
            return false;
        }
        Nature current = Nature.byId(ninjaData.getNatureAffinity());
        Nature next = current == null ? Nature.values()[0]
                : Nature.values()[(current.ordinal() + 1) % Nature.values().length];
        ninjaData.setNatureAffinity(next.getSerializedName());
        player.displayClientMessage(Component.translatable("jutsu.elemental_edge.nature_set",
                Component.translatable(next.translationKey()).withStyle(ChatFormatting.AQUA)), true);
        return true;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            return false;
        }
        if (Nature.byId(ninjaData.getNatureAffinity()) == null) {
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
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        // While the lightning edge is up, the caster moves faster.
        Nature nature = Nature.byId(ninjaData.getNatureAffinity());
        applyLightningSpeed(player, nature == Nature.WOOD);

        if (player.level() instanceof ServerLevel level && player.tickCount % PARTICLE_INTERVAL == 0) {
            if (nature != null) {
                Vec3 hand = JutsuEffects.handPosition(player);
                level.sendParticles(particleFor(nature), hand.x, hand.y, hand.z,
                        PARTICLE_COUNT, 0.15d, 0.2d, 0.15d, 0.01d);
            }
        }
    }

    /**
     * One blow carrying the caster's element: damage taken through the armour, and the state the
     * element leaves behind.
     */
    public static void onMeleeHit(Player caster, LivingEntity victim) {
        INinjaData data = caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        Nature nature = data == null ? null : Nature.byId(data.getNatureAffinity());
        if (nature == null) {
            return;
        }
        // The element is a separate hit of its own, so the invulnerability window the steel just
        // opened is cleared rather than letting the element go missing.
        victim.invulnerableTime = 0;
        victim.hurt(caster.damageSources().magic(), ELEMENT_DAMAGE);

        switch (nature) {
            case FIRE -> victim.setSecondsOnFire(BURN_SECONDS);
            case WATER -> {
                ElementalStates.refreshWet(victim, ElementalStates.WET_TICKS);
                ElementalStates.setChilled(victim, ElementalStates.CHILLED_TICKS);
            }
            case WIND -> {
                ElementalStates.dry(victim);
                Vec3 push = victim.position().subtract(caster.position());
                if (push.lengthSqr() > 1.0e-4) {
                    push = push.normalize().scale(0.9d);
                    victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, 0.25d, push.z));
                    victim.hurtMarked = true;
                }
            }
            case EARTH -> victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
            case WOOD -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
                if (victim instanceof Player victimPlayer) {
                    victimPlayer.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(victimData -> {
                        victimData.useChakra(5.0f, 20);
                    });
                }
                if (victim.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                            12, 0.3d, 0.3d, 0.3d, 0.05d);
                }
            }
        }
    }

    private static net.minecraft.core.particles.SimpleParticleType particleFor(Nature nature) {
        return switch (nature) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case WIND -> ParticleTypes.CLOUD;
            default -> ParticleTypes.CRIT;
        };
    }

    /** @return whether the stance is up right now, asked by the melee hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** The stance is mastered by holding it, so the tick credit is paid from {@link #performServer}. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The element is read on the server and drawn from there.
    }

    private static void applyLightningSpeed(Player player, boolean on) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(LIGHTNING_SPEED_ID);
        if (on) {
            if (existing == null) {
                instance.addTransientModifier(new AttributeModifier(LIGHTNING_SPEED_ID,
                        "narutomod.elemental_edge.lightning_speed", LIGHTNING_SPEED_BONUS,
                        AttributeModifier.Operation.ADDITION));
            }
        } else if (existing != null) {
            instance.removeModifier(LIGHTNING_SPEED_ID);
        }
    }

    /** Sheathing the blade takes the element out of it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        applyLightningSpeed(player, false);
    }
}
