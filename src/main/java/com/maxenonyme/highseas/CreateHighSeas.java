package com.maxenonyme.highseas;

import com.maxenonyme.highseas.block.BoatSailBlock;
import com.maxenonyme.highseas.block.RudderBlock;
import com.maxenonyme.highseas.block.WindVaneBlock;
import com.maxenonyme.highseas.block.entity.WindVaneBlockEntity;
import com.maxenonyme.highseas.sail.SailWindSystem;
import com.maxenonyme.highseas.system.HighSeasLifecycleHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.maxenonyme.highseas.block.AnchorBlock;
import com.maxenonyme.highseas.block.AnchorItem;
import com.maxenonyme.highseas.block.BoatEngineBlock;
import com.maxenonyme.highseas.block.BoatEngineItem;
import com.maxenonyme.highseas.block.BuoyBlock;
import com.maxenonyme.highseas.block.BuoyItem;
import com.maxenonyme.highseas.block.RudderItem;
import com.maxenonyme.highseas.block.WindVaneItem;
import com.maxenonyme.highseas.block.entity.AnchorBlockEntity;
import com.maxenonyme.highseas.block.entity.BoatEngineBlockEntity;
import com.maxenonyme.highseas.block.entity.BuoyBlockEntity;
import com.maxenonyme.highseas.block.entity.BuoySeatEntity;
import com.maxenonyme.highseas.client.BoatEngineClient;
import com.maxenonyme.highseas.client.BuoyRenderTilt;
import com.maxenonyme.highseas.client.BuoyRideEffects;
import com.maxenonyme.highseas.client.SailRenderClient;
import com.maxenonyme.highseas.client.SeaglideClientHandler;
import com.maxenonyme.highseas.client.SeaglideRushEffects;
import com.maxenonyme.highseas.client.WindVaneClient;
import com.maxenonyme.highseas.config.HighSeasConfig;
import com.maxenonyme.highseas.gui.BoatEngineMenu;
import com.maxenonyme.highseas.gui.EngineTogglePayload;
import com.maxenonyme.highseas.helm.EngineStatePayload;
import com.maxenonyme.highseas.helm.HelmClientHandler;
import com.maxenonyme.highseas.helm.HelmInputPayload;
import com.maxenonyme.highseas.helm.HelmReleasePayload;
import com.maxenonyme.highseas.helm.HelmSeatEntity;
import com.maxenonyme.highseas.helm.HelmServer;
import com.maxenonyme.highseas.helm.ThrottleCapPayload;
import com.maxenonyme.highseas.item.OarItem;
import com.maxenonyme.highseas.item.SeaglideAttackGuard;
import com.maxenonyme.highseas.item.SeaglideDrainPayload;
import com.maxenonyme.highseas.item.SeaglideImpactPayload;
import com.maxenonyme.highseas.item.SeaglideItem;
import com.maxenonyme.highseas.oar.OarAnimSyncPayload;
import com.maxenonyme.highseas.oar.OarClientHandler;
import com.maxenonyme.highseas.oar.OarPropulsionSystem;
import com.maxenonyme.highseas.oar.OarRowPayload;
import com.maxenonyme.highseas.sail.FurlSyncPayload;
import com.maxenonyme.highseas.sail.SailCollisionSystem;
import com.maxenonyme.highseas.sail.SailFurlHandler;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(CreateHighSeas.MOD_ID)
public class CreateHighSeas {
    public static final String MOD_ID = "create_high_seas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
            .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister
            .create(BuiltInRegistries.SOUND_EVENT, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
            .create(BuiltInRegistries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
            .create(BuiltInRegistries.MENU, MOD_ID);

    public static final Supplier<MenuType<BoatEngineMenu>> BOAT_ENGINE_MENU =
            MENUS.register("boat_engine",
                    () -> IMenuTypeExtension
                            .create(BoatEngineMenu::new));

    public static final Supplier<EntityType<HelmSeatEntity>> HELM_SEAT =
            ENTITY_TYPES.register("helm_seat",
                    () -> EntityType.Builder
                            .<HelmSeatEntity>of(
                                    HelmSeatEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25f, 0.35f)
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build("helm_seat"));

    public static final Supplier<EntityType<BuoySeatEntity>> BUOY_SEAT =
            ENTITY_TYPES.register("buoy_seat",
                    () -> EntityType.Builder
                            .<BuoySeatEntity>of(
                                    BuoySeatEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25f, 0.35f)
                            .noSummon()
                            .fireImmune()
                            .clientTrackingRange(10)
                            .updateInterval(20)
                            .build("buoy_seat"));

    public static final Supplier<SoundEvent> BOAT_ENGINE_IDLE_SOUND = SOUND_EVENTS.register(
            "boat_engine_idle",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "boat_engine_idle")));
    public static final Supplier<SoundEvent> BOAT_ENGINE_RUNNING_SOUND = SOUND_EVENTS.register(
            "boat_engine_running",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "boat_engine_running")));

    public static final Supplier<Block> WIND_VANE = BLOCKS.register("wind_vane",
            () -> new WindVaneBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));
    public static final Supplier<Item> WIND_VANE_ITEM = ITEMS.register("wind_vane",
            () -> new WindVaneItem(WIND_VANE.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<WindVaneBlockEntity>> WIND_VANE_BE = BLOCK_ENTITIES.register(
            "wind_vane",
            () -> BlockEntityType.Builder.of(WindVaneBlockEntity::new, WIND_VANE.get()).build(null));

    public static final Supplier<Block> RUDDER = BLOCKS.register("rudder",
            () -> new RudderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)));
    public static final Supplier<Item> RUDDER_ITEM = ITEMS.register("rudder",
            () -> new RudderItem(RUDDER.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<CopycatBlockEntity>> RUDDER_COPYCAT_BE = BLOCK_ENTITIES.register(
            "rudder_copycat",
            () -> BlockEntityType.Builder.of((pos, state) -> new CopycatBlockEntity(CreateHighSeas.RUDDER_COPYCAT_BE.get(), pos, state), RUDDER.get()).build(null));

    public static final Supplier<Block> ANCHOR = BLOCKS.register("anchor",
            () -> new AnchorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
    public static final Supplier<Item> ANCHOR_ITEM = ITEMS.register("anchor",
            () -> new AnchorItem(ANCHOR.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<AnchorBlockEntity>> ANCHOR_BE = BLOCK_ENTITIES.register(
            "anchor",
            () -> BlockEntityType.Builder.of((pos, state) -> new AnchorBlockEntity(CreateHighSeas.ANCHOR_BE.get(), pos, state), ANCHOR.get()).build(null));

    public static final Supplier<Item> SEAGLIDE = ITEMS.register("seaglide",
            () -> new SeaglideItem(new Item.Properties().stacksTo(1)));

    public static final Supplier<Item> OAR_OF_BOAT = ITEMS.register("oar_of_boat",
            () -> new OarItem(new Item.Properties()));

    public static final Supplier<Block> BOAT_ENGINE = BLOCKS.register("boat_engine",
            () -> new BoatEngineBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));
    public static final Supplier<Item> BOAT_ENGINE_ITEM = ITEMS.register("boat_engine",
            () -> new BoatEngineItem(BOAT_ENGINE.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<BoatEngineBlockEntity>> BOAT_ENGINE_BE = BLOCK_ENTITIES.register(
            "boat_engine",
            () -> BlockEntityType.Builder.of(BoatEngineBlockEntity::new, BOAT_ENGINE.get()).build(null));

    public static final Supplier<Block> BUOY = BLOCKS.register("buoy",
            () -> new BuoyBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));
    public static final Supplier<Item> BUOY_ITEM = ITEMS.register("buoy",
            () -> new BuoyItem(BUOY.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<BuoyBlockEntity>> BUOY_BE = BLOCK_ENTITIES
            .register("buoy",
                    () -> BlockEntityType.Builder
                            .of(BuoyBlockEntity::new, BUOY.get()).build(null));

    public static final Map<DyeColor, Supplier<Block>> SAILS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            SAILS.put(color, BLOCKS.register(color.getSerializedName() + "_sail",
                    () -> new BoatSailBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STRIPPED_SPRUCE_WOOD)
                            .sound(SoundType.SCAFFOLDING)
                            .noOcclusion(), color)));
        }
    }

    public static final Supplier<Item> SAIL_ITEM = ITEMS.register("white_sail",
            () -> new BlockItem(SAILS.get(DyeColor.WHITE).get(), new Item.Properties()));

    public CreateHighSeas(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER,
                HighSeasConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT,
                HighSeasConfig.CLIENT_SPEC);
        modEventBus.addListener(HighSeasConfig::onConfig);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(SailWindSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(BoatBuoyancySystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(BoatBuoyancySystem::onPhysicsTick);
        NeoForge.EVENT_BUS.addListener(BuoyFloatSystem::onPostPhysicsTick);
        NeoForge.EVENT_BUS.addListener(HelmServer::onServerTick);
        NeoForge.EVENT_BUS.addListener(OarPropulsionSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(SailCollisionSystem::onServerTick);
        NeoForge.EVENT_BUS.addListener(SailFurlHandler::onRightClick);
        NeoForge.EVENT_BUS.addListener(SailFurlHandler::onLogin);
        NeoForge.EVENT_BUS.addListener(SailFurlHandler::onServerStarted);
        NeoForge.EVENT_BUS.addListener(BoatEngineBlock::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(SeaglideAttackGuard::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(SeaglideAttackGuard::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(HighSeasLifecycleHandler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(HighSeasLifecycleHandler::onLevelUnload);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (container, parent) -> new ConfigurationScreen(container,
                            parent));
            SailRenderClient.init(modEventBus);
            WindVaneClient.init(modEventBus);
            BoatEngineClient.init(modEventBus);
            NeoForge.EVENT_BUS.addListener(OarClientHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(OarClientHandler::onRenderHand);
            NeoForge.EVENT_BUS.addListener(HelmClientHandler::onClientTickPre);
            NeoForge.EVENT_BUS.addListener(HelmClientHandler::onClientTickPost);
            NeoForge.EVENT_BUS.addListener(HelmClientHandler::onRenderHand);
            NeoForge.EVENT_BUS.addListener(HelmClientHandler::onLevelUnload);
            NeoForge.EVENT_BUS.addListener(SeaglideClientHandler::onClientTickPre);
            NeoForge.EVENT_BUS.addListener(SeaglideClientHandler::onClientTick);
            NeoForge.EVENT_BUS.addListener(SeaglideClientHandler::onAttackInput);
            NeoForge.EVENT_BUS.addListener(SeaglideClientHandler::onPlaySound);
            NeoForge.EVENT_BUS.addListener(SeaglideRushEffects::onCameraAngles);
            NeoForge.EVENT_BUS.addListener(SeaglideRushEffects::onRenderGui);
            NeoForge.EVENT_BUS.addListener(BuoyRenderTilt::onPre);
            NeoForge.EVENT_BUS.addListener(BuoyRenderTilt::onPost);
            NeoForge.EVENT_BUS.addListener(BuoyRideEffects::onClientTick);
            NeoForge.EVENT_BUS.addListener(BuoyRideEffects::onCameraAngles);
            NeoForge.EVENT_BUS.addListener(BuoyRideEffects::onRenderGui);
            NeoForge.EVENT_BUS.addListener(BuoyRideEffects::onRenderHand);
            NeoForge.EVENT_BUS.addListener(BuoyRideEffects::onRenderLevel);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(this::registerToSimulatedTab);
        event.enqueueWork(BoatSailBlock::registerMovementCheck);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                FurlSyncPayload.TYPE,
                FurlSyncPayload.CODEC,
                FurlSyncPayload::handle);
        registrar.playToServer(
                OarRowPayload.TYPE,
                OarRowPayload.CODEC,
                OarRowPayload::handle);
        registrar.playToClient(
                OarAnimSyncPayload.TYPE,
                OarAnimSyncPayload.CODEC,
                OarAnimSyncPayload::handle);
        registrar.playToServer(
                HelmInputPayload.TYPE,
                HelmInputPayload.CODEC,
                HelmInputPayload::handle);
        registrar.playToServer(
                HelmReleasePayload.TYPE,
                HelmReleasePayload.CODEC,
                HelmReleasePayload::handle);
        registrar.playToServer(
                ThrottleCapPayload.TYPE,
                ThrottleCapPayload.CODEC,
                ThrottleCapPayload::handle);
        registrar.playToServer(
                EngineTogglePayload.TYPE,
                EngineTogglePayload.CODEC,
                EngineTogglePayload::handle);
        registrar.playToServer(
                SeaglideDrainPayload.TYPE,
                SeaglideDrainPayload.CODEC,
                SeaglideDrainPayload::handle);
        registrar.playToServer(
                SeaglideImpactPayload.TYPE,
                SeaglideImpactPayload.CODEC,
                SeaglideImpactPayload::handle);
        registrar.playToClient(
                EngineStatePayload.TYPE,
                EngineStatePayload.CODEC,
                EngineStatePayload::handle);
    }

    @SuppressWarnings("unchecked")
    private void registerToSimulatedTab() {
        try {
            Class<?> regClass = Class.forName("dev.simulated_team.simulated.registrate.SimulatedRegistrate");
            List<Supplier<Item>> tabItems = (List<Supplier<Item>>) regClass.getField("TAB_ITEMS").get(null);
            Map<ResourceLocation, ResourceLocation> itemToSection = (Map<ResourceLocation, ResourceLocation>) regClass
                    .getField("ITEM_TO_SECTION").get(null);

            ResourceLocation highSeasSection = ResourceLocation.fromNamespaceAndPath(MOD_ID, "high_seas");
            tabItems.add(WIND_VANE_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "wind_vane"), highSeasSection);
            tabItems.add(RUDDER_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "rudder"), highSeasSection);
            tabItems.add(ANCHOR_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "anchor"), highSeasSection);
            tabItems.add(OAR_OF_BOAT::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oar_of_boat"), highSeasSection);
            tabItems.add(BOAT_ENGINE_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "boat_engine"), highSeasSection);
            tabItems.add(SEAGLIDE::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "seaglide"), highSeasSection);
            tabItems.add(BUOY_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "buoy"), highSeasSection);
            tabItems.add(SAIL_ITEM::get);
            itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "white_sail"), highSeasSection);
        } catch (Exception ignored) {
        }
    }
}
