package com.maxenonyme.AbyssDimension;

import com.mojang.logging.LogUtils;
import com.maxenonyme.AbyssDimension.entities.EntityRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import java.util.function.Supplier;

@Mod(CreateAbyss.MOD_ID)
public class CreateAbyss {
    public static final String MOD_ID = "create_abyss";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister
            .create(net.minecraft.core.registries.BuiltInRegistries.DENSITY_FUNCTION_TYPE, MOD_ID);

    public static final Supplier<com.mojang.serialization.MapCodec<com.maxenonyme.createsubmarine.abyss.AbyssDepthMultiplier>> ABYSS_DEPTH_MULTIPLIER = DENSITY_FUNCTIONS
            .register("abyss_depth_multiplier",
                    () -> com.maxenonyme.createsubmarine.abyss.AbyssDepthMultiplier.CODEC);

    public static final Supplier<com.mojang.serialization.MapCodec<com.maxenonyme.createsubmarine.worldgen.SeafloorHeightFunction>> SEAFLOOR_HEIGHT = DENSITY_FUNCTIONS
            .register("seafloor_height",
                    () -> com.maxenonyme.createsubmarine.worldgen.SeafloorHeightFunction.CODEC);

    public static final Supplier<com.mojang.serialization.MapCodec<com.maxenonyme.createsubmarine.worldgen.SeafloorNoiseFunction>> SEAFLOOR_NOISE = DENSITY_FUNCTIONS
            .register("seafloor_noise",
                    () -> com.maxenonyme.createsubmarine.worldgen.SeafloorNoiseFunction.CODEC);

    public static final Supplier<CreativeModeTab> ABYSS_TAB = CREATIVE_MODE_TABS.register("abyss_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_abyss.abyss_tab"))
                    .icon(() -> new ItemStack(EntityRegistry.AMPHISTIUM_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(EntityRegistry.AMPHISTIUM_SPAWN_EGG.get());
                        output.accept(EntityRegistry.COOKIECUTTER_SHARK_SPAWN_EGG.get());
                        output.accept(EntityRegistry.MAGMATIC_SNAIL_SPAWN_EGG.get());
                        output.accept(EntityRegistry.ISOPOD_SPAWN_EGG.get());
                        output.accept(EntityRegistry.SIZE_2_ISOPOD_SPAWN_EGG.get());
                        output.accept(EntityRegistry.SIZE_3_ISOPOD_SPAWN_EGG.get());
                        output.accept(EntityRegistry.SIZE_4_ISOPOD_SPAWN_EGG.get());
                        output.accept(EntityRegistry.ABYSSAL_CUTTLEFISH_SPAWN_EGG.get());
                        output.accept(EntityRegistry.PELICAN_EEL_SPAWN_EGG.get());
                    })
                    .build());

    public CreateAbyss(IEventBus modEventBus, ModContainer modContainer) {
        // Abyss still in development: content only exists in the dev environment
        if (net.neoforged.fml.loading.FMLEnvironment.production) {
            LOGGER.info("Create Abyss is in development, content disabled in production");
            return;
        }
        LianaRegistry.init();
        EntityRegistry.init(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        DENSITY_FUNCTIONS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(com.maxenonyme.AbyssDimension.system.LianaLODOptimizer::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.maxenonyme.AbyssDimension.system.SubmarineLianaCommand::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.maxenonyme.AbyssDimension.system.SubmarineLianaCommand::register);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            CreateAbyssClient.init(modEventBus);
        }
    }

    private void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToServer(
                com.maxenonyme.AbyssDimension.network.StruggleSharkPayload.TYPE,
                com.maxenonyme.AbyssDimension.network.StruggleSharkPayload.CODEC,
                com.maxenonyme.AbyssDimension.network.StruggleSharkPayload::handle);
    }
}
