package com.sekwah.narutomod.item;

import com.sekwah.narutomod.item.armor.NarutoArmorItem;
import com.sekwah.narutomod.item.armor.NarutoArmorMaterial;
import com.sekwah.narutomod.item.weapons.ExplosiveKunaiItem;
import com.sekwah.narutomod.item.weapons.KunaiItem;
import com.sekwah.narutomod.item.weapons.SenbonItem;
import com.sekwah.narutomod.item.weapons.ShurikenItem;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.sekwah.narutomod.NarutoMod.MOD_ID;

public class NarutoItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> MODEL_TEST = ITEMS.register("model_test", ()
            -> new Item(new Item.Properties()));

    // Weapons
    public static final RegistryObject<Item> KUNAI = ITEMS.register("kunai", ()
            -> new KunaiItem(new Item.Properties()));

    public static final RegistryObject<Item> SENBON = ITEMS.register("senbon", ()
            -> new SenbonItem(new Item.Properties()));

    public static final RegistryObject<Item> EXPLOSIVE_KUNAI = ITEMS.register("explosive_kunai", ()
            -> new ExplosiveKunaiItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> SHURIKEN = ITEMS.register("shuriken", ()
            -> new ShurikenItem(new Item.Properties()));


    // Armor

    /**
     * Bonuses the armour pieces carry, see {@code events/NarutoArmorEvents}. They are ninja kit, so
     * what they give is chakra and the body, not armour points.
     */
    /** A village headband helps its ninja keep hold of their chakra. */
    private static final NarutoArmorItem.Bonus HEADBAND_BONUS = new NarutoArmorItem.Bonus(0.0f, 0.0f, 0.4f);
    /** An ANBU mask is worn by shinobi trained to hold far more than they show. */
    private static final NarutoArmorItem.Bonus ANBU_MASK_BONUS = new NarutoArmorItem.Bonus(75.0f, 0.0f, 0.0f);
    /** The standard flak jacket takes a hit so its wearer does not have to. */
    private static final NarutoArmorItem.Bonus FLAK_BONUS = new NarutoArmorItem.Bonus(0.0f, 2.0f, 0.0f);
    /** The jonin flak is built over the same idea with better plate. */
    private static final NarutoArmorItem.Bonus JONIN_FLAK_BONUS = new NarutoArmorItem.Bonus(0.0f, 4.0f, 0.0f);
    /** ANBU field armour: a little less plate than a flak, a little more chakra discipline. */
    private static final NarutoArmorItem.Bonus ANBU_ARMOR_BONUS = new NarutoArmorItem.Bonus(40.0f, 2.0f, 0.2f);
    /** The Akatsuki cloak: S-rank chakra, and plenty of it. */
    private static final NarutoArmorItem.Bonus AKATSUKI_BONUS = new NarutoArmorItem.Bonus(120.0f, 0.0f, 0.5f);

    public static final RegistryObject<Item> RED_ANBU_MASK = ITEMS.register("red_anbu_mask", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.ANBU_MAT, ArmorItem.Type.HELMET, new Item.Properties())
            .setBonus(ANBU_MASK_BONUS)
            .setShouldHideNameplate(true));

    public static final RegistryObject<Item> YELLOW_ANBU_MASK = ITEMS.register("yellow_anbu_mask", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.ANBU_MAT, ArmorItem.Type.HELMET, new Item.Properties())
            .setBonus(ANBU_MASK_BONUS)
            .setShouldHideNameplate(true));

    public static final RegistryObject<Item> GREEN_ANBU_MASK = ITEMS.register("green_anbu_mask", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.ANBU_MAT, ArmorItem.Type.HELMET, new Item.Properties())
            .setBonus(ANBU_MASK_BONUS)
            .setShouldHideNameplate(true));

    public static final RegistryObject<Item> BLUE_ANBU_MASK = ITEMS.register("blue_anbu_mask", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.ANBU_MAT, ArmorItem.Type.HELMET, new Item.Properties())
            .setBonus(ANBU_MASK_BONUS)
            .setShouldHideNameplate(true));

    public static final RegistryObject<Item> MIST_ANBU_MASK = ITEMS.register("mist_anbu_mask", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.ANBU_MAT, ArmorItem.Type.HELMET, new Item.Properties())
            .setBonus(ANBU_MASK_BONUS)
            .setShouldHideNameplate(true));

    public static final RegistryObject<Item> FLAK_JACKET_NEW = ITEMS.register("flak_jacket_new", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.FLAK_MAT, ArmorItem.Type.CHESTPLATE, new Item.Properties())
            .setBonus(FLAK_BONUS));

    public static final RegistryObject<Item> FLAK_JACKET = ITEMS.register("flak_jacket", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.FLAK_STRONGER_MAT, ArmorItem.Type.CHESTPLATE, new Item.Properties())
            .setBonus(JONIN_FLAK_BONUS));

    public static final RegistryObject<Item> ANBU_ARMOR = ITEMS.register("anbu_armor", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.FLAK_STRONGER_MAT, ArmorItem.Type.CHESTPLATE, new Item.Properties())
            .setBonus(ANBU_ARMOR_BONUS));

    public static final RegistryObject<Item> AKATSUKI_CLOAK = ITEMS.register("akatsuki_cloak", ()
            -> new NarutoArmorItem(NarutoArmorMaterial.CHARACTER_CLOTHES, ArmorItem.Type.CHESTPLATE, new Item.Properties())
            .setBonus(AKATSUKI_BONUS));

    public static final RegistryObject<Item> LONELY_MARCH = ITEMS.register("lonely_march", ()
            -> new RecordItem(41, NarutoSounds.LONELY_MARCH, new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 46 * 20));

    public static final RegistryObject<Item> FABRIC = ITEMS.register("fabric", ()
            -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FABRIC_REINFORCED = ITEMS.register("fabric_reinforced", ()
            -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FABRIC_REINFORCED_GREEN = ITEMS.register("fabric_reinforced_green", ()
            -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FABRIC_REINFORCED_BLACK = ITEMS.register("fabric_reinforced_black", ()
            -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ARMOR_PLATE = ITEMS.register("armor_plate", ()
            -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ARMOR_PLATE_GREEN = ITEMS.register("armor_plate_green", ()
            -> new Item(new Item.Properties()));

    /**
     * Field rations: chakra and stamina back instantly, plus a short burst of speed and haste.
     */
    public static final RegistryObject<Item> MILITARY_PILLS = ITEMS.register("military_pills",
            () -> new MilitaryPillItem(new Item.Properties().stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(6).saturationMod(0.6f).build())));

    /**
     * Ichiraku's finest. A proper meal rather than a snack: more than a full hunger bar's worth in
     * one bowl, and enough saturation to keep the caster topped up for a while afterwards.
     */
    public static final RegistryObject<Item> RAMEN = ITEMS.register("ramen",
            () -> new Item(new Item.Properties().stacksTo(16)
                    .food(new FoodProperties.Builder().nutrition(8).saturationMod(1.2f).build())));

    /**
     * The talisman every clone technique burns, see
     * {@code ShadowCloneJutsuAbility#handleCost(Player, INinjaData, int)}. One per summon, no matter
     * how many clones come out of it.
     */
    public static final RegistryObject<Item> CHAKRA_SEAL = ITEMS.register("chakra_seal",
            () -> new Item(new Item.Properties()));

    /**
     * The Worker's Seal: used on a block, it leaves a shadow clone behind to farm the ground around
     * that spot and hand the harvest over when it pops, see {@link WorkerSealItem}.
     */
    public static final RegistryObject<Item> WORKER_SEAL = ITEMS.register("worker_seal",
            () -> new WorkerSealItem(new Item.Properties()));

    /**
     * A store of chakra that binds itself to the first ninja to use it, see
     * {@link ChakraBatteryItem}.
     */
    public static final RegistryObject<Item> CHAKRA_BATTERY = ITEMS.register("chakra_battery",
            () -> new ChakraBatteryItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> KATANA = ITEMS.register("katana", ()
            -> new SwordItem(NinjaTier.KATANA, 3, -1.95F, new Item.Properties()));

    /**
     * Samehada: quick enough for a chain to be kept alive, and it takes chakra off whatever it cuts.
     */
    public static final RegistryObject<Item> SAMEHADA = ITEMS.register("samehada",
            () -> new ComboSwordItem(NinjaTier.KATANA, 4, -2.4F, new Item.Properties().stacksTo(1),
                    0.5f, 20, 20 * 4, 1.5f));

    /**
     * Kubikiribocho: slow, heavy, and the chain is worth far more per step.
     */
    public static final RegistryObject<Item> KUBIKIRIBOCHO = ITEMS.register("kubikiribocho",
            () -> new ComboSwordItem(NinjaTier.KATANA, 6, -3.2F, new Item.Properties().stacksTo(1),
                    0.75f, 20, 20 * 5, 0.0f));

    // Ninja Masks

    private static RegistryObject<Item> createHeadband(String itemName) {
        return ITEMS.register(itemName, ()
                -> new NarutoArmorItem(NarutoArmorMaterial.HEADBAND, ArmorItem.Type.HELMET, new Item.Properties())
                .setBonus(HEADBAND_BONUS));
    }

    //    BLANK(0, "blankBlueHeadBand", "headband_blank);"),
    public static final RegistryObject<Item> HEADBAND_BLUE = createHeadband("headband_blue");
    //    BLANK_BLACK(1, "blankBlackHeadBand", "headband_black);"),
    public static final RegistryObject<Item> HEADBAND_BLACK = createHeadband("headband_black");
    //    BLANK_RED(2, "blankRedHeadBand", "headband_kred);"),
    public static final RegistryObject<Item> HEADBAND_RED = createHeadband("headband_red");
    //    CUSTARD(4, "custardHeadband", "headband_custard);"),
    public static final RegistryObject<Item> HEADBAND_CUSTARD = createHeadband("headband_custard");


    public static final RegistryObject<Item> HEADBAND_LEAF = createHeadband("headband_leaf");
    public static final RegistryObject<Item> HEADBAND_LEAF_SCRATCHED = createHeadband("headband_leaf_scratched");
    public static final RegistryObject<Item> HEADBAND_LEAF_BLACK = createHeadband("headband_leaf_black");
    public static final RegistryObject<Item> HEADBAND_LEAF_BLACK_SCRATCHED = createHeadband("headband_leaf_black_scratched");

    public static final RegistryObject<Item> HEADBAND_ROCK = createHeadband("headband_rock");
    public static final RegistryObject<Item> HEADBAND_ROCK_SCRATCHED = createHeadband("headband_rock_scratched");

    public static final RegistryObject<Item> HEADBAND_SAND = createHeadband("headband_sand");
    public static final RegistryObject<Item> HEADBAND_SAND_SCRATCHED = createHeadband("headband_sand_scratched");

    public static final RegistryObject<Item> HEADBAND_SOUND = createHeadband("headband_sound");
    public static final RegistryObject<Item> HEADBAND_SOUND_SCRATCHED = createHeadband("headband_sound_scratched");

    public static final RegistryObject<Item> HEADBAND_MIST = createHeadband("headband_mist");
    public static final RegistryObject<Item> HEADBAND_MIST_SCRATCHED = createHeadband("headband_mist_scratched");

    public static final RegistryObject<Item> HEADBAND_WATERFALL = createHeadband("headband_waterfall");
    public static final RegistryObject<Item> HEADBAND_WATERFALL_SCRATCHED = createHeadband("headband_waterfall_scratched");

    public static final RegistryObject<Item> HEADBAND_CLOUD = createHeadband("headband_cloud");
    public static final RegistryObject<Item> HEADBAND_CLOUD_SCRATCHED = createHeadband("headband_cloud_scratched");

    public static final RegistryObject<Item> HEADBAND_RAIN = createHeadband("headband_rain");
    public static final RegistryObject<Item> HEADBAND_RAIN_SCRATCHED = createHeadband("headband_rain_scratched");

    public static final RegistryObject<Item> HEADBAND_GRASS = createHeadband("headband_grass");
    public static final RegistryObject<Item> HEADBAND_GRASS_SCRATCHED = createHeadband("headband_grass_scratched");


    public static final RegistryObject<Item> HEADBAND_PRIDE = createHeadband("headband_pride");
    public static final RegistryObject<Item> HEADBAND_YOUTUBE = createHeadband("headband_youtube");
    public static final RegistryObject<Item> HEADBAND_LAVA = createHeadband("headband_lava");


    // Materials

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

}
