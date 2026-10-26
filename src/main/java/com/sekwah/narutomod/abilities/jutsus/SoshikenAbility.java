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
import net.minecraft.world.phys.Vec3;

/**
 * Soshiken: the gentle fist worn as a stance rather than thrown as a technique.
 *
 * <p>The Byakugan's palms are normally a decision - open the eye, then strike. This makes the palm
 * the stance itself: while the seal is held, every empty-handed blow carries a thread of chakra into
 * the tenketsu it lands on, so the caster's ordinary fist does the work of a technique. The eye has
 * to be open, because a palm that does not see the network under the skin is only a punch.</p>
 *
 * <p>All of that work happens where the hit lands: {@code TechniqueMarkEvents} reads
 * {@link #UNTIL_TAG} on every melee blow, adds the extra damage, seals the channels it touched and
 * pays the per-hit chakra. This class only keeps the mark alive, pays the upkeep and draws the faint
 * chakra on the palms. That is also why the mark is stamped a few ticks ahead instead of only on the
 * tick a key is down: the frame in which a hit is registered is not the frame in which the stance
 * was paid for.</p>
 *
 * <p>There is no cooldown. The upkeep is the price, and the stance ends the moment the caster lets
 * go, runs dry or closes the eye.</p>
 */
public class SoshikenAbility extends Ability implements Ability.Channeled {

    /** A stance is a held breath; the palm it charges is heard when it lands. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V V B B - the Byakugan, then the seal of the soft fist. */
    public static final long COMBO = 232231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "soshiken");

    /**
     * The stamp the palm hook reads on every melee hit, see {@code TechniqueMarkEvents}. Written as
     * game time rather than a flag so it survives a relog and needs no cleanup when the stance ends.
     */
    public static final String UNTIL_TAG = "narutomod:soshiken_until";

    /** How far ahead of the clock the mark is kept while the stance is held, in ticks. */
    private static final long MARK_TICKS = 10L;

    /** What opening the seal costs, and what every tick of holding it costs on top. */
    private static final float BASE_COST = 40.0f;
    private static final float COST_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How often the chakra gathered on the palms is drawn, in ticks. */
    private static final int HALO_INTERVAL = 4;

    /**
     * Where the palms sit relative to the body. The same offsets {@code JutsuEffects#handPosition}
     * uses, repeated here because that helper only places the right hand and the stance charges both.
     */
    private static final double PALM_FORWARD = 0.30d;
    private static final double PALM_SIDE = 0.34d;
    private static final double PALM_HEIGHT = 1.15d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** The stance is a held seal: a tap would charge the palms for no time at all. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The stance is a way of using the eye, not another eye: it needs the Byakugan open.
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // The seal is paid for once, when the stance opens; the palms are paid for by the tick.
        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? BASE_COST + COST_PER_TICK : COST_PER_TICK);
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
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        // Stamped every tick rather than once, so a hit that lands between two ticks is still inside
        // the stance; letting go simply stops the stamp being pushed forward.
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_TICKS);
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (ticksChanneled % HALO_INTERVAL != 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        drawHalo(level, player);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // The mark was stamped a few ticks ahead and lapses on its own, so a release needs no cleanup.
    }

    /**
     * A stance is mastered by holding it, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** A faint ring of chakra on both empty palms, so the caster can see what the stance is charging. */
    private static void drawHalo(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flatLook = new Vec3(look.x, 0.0d, look.z);
        flatLook = flatLook.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flatLook.normalize();
        Vec3 across = new Vec3(-flatLook.z, 0.0d, flatLook.x).scale(PALM_SIDE);
        Vec3 chest = player.position().add(flatLook.scale(PALM_FORWARD)).add(0.0d, PALM_HEIGHT, 0.0d);
        drawPalm(level, chest.add(across));
        drawPalm(level, chest.subtract(across));
    }

    private static void drawPalm(ServerLevel level, Vec3 palm) {
        level.sendParticles(ParticleTypes.END_ROD, palm.x, palm.y, palm.z,
                2, 0.07d, 0.05d, 0.07d, 0.0d);
    }
}
