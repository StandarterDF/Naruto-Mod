package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.item.NarutoItems;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Kubikiribocho mends itself on what it kills.
 *
 * <p>The blade is not a sword that can be taken to a forge: the iron in it is the iron it has taken
 * from bodies, and the edge knits back together a little every time the weapon is fed. A quarter of
 * a chipped blade comes back over a fight, which is why the Executioner's Blade is carried rather
 * than maintained - and why losing it is worse than losing an arm.</p>
 *
 * <p>The repair is small and random within a band rather than a fixed slice, so no two kills are
 * worth exactly the same. A kill with anything other than the sword in hand does nothing.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KubikiribochoEvents {

    /** How much of the blade's life a kill puts back, as a share of its full durability. */
    private static final float MIN_REPAIR_SHARE = 0.12f;
    private static final float MAX_REPAIR_SHARE = 0.15f;

    private static final int REPAIR_PARTICLES = 8;

    private KubikiribochoEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity killer)) {
            return;
        }
        ItemStack held = killer.getMainHandItem();
        if (!held.is(NarutoItems.KUBIKIRIBOCHO.get()) || !held.isDamageableItem()) {
            return;
        }
        int wear = held.getDamageValue();
        if (wear <= 0) {
            return;
        }
        // Kenjutsu mastery makes the blade knit back together faster: every learned
        // node in the branch adds a little to the repair, so a fully trained swordsman
        // gets the Executioner's Blade back to shape in a couple of kills.
        float kenjutsuBonus = 0.0f;
        // Count unlocked Kenjutsu nodes.
        if (killer instanceof net.minecraft.world.entity.player.Player) {
            var opt = killer.getCapability(NinjaCapabilityHandler.NINJA_DATA);
            INinjaData data = opt.resolve().orElse(null);
            if (data != null) {
                int count = 0;
                for (Skill skill : Skill.all()) {
                    if (skill.branch() == SkillBranch.KENJUTSU && data.getSkillData().getUnlocked().contains(skill.id())) {
                        count++;
                    }
                }
                kenjutsuBonus = count * 0.02f;
            }
        }
        float share = MIN_REPAIR_SHARE + killer.getRandom().nextFloat() * (MAX_REPAIR_SHARE - MIN_REPAIR_SHARE);
        share = Math.min(0.9f, share + kenjutsuBonus);
        int mended = Math.round(held.getMaxDamage() * share);
        held.setDamageValue(Math.max(0, wear - mended));

        if (killer.level() instanceof ServerLevel level) {
            Vec3 hand = killer.position().add(0.0d, killer.getBbHeight() * 0.6d, 0.0d);
            level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z,
                    REPAIR_PARTICLES, 0.3d, 0.3d, 0.3d, 0.3d);
        }
    }
}
