package com.maxenonyme.AbyssDimension;

import com.maxenonyme.AbyssDimension.client.MagmaFlowSpriteSource;
import com.mojang.logging.LogUtils;
import com.maxenonyme.AbyssDimension.entities.EntityRegistry;
import com.maxenonyme.AbyssDimension.experimental.worldgen.ExperimentalWorldgen;
import com.maxenonyme.AbyssDimension.network.StruggleSharkPayload;
import com.maxenonyme.AbyssDimension.system.GeothermalBreathing;
import com.maxenonyme.AbyssDimension.system.LianaLODOptimizer;
import com.maxenonyme.AbyssDimension.system.SubmarineLianaCommand;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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
    public static final boolean DEV_ONLY = false;

    public static final Supplier<Item> MUSIC_DISC_SEAFOAM = musicDisc("seafoam");
    public static final Supplier<Item> MUSIC_DISC_SNELLS_WINDOW = musicDisc("snells_window");

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Supplier<CreativeModeTab> ABYSS_TAB = CREATIVE_MODE_TABS.register("abyss_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_abyss.abyss_tab"))
                    .icon(() -> new ItemStack(EntityRegistry.AMPHISTIUM_SPAWN_EGG.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(EntityRegistry.AMPHISTIUM_SPAWN_EGG.get());
                        output.accept(EntityRegistry.COOKIECUTTER_SHARK_SPAWN_EGG.get());
                        output.accept(EntityRegistry.MAGMATIC_SNAIL_SPAWN_EGG.get());
                        output.accept(EntityRegistry.ISOPOD_SPAWN_EGG.get());
                        output.accept(EntityRegistry.ABYSSAL_CUTTLEFISH_SPAWN_EGG.get());
                        output.accept(EntityRegistry.PELICAN_EEL_SPAWN_EGG.get());
                        output.accept(GeothermalRegistry.GEOTHERMAL_VENT_ITEM.get());
                        output.accept(GeothermalRegistry.FOAM_ROCK_GEOTHERMAL_VENT_ITEM.get());
                        output.accept(GeothermalRegistry.DEEPSLATE_GEOTHERMAL_VENT_ITEM.get());
                        output.accept(GeothermalRegistry.MAGMA_ITEM.get());
                        output.accept(GeothermalRegistry.BURNING_SAND_ITEM.get());
                        output.accept(GeothermalRegistry.BURNING_WOOD_ITEM.get());
                        output.accept(MUSIC_DISC_SEAFOAM.get());
                        output.accept(MUSIC_DISC_SNELLS_WINDOW.get());
                        if (CreateSubmarine.SUBMARINE_STAFF_ENABLED)
                            output.accept(CreateSubmarine.SUBMARINE_STAFF.get());
                    })
                    .build());

    public CreateAbyss(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(MagmaFlowSpriteSource::register);
        }
        modEventBus.addListener(this::registerPayloads);
        if (!enabled()) {
            LOGGER.info("Create Abyss is in development, content disabled in production");
            return;
        }
        LianaRegistry.init();
        GeothermalRegistry.init();
        EntityRegistry.init(modEventBus);
        ExperimentalWorldgen.init(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(LianaLODOptimizer::onServerTick);
        NeoForge.EVENT_BUS.addListener(GeothermalBreathing::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SubmarineLianaCommand::onServerTick);
        NeoForge.EVENT_BUS.addListener(SubmarineLianaCommand::register);

        if (FMLEnvironment.dist.isClient()) {
            CreateAbyssClient.init(modEventBus);
        }
    }

    private static Supplier<Item> musicDisc(String name) {
        ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
        return ITEMS.register("music_disc_" + name,
                () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song)));
    }

    public static boolean enabled() {
        return !DEV_ONLY || !FMLEnvironment.production;
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID).optional();
        registrar.playToServer(
                StruggleSharkPayload.TYPE,
                StruggleSharkPayload.CODEC,
                StruggleSharkPayload::handle);
    }
}
