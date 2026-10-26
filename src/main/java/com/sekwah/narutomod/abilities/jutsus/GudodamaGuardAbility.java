package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.List;

/**
 * The Truth-Seeking Orbs held as a shield.
 *
 * <p>An orb in orbit is not a wall; it is a small piece of nothing that eats what it meets. Anything
 * thrown at the caster inside three blocks is swallowed the moment it arrives, and the orb that
 * swallowed it is spent. That is the whole trade of the stance: it is perfect against a volley and
 * cannot be held longer than the orbs last, so it is a decision about when to open the guard rather
 * than something to leave running.</p>
 *
 * <p>When the orbs run out the mode closes on its own (see {@link SixPathsModeAbility}); the guard
 * stance is then refused by its own upkeep, because it cannot stand on nothing.</p>
 */
public class GudodamaGuardAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A ring of black spheres does not announce itself; the shots it eats do. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C C C B C, one key on from the throw. */
    public static final long COMBO = 333313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "gudodama_guard");

    /** Chakra paid per tick to keep the ring turning. */
    private static final float COST_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** The bubble the orbs sweep. */
    private static final double RADIUS = 3.0d;

    /** How often the sweep runs. A shot that arrives between sweeps is already inside the caster. */
    private static final int SWEEP_INTERVAL = 5;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!SixPathsModeAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.six_paths_mode.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
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
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    8, RADIUS * 0.5d, 0.55d, RADIUS * 0.5d, 0.02d);
        }
        if (player.tickCount % SWEEP_INTERVAL == 0) {
            swallow(level, player);
        }
    }

    /**
     * Eats the shots inside the ring, one orb per shot, stopping when the orbs do. A volley larger
     * than the remaining stock is only partly stopped - the guard is a stock of nothing, and it has
     * to run out honestly.
     */
    private static void swallow(ServerLevel level, Player player) {
        int orbs = SixPathsModeAbility.orbs(player);
        if (orbs <= 0) {
            return;
        }
        List<Projectile> incoming = level.getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(RADIUS),
                projectile -> projectile.isAlive() && projectile.getOwner() != player);
        for (Projectile projectile : incoming) {
            if (orbs <= 0) {
                break;
            }
            if (projectile.distanceToSqr(player) > RADIUS * RADIUS) {
                continue;
            }
            level.sendParticles(ParticleTypes.SQUID_INK, projectile.getX(), projectile.getY(),
                    projectile.getZ(), 8, 0.12d, 0.12d, 0.12d, 0.02d);
            projectile.discard();
            orbs--;
        }
        SixPathsModeAbility.setOrbs(player, orbs);
    }

    /** The guard is mastered by the time it is held, so the tick credit is paid from the upkeep. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The ring is drawn by the server; there is nothing to hold on the client.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing was stored on the caster: the ring and the stock belong to the mode.
    }
}
