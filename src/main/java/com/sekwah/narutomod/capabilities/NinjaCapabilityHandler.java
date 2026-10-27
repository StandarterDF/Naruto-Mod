package com.sekwah.narutomod.capabilities;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.SenriganFlightAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganSightAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.item.ChakraBatteryItem;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public class NinjaCapabilityHandler {

    public static final Capability<INinjaData> NINJA_DATA = CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(NarutoMod.MOD_ID, "ninja_data"), new NinjaData(!event.getObject().level().isClientSide));
        }
    }

    @SubscribeEvent
    public static void onPlayerUpdate(TickEvent.PlayerTickEvent event) {
        if(event.phase.equals(TickEvent.Phase.END)) {
            Player player = event.player;
            player.getCapability(NINJA_DATA).ifPresent(data -> {
                if(event.side.isServer()) {
                    // Creative no longer refills the pool every tick: it regenerates ten times as
                    // fast instead (see NinjaData#updateDataServer), so a creative caster still has
                    // to pay for what they cast and gets the bar back quickly rather than instantly.
                    data.updateDataServer(player);
                    if(!player.isSpectator()) {
                        // Toggles are paid for tick by tick, so a battery in the ninja's inventory
                        // gets the chance to keep the stance up once their own chakra runs low. The
                        // channeled cast, if there is one, has already had the same chance inside
                        // updateDataServer, and a pool that is already full enough is left alone.
                        if(!data.getToggleAbilityData().getAbilitiesHashSet().isEmpty()) {
                            ChakraBatteryItem.fuelUpkeep(player, data);
                        }
                        // Walk a snapshot: an ability that can no longer pay is removed right here, and
                        // removing from the live set while walking it would throw
                        // ConcurrentModificationException. Anything that has gone missing from the
                        // registry is dropped from the set rather than crashing on a null.
                        for (ResourceLocation abilityName : new ArrayList<>(data.getToggleAbilityData().getAbilitiesHashSet())) {
                            Ability ability = NarutoRegistries.ABILITIES.getValue(abilityName);
                            if (ability == null) {
                                data.getToggleAbilityData().removeAbility(abilityName);
                                continue;
                            }
                            if(ability.handleCost(player, data)) {
                                ability.performServer(player, data);
                            } else {
                                // Silent: handleCost has already shown the reason on the action bar.
                                data.getToggleAbilityData().removeAbilityEndedSilently(player, data, ability);
                            }
                        }
                    }
                } else {
                    data.updateDataClient(player);
                    if(!player.isSpectator()) {
                        data.getToggleAbilityData().getAbilitiesHashSet().forEach(abilityName -> {
                            Ability ability = NarutoRegistries.ABILITIES.getValue(abilityName);
                            if(ability instanceof Ability.Toggled toggleAbility) toggleAbility.performToggleClient(player, data);
                        });
                    }
                }
            });
        }

    }

    @SubscribeEvent
    public static void playerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(NINJA_DATA).ifPresent(original -> {
            event.getEntity().getCapability(NINJA_DATA).ifPresent(future -> {
                future.deserializeNBT(original.serializeNBT());
                if (event.isWasDeath()) {
                    // Dying costs a slice of every technique's mastery, the deeper the closer it was
                    // to finished, and takes all of the caster's meditations with it.
                    Mastery.applyDeathPenalty(event.getEntity(), future);
                    dispelMeditationClones(event.getEntity(), original);
                    future.getMeditationData().clear();
                    future.setChakraCapacityBonus(0f);
                    // The Senrigan's borrowed flight must not be inherited by the new body: the
                    // toggle set is copied over, and without this a respawned caster would still be
                    // marked as flying.
                    SenriganFlightAbility.clearOnDeath(event.getEntity(), future);
                    SenriganSightAbility.clear(event.getEntity());
                }
            });
        });
    }

    /**
     * Pops whatever meditating clones of a dead caster can still be reached, so nothing keeps
     * feeding them from the grave. Their links go with them; the rest is cleared by the caller.
     */
    private static void dispelMeditationClones(Player caster, INinjaData original) {
        MinecraftServer server = caster.getServer();
        if (server == null) {
            return;
        }
        for (UUID cloneId : new ArrayList<>(original.getMeditationData().getLinks().keySet())) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity clone = level.getEntity(cloneId);
                if (clone instanceof ShadowCloneEntity shadowClone) {
                    shadowClone.dispel();
                    break;
                }
            }
        }
    }

    //public static final EntitySize STANDING_SIZE = EntitySize.flexible(0.1F, 0.1F);

    // For things like the transformation jutsus (make sure to trigger it manually when you change)
/*    @SubscribeEvent
    public static void playerSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            event.setNewEyeHeight(5f);
            event.setNewSize(EntityDimensions.scalable(10F, 10F));
        }
    }*/
}
