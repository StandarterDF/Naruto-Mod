package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A rod of the outer path, driven into whatever the caster is looking at.
 *
 * <p>The Rinnegan's black rods are receivers before they are weapons: a body pierced by one stops
 * being its own. A rod standing in the ground is the same idea stretched over an area - every ninja
 * who comes within three blocks of it loses the flow that would refill their pool, and the eye that
 * planted it can see them through the wall they are standing behind.</p>
 *
 * <p>The rod is a display entity rather than a block, so it leaves the world's construction and
 * collision untouched and can be placed on any face without a block update. It is permanent until
 * the world removes it; the technique is a claim on ground, not a thrown weapon.</p>
 */
public class BlackRodAbility extends Ability implements Ability.Cooldown {

    /** A rod being driven in is heard as a short, hard strike. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** C V V C V B - the Rinnegan (C V V C) and two more keys. */
    public static final long COMBO = 233321L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "black_rod");

    /** The rod itself, and the caster who owns it. Both live on the display's persistent data. */
    public static final String ROD_TAG = "narutomod:black_rod";
    public static final String OWNER_TAG = "narutomod:black_rod_owner";

    /** How far down the look ray a rod can be driven. */
    private static final double RANGE = 6.0d;

    /** What it costs and how long the eye needs before another rod can be driven. */
    private static final float COST = 25.0f;
    private static final int COOLDOWN = 100;

    /** How close a ninja has to stand to a rod to lose their regeneration. */
    public static final double SUPPRESSION_RADIUS = 3.0d;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (Targeting.lookedAtBlock(player, RANGE) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.black_rod.noblock",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return;
        }
        // Half a centimetre proud of the face, so the rod reads as standing on the surface rather
        // than sunk into it.
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 spot = hit.getLocation().add(normal.scale(0.08d));

        Display.ItemDisplay rod = EntityType.ITEM_DISPLAY.create(level);
        if (rod == null) {
            return;
        }
        // The item stack has no public setter in 1.20.1; loading the display's own save format is
        // the same path a rod restored from disk takes.
        CompoundTag display = new CompoundTag();
        display.put("item", new ItemStack(Items.END_ROD).save(new CompoundTag()));
        rod.load(display);
        rod.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0f);
        rod.setNoGravity(true);

        CompoundTag data = rod.getPersistentData();
        data.putBoolean(ROD_TAG, true);
        data.putUUID(OWNER_TAG, player.getUUID());

        level.addFreshEntity(rod);
        level.sendParticles(ParticleTypes.SQUID_INK, spot.x, spot.y + 0.1d, spot.z,
                14, 0.18d, 0.18d, 0.18d, 0.02d);
    }

    /** @return whether the entity is one of the rods this technique places. */
    public static boolean isRod(Entity entity) {
        return entity.getPersistentData().getBoolean(ROD_TAG);
    }
}
