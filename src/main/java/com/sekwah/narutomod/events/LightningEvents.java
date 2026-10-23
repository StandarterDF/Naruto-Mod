package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.item.ChakraBatteryItem;
import com.sekwah.narutomod.item.ChakraCrystalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A lightning strike on a rod refills the chakra batteries around it.
 *
 * <p>This is the one way to charge a battery without spending chakra to do it, and it is weather
 * rather than a technique: a storm over a base is worth waiting out, and a lightning rod turns a
 * random hazard into a slow generator. Only a strike that actually lands on a rod counts - a bolt
 * that hits a tree or a mob is just a bolt.</p>
 *
 * <p>Batteries are charged both in the inventories of nearby players and wherever they lie on the
 * ground, so a row of batteries left under the rod can be topped up in one storm.</p>
 *
 * <p>Forge 47 has no lightning strike event, so this listens for the bolt itself being added to the
 * world. The vanilla bolt is already moved onto the rod by the time it is spawned, which is why
 * looking for a rod under the bolt is enough to tell a caught strike from a wild one.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class LightningEvents {

    /** How much chakra one strike puts into each battery. */
    private static final float CHARGE_PER_STRIKE = 150.0f;

    /** How far from the strike batteries are topped up, in blocks. */
    private static final double CHARGE_RADIUS = 8.0d;

    /** How far from the strike point a lightning rod may stand and still have caught the bolt. */
    private static final int ROD_SEARCH = 3;

    private LightningEvents() {
    }

    @SubscribeEvent
    public static void onLightningBolt(EntityJoinLevelEvent event) {
        if (!NarutoConfig.lightningChargesBatteries) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LightningBolt bolt)) {
            return;
        }
        Vec3 strike = bolt.position();
        if (!struckRod(level, BlockPos.containing(strike))) {
            return;
        }
        AABB area = new AABB(strike, strike).inflate(CHARGE_RADIUS);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            chargeInventory(player);
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            charge(item.getItem());
        }
    }

    /** @return whether a lightning rod stood close enough to the strike to have drawn it. */
    private static boolean struckRod(ServerLevel level, BlockPos centre) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ROD_SEARCH, -ROD_SEARCH, -ROD_SEARCH),
                centre.offset(ROD_SEARCH, ROD_SEARCH, ROD_SEARCH))) {
            if (level.getBlockState(pos).is(Blocks.LIGHTNING_ROD)) {
                return true;
            }
        }
        return false;
    }

    /** Tops up every battery the player carries, each up to its own capacity. */
    private static void chargeInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ChakraBatteryItem
                    || stack.getItem() instanceof ChakraCrystalItem) {
                charge(stack);
            }
        }
    }

    /** Puts one strike's worth of chakra into a battery or crystal, never past its capacity. */
    private static void charge(ItemStack stack) {
        if (stack.getItem() instanceof ChakraBatteryItem) {
            ChakraBatteryItem.setStoredChakra(stack,
                    ChakraBatteryItem.getStoredChakra(stack) + CHARGE_PER_STRIKE);
        } else if (stack.getItem() instanceof ChakraCrystalItem) {
            // A crystal is smaller than a battery, so a single strike fills it outright.
            ChakraCrystalItem.setCharge(stack, ChakraCrystalItem.getCharge(stack) + CHARGE_PER_STRIKE);
        }
    }
}
