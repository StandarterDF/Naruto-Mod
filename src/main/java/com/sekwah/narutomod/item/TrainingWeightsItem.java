package com.sekwah.narutomod.item;

import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A pair of weighted bands worn on the wrists, or slung over the shoulders when they are not being
 * worn: the oldest training tool a taijutsu student is given and the last one they give up.
 *
 * <p>It is carried rather than equipped. A weight that sits in the pack while the ninja runs, climbs
 * and fights makes every step and every technique a little more expensive, and that is the training:
 * there is no slot to remember and nothing to take off before a technique, only a pack that is
 * heavier than it was. Two of them are the limit - a body can only be dragged down so far before the
 * training stops being training - and each one costs a twentieth of the ninja's speed and a tenth
 * again of their stamina.</p>
 *
 * <p>The body is stubborn, though, and it learns. The longer the weights are carried the less they
 * bite, until the drag is gone entirely and the training starts paying out instead: up to a quarter
 * more speed, a harder hand, a quicker pick, a deeper stamina pool and a faster recovery. The
 * weights go from being a handicap to being the thing that makes the caster stronger, which is the
 * whole point of wearing them in the first place.</p>
 *
 * <p>The item itself only answers questions about itself: whether this player is carrying one, how
 * many, by how much they are slowed and strained, and how far along the adaptation has come. The
 * slowdown, the strength and the speed are applied through {@link #applyEffects(Player)}, which keeps
 * a single attribute modifier up to date and removes it again once the weights are gone; the stamina
 * side is read through {@link #staminaMultiplier(Player)} and friends by whatever spends or regens
 * stamina. Neither is written to the save file - a transient attribute modifier and a value read
 * fresh every hit cannot outlive the weights in the pack, while the adaptation that earned them is
 * kept in the player's own data.</p>
 *
 * <p>Lore: a weight thrown out of the pack does not just fall. The chakra packed into it to keep
 * it heavy all those years lets go at once, and the band goes off like a small bomb. It breaks
 * nothing, but it shoves whatever is standing over it.</p>
 */
public class TrainingWeightsItem extends Item {

    /** A twentieth of the ninja's speed per weight carried, before the body learns. */
    public static final double SLOW_PER_WEIGHT = 0.05d;

    /** A tenth again of every stamina cost per weight carried. */
    public static final float STAMINA_EXTRA_PER_WEIGHT = 0.10f;

    /** How many weights the body can be dragged down by; the item stacks to exactly this. */
    public static final int MAX_WEIGHTS = 2;

    /** Ticks of carrying before the body is fully adapted: twenty minutes of wearing the load. */
    public static final int FULL_ADAPTATION_TICKS = 24000;

    /** Speed the fully adapted caster gains on top of their normal speed. */
    public static final double SPEED_BONUS_MAX = 0.25d;

    /** Flat attack damage a fully adapted caster gains. */
    public static final double DAMAGE_BONUS_MAX = 2.0d;

    /** Dig-speed effect amplifier at full adaptation (haste III). */
    public static final int DIG_SPEED_AMPLIFIER_MAX = 2;

    /** Stamina capacity a fully adapted caster gains. */
    public static final float STAMINA_CAPACITY_BONUS_MAX = 20.0f;

    /** Stamina regen per second a fully adapted caster gains. */
    public static final float STAMINA_REGEN_BONUS_MAX = 0.5f;

    /** Stable id, so the single speed modifier is updated rather than stacked. */
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("5f0c9a2e-3b71-4d68-9c14-8a2e7d5b0c93");

    /** Stable id for the strength the training leaves behind. */
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("7a3d2e1f-8b4c-4a91-9e2d-6c5b0f3a1d87");

    private static final String SPEED_MODIFIER_NAME = "narutomod:training_weights";

    private static final String ADAPTATION_TAG = "narutomod:training_weights_adaptation";
    private static final String PREV_CARRIED_TAG = "narutomod:training_weights_prev";

    public TrainingWeightsItem(Properties properties) {
        super(properties);
    }

    /**
     * @return how many weights this player is carrying, capped at {@link #MAX_WEIGHTS}; anything in
     *         the inventory counts, worn or not, because the item is carried rather than equipped
     */
    public static int carried(Player player) {
        Inventory inventory = player.getInventory();
        int carried = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof TrainingWeightsItem) {
                carried += stack.getCount();
                if (carried >= MAX_WEIGHTS) {
                    return MAX_WEIGHTS;
                }
            }
        }
        return carried;
    }

    /** @return whether this player is training under weight at all, asked before the stamina maths. */
    public static boolean isCarrying(Player player) {
        return carried(player) > 0;
    }

    /** @return how far the body has adapted to the load, from 0 to 1. */
    public static float adaptation(Player player) {
        int ticks = player.getPersistentData().getInt(ADAPTATION_TAG);
        if (ticks < 0) {
            ticks = 0;
        }
        if (ticks > FULL_ADAPTATION_TICKS) {
            ticks = FULL_ADAPTATION_TICKS;
        }
        return ticks / (float) FULL_ADAPTATION_TICKS;
    }

    /** Adds to the adaptation counter, clamped at the cap. Permanent: training is not forgotten. */
    public static void addAdaptation(Player player, int delta) {
        int ticks = player.getPersistentData().getInt(ADAPTATION_TAG);
        ticks = Math.max(0, Math.min(FULL_ADAPTATION_TICKS, ticks + delta));
        player.getPersistentData().putInt(ADAPTATION_TAG, ticks);
    }

    /** @return the player's speed multiplier, one when the pack is light and lower under the weights. */
    public static double movementMultiplier(Player player) {
        float adapt = adaptation(player);
        int c = carried(player);
        // The drag fades as the body learns; the bonus takes its place.
        double slow = SLOW_PER_WEIGHT * c * (1.0d - adapt);
        double bonus = SPEED_BONUS_MAX * adapt;
        return 1.0d - slow + bonus;
    }

    /** @return what a stamina cost has to be multiplied by, one when the pack is light. */
    public static float staminaMultiplier(Player player) {
        return 1.0f + STAMINA_EXTRA_PER_WEIGHT * carried(player);
    }

    /** @return extra stamina capacity the training grants, only while the weights are carried. */
    public static float staminaCapacityBonus(Player player) {
        if (!isCarrying(player)) {
            return 0.0f;
        }
        return STAMINA_CAPACITY_BONUS_MAX * adaptation(player);
    }

    /** @return extra stamina regen per second the training grants, only while carried. */
    public static float staminaRegenBonus(Player player) {
        if (!isCarrying(player)) {
            return 0.0f;
        }
        return STAMINA_REGEN_BONUS_MAX * adaptation(player);
    }

    /**
     * Per-tick maintenance: grows the adaptation while the weights are carried, notices when a weight
     * has been thrown out and lets it go off, and keeps the speed and strength modifiers in line.
     */
    public static void tick(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        int current = carried(player);
        int prev = player.getPersistentData().getInt(PREV_CARRIED_TAG);
        if (current < prev) {
            // A weight left the pack: let the packed chakra go.
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(),
                        player.getY() + 0.5d, player.getZ(), 1, 0.0d, 0.0d, 0.0d, 0.0d);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0f, 1.2f);
                for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(
                        net.minecraft.world.entity.LivingEntity.class,
                        player.getBoundingBox().inflate(2.5d),
                        t -> t != player && t.isAlive())) {
                    target.hurt(player.damageSources().magic(), 2.0f);
                    Vec3 push = target.position().subtract(player.position());
                    if (push.lengthSqr() < 0.0001d) {
                        push = new Vec3(0.0d, 0.0d, 1.0d);
                    }
                    push = push.normalize().scale(1.2d);
                    target.push(push.x, 0.4d, push.z);
                    target.hurtMarked = true;
                }
            }
        }
        player.getPersistentData().putInt(PREV_CARRIED_TAG, current);

        if (current > 0) {
            addAdaptation(player, 1);
        }
        applyEffects(player);
    }

    /**
     * Keeps the weight's drag (and, once the body has learned, its gifts) on the player's attributes
     * up to date. Idempotent and cheap: call it every server tick.
     */
    public static void applyEffects(Player player) {
        int carried = carried(player);
        float adapt = adaptation(player);

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double slow = SLOW_PER_WEIGHT * carried * (1.0d - adapt);
            double bonus = SPEED_BONUS_MAX * adapt;
            double amount = -slow + bonus;
            AttributeModifier existing = speed.getModifier(SPEED_MODIFIER_ID);
            if (carried <= 0 && adapt <= 0.0f) {
                if (existing != null) {
                    speed.removeModifier(SPEED_MODIFIER_ID);
                }
            } else {
                if (existing == null || Math.abs(existing.getAmount() - amount) > 1.0e-4d) {
                    if (existing != null) {
                        speed.removeModifier(SPEED_MODIFIER_ID);
                    }
                    if (Math.abs(amount) > 1.0e-4d) {
                        speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID,
                                SPEED_MODIFIER_NAME, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
                    }
                }
            }
        }

        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            double bonus = DAMAGE_BONUS_MAX * adapt;
            AttributeModifier existing = damage.getModifier(DAMAGE_MODIFIER_ID);
            if (adapt <= 0.0f) {
                if (existing != null) {
                    damage.removeModifier(DAMAGE_MODIFIER_ID);
                }
            } else {
                if (existing == null || Math.abs(existing.getAmount() - bonus) > 1.0e-4d) {
                    if (existing != null) {
                        damage.removeModifier(DAMAGE_MODIFIER_ID);
                    }
                    damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID,
                            "narutomod:training_weights_strength", bonus,
                            AttributeModifier.Operation.ADDITION));
                }
            }
        }

        // Dig speed is an effect rather than an attribute: refresh it every tick while it is earned.
        if (adapt > 0.0f && isCarrying(player)) {
            int amplifier = Math.min(DIG_SPEED_AMPLIFIER_MAX, (int) (DIG_SPEED_AMPLIFIER_MAX * adapt + 0.5f));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, amplifier, true, false));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        // The tooltip speaks for the stack in the hand: two weights read as the pair they are.
        int weights = Math.min(Math.max(stack.getCount(), 1), MAX_WEIGHTS);
        tooltip.add(Component.translatable("item.narutomod.training_weights.slow",
                percent(SLOW_PER_WEIGHT * weights)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.training_weights.stamina",
                percent(STAMINA_EXTRA_PER_WEIGHT * weights)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.training_weights.hint", MAX_WEIGHTS)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Turns a fraction into the whole percentage the tooltip shows, rounded rather than truncated. */
    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100.0d);
    }
}
