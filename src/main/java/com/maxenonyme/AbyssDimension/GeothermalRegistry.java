package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.block.BurningSandBlock;
import com.maxenonyme.AbyssDimension.block.BurningWoodBlock;
import com.maxenonyme.AbyssDimension.block.GeothermalVentBlock;
import com.maxenonyme.AbyssDimension.block.MagmaFlowBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

public class GeothermalRegistry {

    public static final Supplier<Block> GEOTHERMAL_VENT = vent("geothermal_vent", Blocks.STONE);
    public static final Supplier<Block> FOAM_ROCK_GEOTHERMAL_VENT = vent("foam_rock_geothermal_vent", Blocks.MOSSY_COBBLESTONE);
    public static final Supplier<Block> DEEPSLATE_GEOTHERMAL_VENT = vent("deepslate_geothermal_vent", Blocks.DEEPSLATE);

    public static final Supplier<Block> MAGMA = CreateAbyss.BLOCKS.register("magma",
            () -> new MagmaFlowBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MAGMA_BLOCK)
                    .emissiveRendering((state, level, pos) -> false)
                    .lightLevel(state -> 10)));

    public static final Supplier<Block> BURNING_SAND = CreateAbyss.BLOCKS.register("burning_sand",
            () -> new BurningSandBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SAND).sound(SoundType.SAND).strength(0.3f)));

    public static final Supplier<Block> BURNING_WOOD = CreateAbyss.BLOCKS.register("burning_wood",
            () -> new BurningWoodBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).sound(BurningWoodBlock.CHARRED).strength(1.0f)));

    public static final Supplier<Item> GEOTHERMAL_VENT_ITEM = item("geothermal_vent", GEOTHERMAL_VENT);
    public static final Supplier<Item> FOAM_ROCK_GEOTHERMAL_VENT_ITEM = item("foam_rock_geothermal_vent", FOAM_ROCK_GEOTHERMAL_VENT);
    public static final Supplier<Item> DEEPSLATE_GEOTHERMAL_VENT_ITEM = item("deepslate_geothermal_vent", DEEPSLATE_GEOTHERMAL_VENT);

    public static final Supplier<Item> MAGMA_ITEM = item("magma", MAGMA);
    public static final Supplier<Item> BURNING_SAND_ITEM = item("burning_sand", BURNING_SAND);
    public static final Supplier<Item> BURNING_WOOD_ITEM = item("burning_wood", BURNING_WOOD);

    private static Supplier<Block> vent(String name, Block rock) {
        return CreateAbyss.BLOCKS.register(name,
                () -> new GeothermalVentBlock(BlockBehaviour.Properties.ofFullCopy(rock).noOcclusion()));
    }

    private static Supplier<Item> item(String name, Supplier<Block> block) {
        return CreateAbyss.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void init() {
    }
}
