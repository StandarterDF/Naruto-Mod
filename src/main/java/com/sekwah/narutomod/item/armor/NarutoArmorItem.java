package com.sekwah.narutomod.item.armor;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.client.renderer.NarutoRenderEvents;
import com.sekwah.narutomod.item.interfaces.IShouldHideNameplate;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * A piece of the mod's armour.
 *
 * <p>Besides the protection its material gives, a piece can carry an {@link Bonus}: the mod's armour
 * is ninja kit rather than plate, and what it is for is chakra, not stopping swords. See
 * {@code events/NarutoArmorEvents} for where the bonuses are actually handed out.</p>
 */
public class NarutoArmorItem extends ArmorItem implements IShouldHideNameplate {

    /**
     * What a piece of armour adds to its wearer while it is on. All of it is optional; a piece with
     * no bonus says nothing and does nothing.
     *
     * @param chakraCapacity       extra points of maximum chakra.
     * @param maxHealth            extra points of health, in half hearts.
     * @param chakraRegenPerSecond chakra poured back into the caster every second.
     */
    public record Bonus(float chakraCapacity, float maxHealth, float chakraRegenPerSecond) {
        public static final Bonus NONE = new Bonus(0.0f, 0.0f, 0.0f);

        public boolean isEmpty() {
            return chakraCapacity == 0.0f && maxHealth == 0.0f && chakraRegenPerSecond == 0.0f;
        }
    }

    private HumanoidModel armorModel = null;
    private String armorTexture = null;
    private boolean forceHideName = false;
    private Bonus bonus = Bonus.NONE;

    public NarutoArmorItem(ArmorMaterial armorMaterial, ArmorItem.Type type, Item.Properties properties) {
        super(armorMaterial, type, properties);
    }

    public NarutoArmorItem setBonus(Bonus bonus) {
        this.bonus = bonus;
        return this;
    }

    public Bonus getBonus() {
        return this.bonus;
    }

    /**
     * The bonuses are listed where the player will see them, under the protection the material
     * already prints.
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (this.bonus.chakraCapacity() > 0.0f) {
            tooltip.add(Component.translatable("narutomod.armor.bonus.chakra", amount(this.bonus.chakraCapacity()))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (this.bonus.maxHealth() > 0.0f) {
            tooltip.add(Component.translatable("narutomod.armor.bonus.health", amount(this.bonus.maxHealth()))
                    .withStyle(ChatFormatting.RED));
        }
        if (this.bonus.chakraRegenPerSecond() > 0.0f) {
            tooltip.add(Component.translatable("narutomod.armor.bonus.regen", amount(this.bonus.chakraRegenPerSecond()))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Prints a whole number without a trailing ".0". */
    private static String amount(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.valueOf(value);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return armorTexture;
    }

    public NarutoArmorItem setArmorModel(HumanoidModel<? extends LivingEntity> armorModel) {
        this.armorModel = armorModel;
        return this;
    }

    public NarutoArmorItem setShouldHideNameplate(boolean shouldHide) {
        this.forceHideName = shouldHide;
        return this;
    }

    public NarutoArmorItem setArmorTexture(String armorTexture) {
        this.armorTexture = NarutoMod.MOD_ID + ":" + armorTexture;
        return this;
    }

    @Override
    public boolean shouldHideNameplate(Entity entity) {
        return this.forceHideName;
    }


    // A little confusing but this is how it works now
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        super.initializeClient(consumer);
        consumer.accept(new IClientItemExtensions() {

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer()
            {
                return NarutoRenderEvents.NARUTO_RENDERER;
            }

            @Override
            public HumanoidModel getHumanoidArmorModel(LivingEntity entityLiving, ItemStack itemStack, EquipmentSlot armorSlot, HumanoidModel _default) {
                return armorModel;
            }
        });
    }
}
