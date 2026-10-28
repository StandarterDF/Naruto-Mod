package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;

/**
 * Applies the four skill bonuses that have to live on an attribute - maximum health, armour,
 * movement speed and attack damage - because vanilla has nowhere else to put them.
 *
 * <p>The rest of the tree is read straight off the player data where it is used (chakra capacity,
 * regeneration, technique costs), but these four are vanilla attributes, so the learned total is
 * written onto them here every tick. The modifier is only rebuilt when the total actually changes,
 * and the fixed uuids mean a caster never ends up with two copies of the same bonus.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SkillTreeEvents {

    private static final UUID HEALTH_ID = UUID.fromString("5b5a4b0c-7b1e-4f0e-9f5a-3c8b1f6a2d10");
    private static final UUID ARMOR_ID = UUID.fromString("0f2e8d3a-6c47-4b9d-8a1e-2d5f7c9b4e31");
    private static final UUID SPEED_ID = UUID.fromString("a1c4f2b7-3d58-4e9a-8b6f-1e0d7c2a5b93");
    private static final UUID DAMAGE_ID = UUID.fromString("7e3b9a15-2c64-4f80-9d1b-4a6e8f0c3d27");
    private static final UUID ATTACK_SPEED_ID = UUID.fromString("9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f");

    private static final String HEALTH_NAME = "narutomod.skill.health";
    private static final String ARMOR_NAME = "narutomod.skill.armor";
    private static final String SPEED_NAME = "narutomod.skill.speed";
    private static final String DAMAGE_NAME = "narutomod.skill.damage";
    private static final String ATTACK_SPEED_NAME = "narutomod.skill.attack_speed";

    /** Applies all skill attribute bonuses immediately, used on respawn/clone. */
    public static void applyAll(Player player, INinjaData ninjaData) {
        if (player.level().isClientSide) {
            return;
        }
        Set<ResourceLocation> unlocked = ninjaData.getSkillData().getUnlocked();
        float rounded = SkillTree.bonus(unlocked, SkillEffect.ALL_ROUNDED);
        apply(player, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_NAME,
                SkillTree.bonus(unlocked, SkillEffect.MAX_HEALTH) + rounded);
        apply(player, Attributes.ARMOR, ARMOR_ID, ARMOR_NAME,
                SkillTree.bonus(unlocked, SkillEffect.ARMOR));
        apply(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_NAME,
                SkillTree.bonus(unlocked, SkillEffect.MOVEMENT_SPEED));
        apply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_NAME,
                SkillTree.bonus(unlocked, SkillEffect.ATTACK_DAMAGE));
        apply(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, ATTACK_SPEED_NAME,
                countKenjutsu(unlocked) * 0.05f);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            Set<ResourceLocation> unlocked = ninjaData.getSkillData().getUnlocked();
            float rounded = SkillTree.bonus(unlocked, SkillEffect.ALL_ROUNDED);
            apply(player, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH_NAME,
                    SkillTree.bonus(unlocked, SkillEffect.MAX_HEALTH) + rounded);
            apply(player, Attributes.ARMOR, ARMOR_ID, ARMOR_NAME,
                    SkillTree.bonus(unlocked, SkillEffect.ARMOR));
            apply(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_NAME,
                    SkillTree.bonus(unlocked, SkillEffect.MOVEMENT_SPEED));
            apply(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE_NAME,
                    SkillTree.bonus(unlocked, SkillEffect.ATTACK_DAMAGE));
        apply(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, ATTACK_SPEED_NAME,
                countKenjutsu(unlocked) * 0.05f);
        });
    }

    private static int countKenjutsu(Set<ResourceLocation> unlocked) {
        int count = 0;
        for (Skill skill : Skill.all()) {
            if (skill.branch() == SkillBranch.KENJUTSU && unlocked.contains(skill.id())) {
                count++;
            }
        }
        return count;
    }

    private static void apply(Player player, Attribute attribute, UUID id, String name, float amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (amount <= 0.0f) {
            if (existing != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.getAmount() == amount) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(id);
        }
        instance.addTransientModifier(new AttributeModifier(id, name, amount,
                AttributeModifier.Operation.ADDITION));
    }
}
