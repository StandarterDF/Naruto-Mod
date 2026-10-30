package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class RinneganPrisonAbility extends Ability implements Ability.Cooldown {
    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinnegan_prison");

    private static final double RANGE = 8.0d;
    private static final float COST = 80.0f;
    public static final int LIFETIME = 12000; // 10 minutes
    public static final String PRISON_TAG = "narutomod:prison_summon";
    public static final String UNTIL_TAG = "narutomod:prison_until";
    public static final String OWNER_TAG = "narutomod:prison_owner";
    public static final String MAXHP_TAG = "narutomod:prison_maxhp";
    public static final String PRISON_LIST_TAG = "narutomod:prison_mobs";
    public static final String ABSORPTION_UNTIL_TAG = "narutomod:prison_absorption_until";
    public static final String ABSORPTION_AMOUNT_TAG = "narutomod:prison_absorption_amount";

    @Override
    public int noiseRadius() { return 12; }

    @Override
    public ActivationType activationType() { return ActivationType.INSTANT; }

    @Override
    public long defaultCombo() { return 2332L; }

    @Override
    public int getCooldown() { return 600; }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled").withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
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
        if (!(player.level() instanceof ServerLevel level)) return;
        CompoundTag pdata = player.getPersistentData();
        ListTag prisoners = pdata.getList(PRISON_LIST_TAG, 8); // 8 = string
        if (prisoners.isEmpty()) {
            // Capture mode
            int count = 0;
            float totalHp = 0.0f;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(RANGE),
                    e -> e != player && e.isAlive() && !(e instanceof Player) && e.getMaxHealth() <= 125.0f
                            && !Friends.areFriends(player, e))) {
                totalHp += target.getMaxHealth();
                ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
                prisoners.add(StringTag.valueOf((typeId == null ? "minecraft:pig" : typeId.toString()) + "|" + target.getMaxHealth()));
                target.discard();
                count++;
            }
            pdata.put(PRISON_LIST_TAG, prisoners);
            ninjaData.addChakra(count * 10.0f);
            player.displayClientMessage(Component.translatable("jutsu.rinnegan_prison.captured",
                    Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD)), true);
        } else {
            // Release mode
            long until = level.getGameTime() + LIFETIME;
            int released = 0;
            for (int i = 0; i < prisoners.size(); i++) {
                String s = prisoners.getString(i);
                String[] parts = s.split("\\|");
                if (parts.length < 2) continue;
                ResourceLocation typeId = new ResourceLocation(parts[0]);
                float maxHp = Float.parseFloat(parts[1]);
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
                if (type == null) continue;
                Entity entity = type.create(level);
                if (!(entity instanceof Mob mob)) continue;
                double angle = (Math.PI * 2.0d / prisoners.size()) * i;
                double x = player.getX() + Math.cos(angle) * 2.0d;
                double z = player.getZ() + Math.sin(angle) * 2.0d;
                mob.moveTo(x, player.getY(), z, player.getYRot(), 0.0f);
                mob.setHealth(maxHp);
                mob.setPersistenceRequired();
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    mob.setDropChance(slot, 0.0f);
                }
                CompoundTag data = mob.getPersistentData();
                data.putBoolean(PRISON_TAG, true);
                data.putLong(UNTIL_TAG, until);
                data.putUUID(OWNER_TAG, player.getUUID());
                data.putFloat(MAXHP_TAG, maxHp);
                level.addFreshEntity(mob);
                released++;
            }
            pdata.remove(PRISON_LIST_TAG);
            player.displayClientMessage(Component.translatable("jutsu.rinnegan_prison.released",
                    Component.literal(String.valueOf(released)).withStyle(ChatFormatting.GOLD)), true);
        }
    }
}
