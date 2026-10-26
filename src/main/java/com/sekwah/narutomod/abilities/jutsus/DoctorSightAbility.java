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

/**
 * Doctor Sight: the Byakugan read as a healer reads a body.
 *
 * <p>Every other use of the eye in a fight asks what a body can do - where it is, what it carries,
 * which channels are open. A medic asks a different question, and the same eye answers it: how much
 * of this body is still there. While this stance is held every living thing nearby wears its own
 * condition - the health left in it and the effects running through it - as a small label over its
 * head, and every body inside the eight trigrams field gets a bar, so a healer can see who is worth
 * the chakra before spending it.</p>
 *
 * <p>Nothing is drawn from the server: the stance only keeps the mark in {@link #UNTIL_TAG} alive,
 * and the labels are put on the screen by {@code ByakuganReadOverlay} from the synced toggle set.
 * The faint pale chakra at the caster's eyes is the only thing the world sees of it, which is what a
 * reading stance should look like.</p>
 */
public class DoctorSightAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** Reading a body is silent. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V B C C - the eye's seal with the last key doubled, as a doctor looks twice. */
    public static final long COMBO = 232311L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "doctor_sight");

    /** The mark that says the read is running. Public so the client overlay reads it, not a copy. */
    public static final String UNTIL_TAG = "narutomod:doctor_sight_until";

    /** Chakra paid to start reading, and the trickle for every tick the read is kept up. */
    private static final float OPEN_COST = 25.0f;
    private static final float UPKEEP = 0.1f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is refreshed each tick. */
    private static final int MARK_TICKS = 10;

    /** How often the pale chakra at the eyes is drawn, in ticks. */
    private static final int AURA_INTERVAL = 20;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Reading a body is still reading with the eye, so the eye has to be open.
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        boolean opening = !isActive(player);
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_TICKS);

        if (player.level() instanceof ServerLevel level && player.tickCount % AURA_INTERVAL == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY(), player.getZ(),
                    1, 0.08d, 0.04d, 0.08d, 0.001d);
        }
    }

    /** @return whether the read is running on this caster right now. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stare is running, read from the synced toggle set. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * A stance is mastered by the time it is up, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The picture is drawn by the client overlay from the synced toggle set.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
