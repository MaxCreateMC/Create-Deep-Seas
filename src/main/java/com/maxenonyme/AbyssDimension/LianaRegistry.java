package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.block.CreepvineSeedBlock;
import com.maxenonyme.AbyssDimension.block.SubmarineLianaBlock;
import com.maxenonyme.AbyssDimension.block.entity.SubmarineLianaBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public class LianaRegistry {
    public static final Supplier<Block> LIANA_BLOCK = CreateAbyss.BLOCKS.register("submarine_liana",
            () -> new SubmarineLianaBlock());

    public static final Supplier<BlockEntityType<SubmarineLianaBlockEntity>> LIANA_BE = CreateAbyss.BLOCK_ENTITIES.register("submarine_liana",
            () -> BlockEntityType.Builder.of(SubmarineLianaBlockEntity::new, LIANA_BLOCK.get()).build(null));

    public static final Supplier<Block> CREEPVINE_SEED = CreateAbyss.BLOCKS.register("creepvine_seed",
            () -> new CreepvineSeedBlock());

    public static final Supplier<Item> CREEPVINE_SEED_ITEM = CreateAbyss.ITEMS.register("creepvine_seed",
            () -> new BlockItem(CREEPVINE_SEED.get(), new Item.Properties()));

    public static void init() {
    }
}
