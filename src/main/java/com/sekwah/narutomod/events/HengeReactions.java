package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The world reacting to a disguise.
 *
 * <p>Henge only copies a face; by default nobody cares. With {@code hengeReactionsEnabled} the face
 * carries a little of the original's reputation: a monster's face makes villagers run for their
 * houses and turns the village's iron golems against the wearer. It is off by default because a
 * disguise quietly rewriting mob AI is exactly the sort of thing a server may not want.</p>
 *
 * <p>The reactions are deliberately timid: no damage, no new goals installed on the mobs, just a
 * shove in the direction they would take anyway - villagers flee, golems pick a target. Everything
 * expires on its own when the disguise drops, because nothing is stored on the mob beyond a short
 * cooldown.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HengeReactions {

    /** How far a disguise is noticed, in blocks. */
    private static final double SCARE_RANGE = 8.0d;

    /** How far an iron golem takes an interest, in blocks. */
    private static final double GOLEM_RANGE = 12.0d;

    /** Once every second is plenty for a reaction that is only flavour. */
    private static final int CHECK_INTERVAL = 20;

    /** How long a scared villager keeps running before it can be frightened again. */
    private static final int SCARE_COOLDOWN = 100;

    private static final String SCARED_UNTIL_TAG = "narutomod:henge_scared_until";

    private HengeReactions() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !NarutoConfig.hengeReactionsEnabled) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!ninjaData.isHenged()) {
                return;
            }
            EntityType<?> disguise = EntityType.byString(ninjaData.getHengeEntityType()).orElse(null);
            if (disguise == null || disguise.getCategory() != MobCategory.MONSTER) {
                // A friendly or player face draws no reaction at all.
                return;
            }
            scareVillagers(level, player);
            angerGolems(level, player);
        });
    }

    private static void scareVillagers(ServerLevel level, Player player) {
        AABB area = player.getBoundingBox().inflate(SCARE_RANGE);
        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, area, LivingEntity::isAlive)) {
            long now = level.getGameTime();
            long scaredUntil = villager.getPersistentData().getLong(SCARED_UNTIL_TAG);
            if (scaredUntil > now) {
                continue;
            }
            villager.getPersistentData().putLong(SCARED_UNTIL_TAG, now + SCARE_COOLDOWN);
            villager.getLookControl().setLookAt(player, 30.0f, 30.0f);
            // Run straight away from the wearer, as if a zombie had walked in.
            Vec3 away = villager.position().subtract(player.position());
            Vec3 dir = away.lengthSqr() < 0.01d
                    ? new Vec3(villager.getRandom().nextDouble() - 0.5d, 0.0d, villager.getRandom().nextDouble() - 0.5d)
                    : away.normalize();
            villager.getNavigation().moveTo(
                    villager.getX() + dir.x * 8.0d,
                    villager.getY(),
                    villager.getZ() + dir.z * 8.0d,
                    1.0d);
            level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO,
                    SoundSource.NEUTRAL, 0.7f, 1.0f);
        }
    }

    private static void angerGolems(ServerLevel level, Player player) {
        AABB area = player.getBoundingBox().inflate(GOLEM_RANGE);
        for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, area, LivingEntity::isAlive)) {
            // Only pick a fight that is not already being fought, so a golem chasing a zombie is left
            // alone and the disguise does not make the village defenceless.
            if (golem.getTarget() == null) {
                golem.setTarget(player);
            }
        }
    }
}
