package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.abilities.jutsus.KotoamatsukamiAbility;
import com.sekwah.narutomod.events.GenjutsuEvents;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public class RinneganPrisonAbility extends Ability implements Ability.Channeled, Ability.Cooldown {
    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinnegan_prison");

    private static final double RANGE = 8.0d;
    private static final float COST = 80.0f;
    public static final int LIFETIME = 12000; // 10 minutes
    public static final String PRISON_TAG = "narutomod:prison_summon";
    public static final String UNTIL_TAG = "narutomod:prison_until";
    public static final String OWNER_TAG = "narutomod:prison_owner";
    public static final String MAXHP_TAG = "narutomod:prison_maxhp";
    public static final String PRISON_LIST_TAG = "narutomod:prison_mobs";
    public static final String RELEASED_LIST_TAG = "narutomod:prison_released";
    public static final String ABSORPTION_UNTIL_TAG = "narutomod:prison_absorption_until";
    public static final String ABSORPTION_AMOUNT_TAG = "narutomod:prison_absorption_amount";

    /**
     * Chakra every held prisoner feeds its jailer each second. Flat on purpose: the cell banks the
     * body, not what the body was worth, so a rabbit and a soldier feed the same eye.
     */
    public static final float CHAKRA_PER_PRISONER = 2.0f;

    @Override
    public int noiseRadius() { return 12; }

    @Override
    public ActivationType activationType() { return ActivationType.CHANNELED; }

    /** Unique seal: the old 2332 collided with Rinbo Hengoku. */
    public static final long COMBO = 23322L;

    @Override
    public long defaultCombo() { return COMBO; }

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
        // Cooldown only gates the start of a cast, never a channel already running.
        if (chargeAmount <= 0 && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        // A tap pays the full price and captures; holding only keeps the channel open and costs nothing.
        if (chargeAmount < 0) {
            float cost = this.masteryCost(ninjaData, COST);
            if (ninjaData.getChakra() < cost) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
                return false;
            }
            ninjaData.useChakra(cost, 20);
        }
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) return;
        CompoundTag pdata = player.getPersistentData();
        ListTag prisoners = pdata.getList(PRISON_LIST_TAG, 8); // 8 = string
        if (ticksActive < 0) {
            // Capture mode: a tap always imprisons every valid mob in range, even when the cell
            // already holds prisoners - new bodies are simply added to the list.
            int count = 0;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(RANGE),
                    e -> e != player && e.isAlive() && !(e instanceof Player) && e.getMaxHealth() <= 125.0f
                            && !Friends.areFriends(player, e))) {
                ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
                prisoners.add(StringTag.valueOf((typeId == null ? "minecraft:pig" : typeId.toString()) + "|" + target.getMaxHealth()));
                target.discard();
                count++;
            }
            pdata.put(PRISON_LIST_TAG, prisoners);
            ninjaData.addChakra(count * 10.0f);
            player.displayClientMessage(Component.translatable("jutsu.rinnegan_prison.captured",
                    Component.literal(String.valueOf(count)).withStyle(ChatFormatting.GOLD)), true);
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        } else {
            // Release mode: free every prisoner at once, all of them now under genjutsu.
            long until = level.getGameTime() + LIFETIME;
            int released = 0;
            int total = prisoners.size();
            ListTag releasedList = pdata.getList(RELEASED_LIST_TAG, 8); // 8 = string
            for (int i = 0; i < total; i++) {
                String s = prisoners.getString(i);
                String[] parts = s.split("\\|");
                if (parts.length < 2) continue;
                ResourceLocation typeId;
                try {
                    typeId = new ResourceLocation(parts[0]);
                } catch (Exception ignored) {
                    continue;
                }
                float maxHp;
                try {
                    maxHp = Float.parseFloat(parts[1]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
                if (type == null) continue;
                Entity entity = type.create(level);
                if (!(entity instanceof Mob mob)) continue;
                double angle = (Math.PI * 2.0d / Math.max(1, total)) * i;
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
                // Genjutsu control marks: the existing Koto handlers refuse to let these mobs
                // take the caster (or any ally of his) for a target, or strike him.
                data.putLong(KotoamatsukamiAbility.UNTIL_TAG, level.getGameTime() + LIFETIME);
                data.putString(KotoamatsukamiAbility.SOURCE_TAG, player.getStringUUID());
                level.addFreshEntity(mob);
                releasedList.add(StringTag.valueOf(mob.getUUID().toString()));
                // The freed body is under the same illusion the ordinary genjutsu plants: it fights for its
                // new master, never takes him (or another charmed body) for an enemy, and wears the illusion's
                // witch-light while it lasts.
                GenjutsuEvents.apply(player, mob, LIFETIME);
                // Visual: enchantment glyphs + soul particles read as a mind being taken.
                level.sendParticles(ParticleTypes.ENCHANT, mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.8d, mob.getZ(),
                        30, 0.35d, 0.3d, 0.35d, 0.4d);
                level.sendParticles(ParticleTypes.SOUL, mob.getX(),
                        mob.getY() + mob.getBbHeight() * 0.5d, mob.getZ(),
                        8, 0.25d, 0.35d, 0.25d, 0.01d);
                released++;
            }
            pdata.put(RELEASED_LIST_TAG, releasedList);
            pdata.remove(PRISON_LIST_TAG);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
            player.displayClientMessage(Component.translatable("jutsu.rinnegan_prison.released",
                    Component.literal(String.valueOf(released)).withStyle(ChatFormatting.GOLD)), true);
            if (released > 0) {
                this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
            }
        }
    }
}
