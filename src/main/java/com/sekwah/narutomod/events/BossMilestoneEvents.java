package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.NarutoItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/**
 * The first time a ninja brings down one of the world's great monsters, the village sends them a
 * scroll.
 *
 * <p>These are milestones rather than loot: each is paid once per ninja, which is why the claimed
 * id is written into their ninja data. A second Ender Dragon is just a fight; the first one is the
 * story beat. The scrolls chosen are the expensive ones - a boss should hand over something a
 * village cannot sell.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BossMilestoneEvents {

    private BossMilestoneEvents() {
    }

    /** One boss, its claimed id and the scroll it hands over. */
    private record Milestone(String id, Supplier<Item> scroll) {
    }

    private static Milestone milestoneFor(Entity entity) {
        if (entity instanceof EnderDragon) {
            return new Milestone("ender_dragon", NarutoItems.SCROLL_RASENSHURIKEN);
        }
        if (entity instanceof WitherBoss) {
            return new Milestone("wither", NarutoItems.SCROLL_EIGHT_GATES);
        }
        if (entity instanceof ElderGuardian) {
            return new Milestone("elder_guardian", NarutoItems.SCROLL_KAMUI);
        }
        if (entity instanceof Warden) {
            return new Milestone("warden", NarutoItems.SCROLL_SAGE_MODE);
        }
        return null;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Milestone milestone = milestoneFor(event.getEntity());
        if (milestone == null) {
            return;
        }
        Player killer = killingPlayer(event);
        if (killer == null || killer.level().isClientSide) {
            return;
        }
        killer.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isNinjaModeEnabled() || ninjaData.hasClaimedBossMilestone(milestone.id())) {
                return;
            }
            ninjaData.claimBossMilestone(milestone.id());
            ItemStack scroll = new ItemStack(milestone.scroll().get());
            if (!killer.getInventory().add(scroll)) {
                killer.drop(scroll, false);
            }
            killer.displayClientMessage(Component.translatable("naruto.boss.milestone",
                            event.getEntity().getDisplayName(), scroll.getHoverName())
                    .withStyle(ChatFormatting.GOLD), false);
        });
    }

    /** @return the ninja credited with the kill, whether by blade, arrow or a clone. */
    private static Player killingPlayer(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (source instanceof Player player) {
            return player;
        }
        Entity credit = event.getEntity().getKillCredit();
        return credit instanceof Player player ? player : null;
    }
}
