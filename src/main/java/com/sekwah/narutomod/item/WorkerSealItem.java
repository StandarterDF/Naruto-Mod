package com.sekwah.narutomod.item;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Worker's Seal: a talisman that pins a shadow clone to a patch of ground and puts it to work.
 *
 * <p>Used on a block, it leaves a clone of the caster standing there with both hands empty. The clone
 * does not follow, does not fight and does not come back on its own: it works the field around the
 * spot it was left on, reaping whatever is ripe and dropping the crop into a bag it carries. When it
 * finally pops, the bag is handed to the caster - not at once, but
 * {@code ShadowCloneEntity#WORKER_LOOT_DELAY} ticks later, which is what stops a worker from being a
 * way to move a farm across the map.</p>
 *
 * @see com.sekwah.narutomod.entity.ai.WorkerHarvestGoal
 */
public class WorkerSealItem extends Item {

    /** How long a worker works before it runs out of chakra and hands the harvest over. */
    public static final int WORKER_LIFETIME = 20 * 60 * 10;

    public WorkerSealItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos home = context.getClickedPos().relative(context.getClickedFace());
        Vec3 spot = Vec3.atBottomCenterOf(home);

        ShadowCloneEntity worker = new ShadowCloneEntity(serverLevel, player, WORKER_LIFETIME, false);
        worker.setWorker(true);
        worker.setWorkerHome(home);
        // A farm hand needs both hands free: equipment is copied by the constructor, so it is put
        // away again here rather than left swinging a sword at the wheat.
        worker.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        worker.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        worker.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0f);
        serverLevel.addFreshEntity(worker);

        serverLevel.sendParticles(ParticleTypes.CLOUD, spot.x, spot.y + 0.9d, spot.z,
                30, 0.4d, 0.5d, 0.4d, 0.02d);
        serverLevel.playSound(null, spot.x, spot.y, spot.z, NarutoSounds.SHADOW_CLONE_POOF.get(),
                SoundSource.PLAYERS, 0.8f, 1.0f);

        if (!player.isCreative()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
