package com.sekwah.narutomod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.sekwah.narutomod.item.armor.NarutoArmorMaterial;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Toneri's scarf: a keepsake that hardens the wearer against incoming blows and quickens the
 * passive gathering of nature energy. It does nothing for the active Senjutsu Charging technique.
 */
public class ToneriScarfItem extends ArmorItem {

    public ToneriScarfItem(Properties properties) {
        super(NarutoArmorMaterial.CHARACTER_CLOTHES, ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "narutomod:textures/models/armor/toneri_scarf.png";
    }

    /**
     * @return whether this player is wearing a Toneri scarf in the chest slot. A single scarf is
     *         enough; wearing more does not stack.
     */
    public static boolean hasScarf(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ToneriScarfItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.toneri_scarf.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}