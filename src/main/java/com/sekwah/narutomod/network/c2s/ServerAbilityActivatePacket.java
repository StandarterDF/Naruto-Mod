package com.sekwah.narutomod.network.c2s;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.ModeBus;
import com.sekwah.narutomod.abilities.jutsus.AbstractDojutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
import com.sekwah.narutomod.events.ByakuganEvents;
import com.sekwah.narutomod.events.GenjutsuEvents;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.function.Supplier;

/**
 * Tell the server that the user wants to cast a specific ability.
 */
public class ServerAbilityActivatePacket {

    private final int abilityId;

    public ServerAbilityActivatePacket(ResourceLocation ability) {
        this.abilityId = NarutoRegistries.ABILITIES.getID(ability);
    }

    public ServerAbilityActivatePacket(int abilityId) {
        this.abilityId = abilityId;
    }

    public static void encode(ServerAbilityActivatePacket msg, FriendlyByteBuf outBuffer) {
        outBuffer.writeInt(msg.abilityId);
    }

    public static ServerAbilityActivatePacket decode(FriendlyByteBuf inBuffer) {
        int abilityId = inBuffer.readInt();
        return new ServerAbilityActivatePacket(abilityId);
    }

    /**
     * @return whether this cast only stops a stance the caster already has running, which is always
     *         allowed so a seal can never trap a body in an upkeep.
     */
    private static boolean closesRunningToggle(INinjaData ninjaData, Ability ability) {
        if (ability.activationType() != Ability.ActivationType.TOGGLE) {
            return false;
        }
        ResourceLocation id = NarutoRegistries.ABILITIES.getKey(ability);
        return id != null && ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(id);
    }

    public static class Handler {
        public static void handle(ServerAbilityActivatePacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                final ServerPlayer player = ctx.get().getSender();
                if(player == null || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    return;
                }
                player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                    if(!ninjaData.isNinjaModeEnabled()) {
                        player.displayClientMessage(Component.translatable("jutsu.not_a_ninja").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    Ability ability = NarutoRegistries.ABILITIES.getValue(msg.abilityId);
                    if (ability == null) {
                        return;
                    }
                    // A mind held by an illusion cannot draw on its chakra, except to break the illusion.
                    if (GenjutsuEvents.isUnderGenjutsu(player)
                            && !"KaiAbility".equals(ability.getClass().getSimpleName())) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.genjutsu.held").withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    ResourceLocation abilityId = NarutoRegistries.ABILITIES.getResourceKey(ability)
                            .map(ResourceKey::location).orElse(null);
                    if (!AbilityUnlocks.isUnlocked(ninjaData, abilityId)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.notunlocked",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // Switched off in the jutsu menu: the row is grey there and the packet is skipped,
                    // but the server repeats the check so a hacked client cannot cast it anyway.
                    if (ninjaData.isAbilityDisabled(abilityId)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.techdisabled",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // A Byakugan palm has closed the caster's channels: no technique may start.
                    // Closing an eye that is already burning is still allowed, or a sealed caster
                    // would be stuck paying an upkeep they cannot switch off.
                    if (ByakuganEvents.isSealed(player) && !closesRunningToggle(ninjaData, ability)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.tenketsu")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    // The chakra cloak leaves no room in the body for another technique. Switching
                    // the cloak off (or pulling the tailed cloak over it) is still allowed, or the
                    // caster would be trapped inside their own shell.
                    if (!ChakraCloakAbility.leavesRoomFor(player, ability) && !closesRunningToggle(ninjaData, ability)) {
                        player.displayClientMessage(Component.translatable("jutsu.fail.chakra_cloak.locked",
                                Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }
                    if (ability.activationType() == Ability.ActivationType.INSTANT) {

                        boolean canTriggerJutsu = true;
                        if (ability  instanceof Ability.Cooldown) {
                            canTriggerJutsu = !((Ability.Cooldown) ability).checkCooldown(player, ninjaData, ability.getTranslationKey(ninjaData));
                        }

                        if(canTriggerJutsu && ability.handleCost(player, ninjaData)) {
                            if (ability.logInChat()) {
                                player.displayClientMessage(Component.translatable("jutsu.cast", Component.translatable(ability.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN), true);
                            }
                            if(ability.castingSound() != null) {
                                player.level().playSound(null,
                                        player, ability.castingSound(), SoundSource.PLAYERS, 0.5f, 1.0f);
                            }
                            ability.emitNoise(player);
                            ability.performServer(player, ninjaData);
                            ability.recordMastery(player, ninjaData);

                            if (ability  instanceof Ability.Cooldown) {
                               ((Ability.Cooldown) ability).registerCooldown(player, ninjaData, ability.getTranslationKey(ninjaData));
                            }
                        } else if(ability.castingFailSound() != null) {
                            player.playNotifySound( ability.castingFailSound(), SoundSource.PLAYERS, 0.5f, 1.0f);
                        }
                    } else if(ability.activationType() == Ability.ActivationType.TOGGLE) {
                        ToggleAbilityData abilityTracker = ninjaData.getToggleAbilityData();
                        HashSet<ResourceLocation> abilities = abilityTracker.getAbilitiesHashSet();
                        NarutoRegistries.ABILITIES.getResourceKey(ability).ifPresent(abilityResourceKey -> {
                            var location = abilityResourceKey.location();
                            if(abilities.contains(location)) {
                                // A stage-based toggle may consume the re-press (the curse seal
                                // advances instead of closing) and stay in the set.
                                if (ability instanceof Ability.Retoggle retoggle
                                        && retoggle.onRetoggle(player, ninjaData)) {
                                    return;
                                }
                                // Toggle ability off
                                abilityTracker.removeAbilityEnded(player, ninjaData, ability);
                            } else {
                                // Toggle ability on. An eye takes the place of the one already
                                // burning in this same step; letting both sit in the set for a tick
                                // made their upkeeps close each other, sometimes leaving neither.
                                // A stance with an entry price (the Six Paths ritual, the cloaks
                                // built on another stance or on the vessel) is refused here, before
                                // anything else is touched: refusing after closing the other modes
                                // would leave the caster with neither the old stance nor the new one.
                                if (!ability.canStartToggle(player, ninjaData)) {
                                    return;
                                }
                                if (ability instanceof AbstractDojutsuAbility eye) {
                                    eye.switchFrom(player, ninjaData);
                                }
                                // One major mode at a time: the body cannot wear two full transformations.
                                ModeBus.closeOthers(player, ninjaData, abilityId);
                                abilityTracker.addAbilityStarted(player, ninjaData, ability);
                                ability.recordMastery(player, ninjaData);
                            }
                        });
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
