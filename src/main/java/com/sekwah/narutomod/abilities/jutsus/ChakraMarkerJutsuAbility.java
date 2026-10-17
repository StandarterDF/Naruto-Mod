package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ChakraMarkerEntity;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves one log behind for the caster to return to, and swaps places with it afterwards.
 *
 * <p>A ninja only ever has a single log planted: hold the technique to put one down (a new log
 * erases the old one, wherever that was) and tap it to swap. Swapping puts the caster where the log
 * stood and the log where the caster stood, so the two trade places rather than the log being spent
 * - one cast takes them home, the next takes them back to whatever they were doing.</p>
 *
 * <p>The log is bound to its caster's saved data rather than to the entity, so it survives logging
 * out and is still there after a restart. Its position is recorded as a coordinate, which is also
 * what makes the swap work when the log's chunk is not loaded. Cross dimension swaps are refused,
 * they would make the Nether a formality.</p>
 */
public class ChakraMarkerJutsuAbility extends Ability implements Ability.Channeled {

    /** C B - the substitution technique's neighbour in the combo list. */
    public static final long COMBO = 13L;

    /** Chakra spent leaving a log behind. */
    private static final float PLANT_COST = 25.0f;

    /** Chakra spent swapping places with the log. */
    private static final float SWAP_COST = 15.0f;

    private static final int CHAKRA_REGEN_DELAY = 20 * 3;

    /** How far from the recorded spot a log still counts as this caster's marker. */
    private static final double SEARCH_RADIUS = 2.0d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = chargeAmount == -1 ? SWAP_COST : PLANT_COST;
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        return true;
    }

    @Override
    public String getTranslationKey(INinjaData ninjaData, int ticksActive) {
        String key = super.getTranslationKey(ninjaData, ticksActive);
        return ticksActive <= 1 ? key : key + ".mark";
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (ticksActive == -1) {
            this.swapWithMarker(player, ninjaData);
        } else {
            this.plantMarker(player, ninjaData);
        }
    }

    /**
     * A quick tap: the log takes the caster's place and the caster takes the log's.
     */
    private void swapWithMarker(Player player, INinjaData ninjaData) {
        Vec3 marker = ninjaData.getMarkerLoc();
        ResourceLocation dimension = ninjaData.getMarkerDimension();

        if (marker == null || dimension == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.nomarker",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.level().dimension().location().equals(dimension)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.wrongdimension",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (ninjaData.getChakra() < SWAP_COST) {
            return;
        }

        ninjaData.addChakra(-SWAP_COST);
        Vec3 from = player.position();
        this.removeMarker(player, dimension, marker);
        this.spawnLog(player, from);
        player.teleportTo(marker.x, marker.y, marker.z);
        // The log stands where the caster stood, so the same trick brings them back here.
        ninjaData.setMarkerLoc(from, dimension);
        this.playEffect(player, marker);
        this.playEffect(player, from);
    }

    /**
     * A held cast: plants a fresh log underfoot and erases the previous one.
     */
    private void plantMarker(Player player, INinjaData ninjaData) {
        if (ninjaData.getChakra() < PLANT_COST) {
            return;
        }
        ninjaData.addChakra(-PLANT_COST);

        ResourceLocation dimension = player.level().dimension().location();
        Vec3 marker = ninjaData.getMarkerLoc();
        if (marker != null) {
            this.removeMarker(player, ninjaData.getMarkerDimension(), marker);
        }

        Vec3 position = player.position();
        this.spawnLog(player, position);
        ninjaData.setMarkerLoc(position, dimension);
        this.playEffect(player, position);
    }

    private void spawnLog(Player player, Vec3 position) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ChakraMarkerEntity marker = new ChakraMarkerEntity(level, player);
        marker.moveTo(position.x, position.y, position.z, player.getYRot(), 0.0f);
        level.addFreshEntity(marker);
    }

    /**
     * Erases the recorded log. The chunk it stands in has to be loaded for this to find it, which
     * it is whenever the caster is swapping with it and normally is when planting a new one too.
     */
    private void removeMarker(Player player, ResourceLocation dimension, Vec3 position) {
        ServerLevel level = this.markerLevel(player, dimension);
        if (level == null || position == null) {
            return;
        }
        for (ChakraMarkerEntity marker : level.getEntitiesOfClass(ChakraMarkerEntity.class,
                new AABB(position, position).inflate(SEARCH_RADIUS), entity -> entity.isOwnedBy(player))) {
            marker.discard();
        }
    }

    private ServerLevel markerLevel(Player player, ResourceLocation dimension) {
        if (dimension == null || !(player.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getServer() == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    private void playEffect(Player player, Vec3 position) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, position.x,
                    position.y + player.getBbHeight() / 2.0d, position.z,
                    40, 0.4d, 0.6d, 0.4d, 0.02d);
        }
        player.level().playSound(null, position.x, position.y, position.z,
                NarutoSounds.SHADOW_CLONE_POOF.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    @Override
    public boolean useChargedMessages() {
        return true;
    }

    @Override
    public boolean hideChannelMessages() {
        return false;
    }
}
