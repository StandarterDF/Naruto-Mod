package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.jutsus.BodyAdaptationAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraMeditationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraSensingAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.abilities.jutsus.EightGatesAbility;
import com.sekwah.narutomod.abilities.jutsus.HengeJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.SageModeAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.ranks.NinjaRanks;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.events.SkillTreeEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerEvents {

    /**
     * Stamina paid for every point of health a landing would otherwise have taken off.
     */
    private static final float SOFT_LANDING_STAMINA_PER_DAMAGE = 2.0f;

    private static final int SOFT_LANDING_STAMINA_REGEN_DELAY = 20 * 3;

    /** Chakra every meal in the {@code narutomod:chakra_food} tag gives on top of its own effects. */
    private static final float TAG_CHAKRA = 5.0f;

    /** The tag that marks any food as ninja rations, for datapacks as much as for the mod's own dishes. */
    public static final TagKey<Item> CHAKRA_FOOD = TagKey.create(Registries.ITEM,
            new ResourceLocation(NarutoMod.MOD_ID, "chakra_food"));

    @SubscribeEvent
    public static void onEntityUpdate(LivingEvent.LivingTickEvent event) {
        // Do chakra updates and other stuff here unless handled in capabilities
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        // Training weights slow down whoever wears them, see TrainingWeightsItem.
        com.sekwah.narutomod.item.TrainingWeightsItem.applySlowdown(player);
        // Two clones sat down to meditate keep their caster topped up and their jutsu coming back
        // twice as fast, see ChakraMeditationJutsuAbility.
        ChakraMeditationJutsuAbility.tickAura(player);
        // The open gates have to be fed and refreshed every tick they are held, see EightGatesAbility.
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            EightGatesAbility.maintain(player, ninjaData);
            // Sage mode is fed from the nature energy pool and closes itself when it runs dry, see
            // SageModeAbility.
            SageModeAbility.maintain(player, ninjaData);
            // Nature energy trickles back on its own, very slowly - but only for a caster who has
            // learned to gather it at all, see AbilityUnlocks#hasSenjutsu.
            if (ninjaData.isNinjaModeEnabled() && AbilityUnlocks.hasSenjutsu(ninjaData)) {
                ninjaData.addSeninMana(0.01f);
            }
            // A disguise is on a plain countdown, see HengeJutsuAbility.
            HengeJutsuAbility.maintain(player, ninjaData);
            // Night vision is fed from the chakra bar and closes itself when it runs dry, see
            // ChakraSensingAbility.
            ChakraSensingAbility.maintain(player, ninjaData);
            // Ranks are milestones worked out from skill nodes and boss kills, see NinjaRanks.
            if (player instanceof ServerPlayer serverPlayer) {
                NinjaRanks.tick(serverPlayer, ninjaData);
            }
        });
    }

    /**
     * A proper night's sleep is the one way to fill the pool for free, once a day.
     *
     * <p>Only a natural morning wake-up counts: being shaken awake by damage or waking in the middle
     * of the night gives nothing, so a bed cannot be pumped for chakra in the middle of a fight. On
     * the morning the body is rested, so both chakra and nature energy come back full - it is the
     * answer to "I have no clones to sit with and no pills left".</p>
     */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.wakeImmediately() || !player.level().isDay()
                || !NarutoConfig.sleepRestoresChakra) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isNinjaModeEnabled()) {
                return;
            }
            ninjaData.setChakra(ninjaData.getMaxChakra());
            ninjaData.setSeninMana(ninjaData.getMaxSeninMana());
        });
    }

    /**
     * Any food in the {@code narutomod:chakra_food} tag gives a small amount of chakra on the last
     * bite, on top of whatever the dish itself does.
     *
     * <p>The tag is the extension point: a datapack can list another mod's meal and it will feed a
     * ninja's chakra without any code. The mod's own dishes add their own larger amounts through
     * {@link com.sekwah.narutomod.item.ChakraFoodItem}, so the tag amount is deliberately modest.</p>
     *
     * <p>An adapted body also digests what a normal stomach would reject: rotten flesh no longer
     * leaves the caster hungry. The effect is applied by the item itself while the food is being
     * finished, which is before this event, so it is simply taken back off - the stance is what
     * makes the meal sit well, not a change to the item.</p>
     */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (event.getItem().is(Items.ROTTEN_FLESH)
                    && BodyAdaptationAbility.isActive(ninjaData)) {
                player.removeEffect(MobEffects.HUNGER);
            }
            if (event.getItem().is(CHAKRA_FOOD) && ninjaData.isNinjaModeEnabled()) {
                ninjaData.addChakra(TAG_CHAKRA);
            }
        });
    }

    /**
     * A ninja with nowhere to respawn and a chakra marker standing comes back at the marker.
     *
     * <p>Beds and respawn anchors always win: their position is set on the player before this
     * event fires, and the marker is only used when the player has no respawn point at all. The
     * marker is a log standing in the world (see {@code ChakraMarkerJutsuAbility}), so this is the
     * one respawn in the mod that is bought with a technique rather than a block.</p>
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()
                || !NarutoConfig.markerRespawnEnabled) {
            return;
        }
        if (player.getRespawnPosition() != null) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            Vec3 marker = ninjaData.getMarkerLoc();
            ResourceLocation dimension = ninjaData.getMarkerDimension();
            if (!ninjaData.isNinjaModeEnabled() || marker == null || dimension == null) {
                return;
            }
            ServerLevel target = player.getServer() == null ? null
                    : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (target == null) {
                return;
            }
            player.teleportTo(target, marker.x, marker.y, marker.z, player.getYRot(), player.getXRot());
            player.resetFallDistance();
        });
    }

    /**
     * Respawning builds a brand new player with no attribute modifiers on it, so anything written
     * onto the caster's attributes has to be put back by hand. The chakra shield is the only one at
     * the moment, and death takes it with it.
     *
     * <p>Runs last on purpose: the capability is copied onto the new player by another handler, and
     * reading the pool before that has happened would see an empty one.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (event.isWasDeath()) {
                ninjaData.setShieldPoints(0.0f);
                ninjaData.setSecondBreathArmed(false);
                // The gates are the one thing a caster cannot switch off, so death is what shuts them.
                EightGatesAbility.close(player, ninjaData);
                ChakraShieldAbility.removeShield(player);
                // Death puts the body back together, including out of the phasing stance.
                KamuiPhaseAbility.release(player, ninjaData);
                // Respawn with full (skill-boosted) health instead of the base pool.
                SkillTreeEvents.applyAll(player, ninjaData);
                player.setHealth(player.getMaxHealth());
            } else if (ninjaData.getShieldPoints() > 0.0f) {
                ChakraShieldAbility.applyShield(player, ninjaData.getShieldPoints());
            }
        });
    }

    /**
     * Puts the shield's armour modifier back after a relogin.
     *
     * <p>The pool itself is saved with the player data, but the modifier that shows it on the armour
     * bar is added as a <em>transient</em> modifier, and transient modifiers are not part of the
     * player's saved attributes. A caster who bought a shield, logged out and came back would keep
     * the points and lose the bar - and the damage would still be taken out of the pool, which reads
     * as damage vanishing into nothing. Death still clears the pool, so this only ever restores a
     * shield that was paid for and not yet broken.</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (ninjaData.getShieldPoints() > 0.0f) {
                ChakraShieldAbility.applyShield(player, ninjaData.getShieldPoints());
            }
        });
    }

    /**
     * The open gates do not let the body knit itself back together.
     *
     * <p>A well fed caster regenerates up to two points of health a second from their own saturation,
     * which is several times what the gates take from them, so without this the drain would be
     * quietly undone and the last gates would be a free buff. While any gate is open nothing heals
     * the caster at all: not food, not a regeneration potion, not the chakra healing technique.</p>
     */
    @SubscribeEvent
    public static void livingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (ninjaData.getGateCount() > 0) {
                event.setAmount(0.0f);
            }
        });
    }

    // Handle if they have some agility perk or leaps.
    //
    /**
     * Chakra no Ukemi: stamina spent so a landing is taken on the legs rather than the spine.
     *
     * <p>The price is the damage the ground was about to charge, doubled, so a short drop is a
     * couple of points and a lethal one is most of a full bar. There is nothing to cast, it simply
     * happens whenever it is affordable.</p>
     *
     * <p>Stamina is spent before health, point for point: if the bar covers the whole fall it is
     * cushioned, and if it covers only part of it that part is taken and the rest of the damage is
     * left for the caster to take normally. Only whole points of damage are bought, so a nearly
     * empty bar is not spent on a rounding error - a caster with two stamina left still keeps their
     * two stamina for a fall it could actually help with.</p>
     */
    @SubscribeEvent
    public static void livingFall(LivingFallEvent event) {
        // The event fires on both sides; only the server owns stamina, so a client-side pass would
        // only make the bar dip locally while the server still lands the damage.
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                if (!ninjaData.isNinjaModeEnabled()) {
                    return;
                }

                // A sage lands on nature itself: no damage and no stamina spent, just dust. The
                // distance is zeroed rather than cushioned, so the landing never reaches vanilla.
                if (ninjaData.isSageMode() && NarutoConfig.sageBuffsEnabled) {
                    event.setDistance(0.0f);
                    player.resetFallDistance();
                    if (player.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(),
                                player.getY() + 0.1d, player.getZ(),
                                20, 0.35d, 0.05d, 0.35d, 0.02d);
                    }
                    return;
                }

                // The vanilla sum, minus the jump boost that lifted the caster in the first place:
                // a fall that would not have hurt has nothing to cushion.
                MobEffectInstance jump = player.getEffect(MobEffects.JUMP);
                float jumpBoost = jump == null ? 0.0f : jump.getAmplifier() + 1;
                float damage = (float) Math.ceil(event.getDistance() - 3.0f - jumpBoost);
                if (damage <= 0.0f) {
                    return;
                }

                // The endurance branch makes a cushioned landing cheaper: the same point of damage
                // costs less stamina the more the caster has trained the roll.
                float discount = SkillTree.bonus(ninjaData.getSkillData().getUnlocked(),
                        SkillEffect.SOFT_LANDING);
                float staminaPerDamage = SOFT_LANDING_STAMINA_PER_DAMAGE
                        * Math.max(0.0f, 1.0f - discount);
                if (staminaPerDamage <= 0.0f) {
                    // A fully discounted landing is free; nothing to pay and nothing to leave over.
                    cushion(event, player, 0.0f, jumpBoost, damage);
                    return;
                }

                // Only whole points: a bar too thin for one point buys nothing, which is better than
                // charging the caster for a fraction of a heart they will not notice.
                int affordable = (int) Math.floor(ninjaData.getStamina() / staminaPerDamage);
                float absorbed = Math.min(damage, affordable);
                if (absorbed <= 0.0f) {
                    return;
                }

                ninjaData.useStamina(absorbed * staminaPerDamage, SOFT_LANDING_STAMINA_REGEN_DELAY);
                cushion(event, player, damage - absorbed, jumpBoost, damage);
            });
        }
    }

    /**
     * Cuts the fall down to the damage the stamina did not cover and plays the landing cloud. A
     * remainder of zero is a full cushion; anything above it is handed back to vanilla, which does
     * the usual damage on touchdown.
     */
    private static void cushion(LivingFallEvent event, Player player, float remaining, float jumpBoost,
                                float original) {
        if (remaining <= 0.0f) {
            event.setDistance(0.0f);
        } else {
            // Vanilla turns the distance back into ceil(distance - 3 - jumpBoost), so handing it
            // back the distance for the damage that is left makes exactly that much land.
            event.setDistance(remaining + 3.0f + jumpBoost);
        }
        player.resetFallDistance();
        if (player.level() instanceof ServerLevel serverLevel && remaining < original) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, player.getX(),
                    player.getY() + 0.1d, player.getZ(),
                    20, 0.35d, 0.05d, 0.35d, 0.02d);
        }
    }

}
