package com.sekwah.narutomod.block;

import com.sekwah.narutomod.block.weapons.PaperBombBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import static com.sekwah.narutomod.NarutoMod.MOD_ID;

public class NarutoBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    // TODO find a way to apply these to the paper bomb
    //public static final Material UNSTABLE_EXPLOSIVE = (new Material.Builder(MaterialColor.FIRE)).noCollider().flammable().notSolidBlocking().nonSolid().destroyOnPush().build();

    public static final RegistryObject<Block> PAPER_BOMB = BLOCKS.register("paper_bomb",
            () -> {
                BlockBehaviour.Properties blockBehavior = BlockBehaviour.Properties.of()
                        .noCollission()
                        .instabreak()
                        .strength(0.5F)
                        .sound(SoundType.CROP);
        return new PaperBombBlock(blockBehavior);
            });

    public static final RegistryObject<Item> ITEM_PAPER_BOMB = BLOCK_ITEMS.register("paper_bomb", ()
            -> new BlockItem(PAPER_BOMB.get(), new Item.Properties()));

    public static final RegistryObject<Block> BONSAI_TREE = BLOCKS.register("bonsai_tree",
            () -> new RotatableDecorativeBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.CROP)
                    , Block.box(2.0D, 0.0D, 2.0D, 14.0D, 8.0D, 14.0D)));

    public static final RegistryObject<Item> ITEM_BONSAI_TREE = BLOCK_ITEMS.register("bonsai_tree", ()
            -> new BlockItem(BONSAI_TREE.get(), new Item.Properties()));

    /**
     * A ward laid on the ground: it chimes when a hostile mob enters its radius, and can optionally
     * keep spawns out of the perimeter. See {@link SentrySealBlock}.
     */
    public static final RegistryObject<Block> SENTRY_SEAL = BLOCKS.register("sentry_seal",
            () -> new SentrySealBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .strength(0.2F)
                    .sound(SoundType.CROP)
                    .noOcclusion()));

    public static final RegistryObject<Item> ITEM_SENTRY_SEAL = BLOCK_ITEMS.register("sentry_seal",
            () -> new BlockItem(SENTRY_SEAL.get(), new Item.Properties()));

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<BlockEntityType<SentrySealBlockEntity>> SENTRY_SEAL_ENTITY =
            BLOCK_ENTITIES.register("sentry_seal", () -> BlockEntityType.Builder
                    .of(SentrySealBlockEntity::new, SENTRY_SEAL.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
    }
}
