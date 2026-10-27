package com.sekwah.narutomod.entity;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.entity.item.PaperBombEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.ChibakuTenseiEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.EarthWallEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.FireballJutsuEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.RasenshurikenJutsuEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.TailedBallEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.TravellingArcEntity;
import com.sekwah.narutomod.entity.jutsuprojectile.WaterBulletJutsuEntity;
import com.sekwah.narutomod.entity.projectile.ExplosiveKunaiEntity;
import com.sekwah.narutomod.entity.projectile.KunaiEntity;
import com.sekwah.narutomod.entity.projectile.SenbonEntity;
import com.sekwah.narutomod.entity.projectile.ShurikenEntity;
import com.sekwah.narutomod.entity.projectile.SmokeBombEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.sekwah.narutomod.NarutoMod.MOD_ID;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NarutoEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<KunaiEntity>> KUNAI = register("kunai",
            EntityType.Builder.<KunaiEntity>of(KunaiEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).setTrackingRange(8));

    public static final RegistryObject<EntityType<SenbonEntity>> SENBON = register("senbon",
            EntityType.Builder.<SenbonEntity>of(SenbonEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).setTrackingRange(8));

    public static final RegistryObject<EntityType<ExplosiveKunaiEntity>> EXPLOSIVE_KUNAI = register("explosive_kunai",
            EntityType.Builder.<ExplosiveKunaiEntity>of(ExplosiveKunaiEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).setTrackingRange(8));

    public static final RegistryObject<EntityType<ShurikenEntity>> SHURIKEN = register("shuriken",
            EntityType.Builder.<ShurikenEntity>of(ShurikenEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).setTrackingRange(8));

    // func_233608_b_ is updateInterval
    public static final RegistryObject<EntityType<PaperBombEntity>> PAPER_BOMB = register("paper_bomb",
            EntityType.Builder.<PaperBombEntity>of(PaperBombEntity::new, MobCategory.MISC).fireImmune().sized(0.5F, 0.5F).setTrackingRange(10).clientTrackingRange(10));


    public static final RegistryObject<EntityType<FireballJutsuEntity>> FIREBALL_JUTSU = register("fireball_jutsu",
            EntityType.Builder.<FireballJutsuEntity>of(FireballJutsuEntity::new, MobCategory.MISC).sized(1.5F, 1.5F).clientTrackingRange(4).updateInterval(10));

    public static final RegistryObject<EntityType<WaterBulletJutsuEntity>> WATER_BULLET_JUTSU = register("water_bullet_jutsu",
            EntityType.Builder.<WaterBulletJutsuEntity>of(WaterBulletJutsuEntity::new, MobCategory.MISC).fireImmune().sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(10));

    public static final RegistryObject<EntityType<RasenshurikenJutsuEntity>> RASENSHURIKEN_JUTSU = register("rasenshuriken_jutsu",
            EntityType.Builder.<RasenshurikenJutsuEntity>of(RasenshurikenJutsuEntity::new, MobCategory.MISC).fireImmune().sized(0.6F, 0.6F).clientTrackingRange(6).updateInterval(10));

    /**
     * The thrown Tailed Beast Bomb. Sized to hold the largest sphere it can draw: a full charge
     * throws a ball a block across.
     */
    public static final RegistryObject<EntityType<TailedBallEntity>> TAILED_BALL = register("tailed_ball",
            EntityType.Builder.<TailedBallEntity>of(TailedBallEntity::new, MobCategory.MISC).fireImmune().sized(1.2F, 1.2F).clientTrackingRange(8).updateInterval(10));

    /**
     * The travelling wind arc. It never moves and draws nothing itself - the front is a function
     * of its age and the crescent is particles - so the box only has to exist for the dispatcher.
     */
    public static final RegistryObject<EntityType<TravellingArcEntity>> TRAVELLING_ARC = register("travelling_arc",
            EntityType.Builder.<TravellingArcEntity>of(TravellingArcEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(10));


    public static final RegistryObject<EntityType<SubstitutionLogEntity>> SUBSTITUTION_LOG = register("substitution_log",
            EntityType.Builder.<SubstitutionLogEntity>of(SubstitutionLogEntity::new, MobCategory.MISC).fireImmune().sized(0.3F, 0.3F).clientTrackingRange(4));

    public static final RegistryObject<EntityType<ShadowCloneEntity>> SHADOW_CLONE = register("shadow_clone",
            EntityType.Builder.<ShadowCloneEntity>of(ShadowCloneEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).clientTrackingRange(10));

    /**
     * The log left behind by the chakra marker technique. Sized like a log standing on end.
     */
    public static final RegistryObject<EntityType<ChakraMarkerEntity>> CHAKRA_MARKER = register("chakra_marker",
            EntityType.Builder.<ChakraMarkerEntity>of(ChakraMarkerEntity::new, MobCategory.MISC).fireImmune().sized(0.5F, 1.0F).clientTrackingRange(10));

    /**
     * The bank of smoke left by the smoke screen technique. It carries no model and is never summoned,
     * only created by the technique, so it only needs a box wide enough to hold the area it hides.
     */
    public static final RegistryObject<EntityType<SmokeCloudEntity>> SMOKE_CLOUD = register("smoke_cloud",
            EntityType.Builder.<SmokeCloudEntity>of(SmokeCloudEntity::new, MobCategory.MISC).fireImmune().sized(4.5F, 3.0F).clientTrackingRange(10).updateInterval(5));

    /**
     * The thrown smoke bomb. It does nothing itself but leave a cloud, which is the point: the
     * consumable and the technique share one cloud entity.
     */
    public static final RegistryObject<EntityType<SmokeBombEntity>> SMOKE_BOMB = register("smoke_bomb",
            EntityType.Builder.<SmokeBombEntity>of(SmokeBombEntity::new, MobCategory.MISC).sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(10));

    /**
     * The invisible centre of the Chibaku Tensei ball. It carries no model and no hitbox work: the
     * ball around it is drawn by the renderer, so the box only has to be big enough not to matter.
     * The tracking range is the widest the game allows, because the ball is meant to be seen across
     * the whole valley it was cast in.
     */
    public static final RegistryObject<EntityType<ChibakuTenseiEntity>> CHIBAKU_TENSEI = register("chibaku_tensei",
            EntityType.Builder.<ChibakuTenseiEntity>of(ChibakuTenseiEntity::new, MobCategory.MISC).fireImmune().sized(6.0F, 6.0F).clientTrackingRange(32).updateInterval(2));

    /**
     * One visual block of an earth wall climbing out of the ground. It carries no hitbox and is never
     * summoned by the player: the technique spawns it just before the real block is placed.
     */
    public static final RegistryObject<EntityType<EarthWallEntity>> EARTH_WALL = register("earth_wall",
            EntityType.Builder.<EarthWallEntity>of(EarthWallEntity::new, MobCategory.MISC).fireImmune().noSummon().sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(10));

    private static <T extends Entity> RegistryObject<EntityType<T>> register(String key, EntityType.Builder<T> builder) {
        return ENTITIES.register(key, () -> builder.build(key));
    }

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }

    @SubscribeEvent
    public static void entityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(SUBSTITUTION_LOG.get(), SubstitutionLogEntity.createAttributes().build());
        event.put(SHADOW_CLONE.get(), ShadowCloneEntity.createAttributes().build());
        event.put(CHAKRA_MARKER.get(), ChakraMarkerEntity.createAttributes().build());
    }

}
