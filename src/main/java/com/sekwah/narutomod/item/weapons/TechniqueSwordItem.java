package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.abilities.jutsus.HiramekareiAbility;
import com.sekwah.narutomod.abilities.jutsus.ShibukiAbility;
import com.sekwah.narutomod.item.ComboSwordItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * A great blade whose technique lives in the sword rather than in a combo.
 *
 * <p>Hiramekarei and Shibuki are not spells a ninja learns to cast; they are things a particular
 * blade does. Right-clicking the blade uses its art, the same way the guard is raised from any of
 * the mod's swords, so the two no longer take up a combo slot or a row in the jutsu menu.</p>
 */
public class TechniqueSwordItem extends ComboSwordItem {

    /** Which blade's art this item carries. */
    public enum Art {
        HIRAMEKAREI,
        SHIBUKI
    }

    private final Art art;

    public TechniqueSwordItem(Tier tier, int attackDamage, float attackSpeed, Properties properties,
                              float damagePerStep, int maxCombo, int comboWindow, Art art) {
        super(tier, attackDamage, attackSpeed, properties, damagePerStep, maxCombo, comboWindow, 0.0f);
        this.art = art;
    }

    public Art art() {
        return this.art;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level.isClientSide) {
            if (this.art == Art.HIRAMEKAREI) {
                HiramekareiAbility.raise(player);
            } else {
                ShibukiAbility.swing(player);
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
