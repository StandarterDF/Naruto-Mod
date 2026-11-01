package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ChakraTransferAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The seal itself: while it holds, every ally the thread reaches is fed from the caster's side.
 *
 * <p>The seal is stored on the caster alone as the list of allies it reaches, so a group is ticked
 * once from one side instead of once per member: threads that leave the radius, die or log out are
 * dropped one by one, and the last one leaving closes the seal.</p>
 *
 * <p>Pools are never averaged. The allies simply receive chakra every second; the caster keeps what
 * they had and recovers a smaller amount of their own. Averaging would make a full caster poorer for
 * helping a nearly empty ally, which is the opposite of what the seal is for.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChakraSealEvents {
    private ChakraSealEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player caster = event.player;
        ListTag stored = caster.getPersistentData().getList(ChakraTransferAbility.ALLIES_TAG, Tag.TAG_STRING);
        if (stored.isEmpty()) {
            return;
        }
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        INinjaData data = caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (data == null) {
            return;
        }
        float mastery = Mastery.level(data, com.sekwah.narutomod.abilities.NarutoAbilities.CHAKRA_TRANSFER.get());
        double radius = ChakraTransferAbility.RANGE + 24.0d * mastery;

        // Every ally still in reach is kept; any thread that snapped is dropped from the seal.
        ListTag kept = new ListTag();
        List<INinjaData> allies = new ArrayList<>();
        for (int i = 0; i < stored.size(); i++) {
            UUID id;
            try {
                id = UUID.fromString(stored.getString(i));
            } catch (IllegalArgumentException malformed) {
                continue;
            }
            Player ally = level.getServer().getPlayerList().getPlayer(id);
            if (ally == null || ally.level() != level || !ally.isAlive()
                    || caster.distanceTo(ally) > radius) {
                continue;
            }
            INinjaData allyData = ally.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
            if (allyData == null) {
                continue;
            }
            kept.add(StringTag.valueOf(id.toString()));
            allies.add(allyData);
        }
        if (allies.isEmpty()) {
            // Nobody left to feed: the seal closes on its own.
            caster.getPersistentData().remove(ChakraTransferAbility.ALLIES_TAG);
            return;
        }
        if (kept.size() != stored.size()) {
            caster.getPersistentData().put(ChakraTransferAbility.ALLIES_TAG, kept);
        }

        // Additive feeding only: no pool levelling. Allies gain more than the caster, because the
        // caster is the one doing the work.
        if (caster.tickCount % 20 == 0) {
            for (INinjaData ally : allies) {
                ally.addChakra(ChakraTransferAbility.ALLY_REGEN_PER_SECOND);
            }
            data.addChakra(ChakraTransferAbility.CASTER_REGEN_PER_SECOND);
        }
    }
}
