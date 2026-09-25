package com.maxenonyme.createsubmarine;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import com.maxenonyme.createsubmarine.submarine.block.*;
import com.maxenonyme.createsubmarine.submarine.block.entity.*;
import com.maxenonyme.createsubmarine.submarine.effect.SuffocationEffect;
import com.maxenonyme.createsubmarine.submarine.system.*;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.network.chat.Component;
import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import net.neoforged.fml.config.ModConfig;

@Mod(CreateSubmarine.MOD_ID)
public class CreateSubmarine {
        public static final String MOD_ID = "create_submarine";
        public static final DeferredRegister<ForceGroup> FORCE_GROUP_REGISTER = DeferredRegister
                        .create(ForceGroups.REGISTRY_KEY, MOD_ID);
        public static final Supplier<ForceGroup> BALLAST_FORCE_GROUP = FORCE_GROUP_REGISTER.register(
                        "ballast",
                        () -> new ForceGroup(
                                        Component.translatable("create_submarine.force_group.ballast"),
                                        Component.translatable("create_submarine.force_group.ballast.description"),
                                        0x00008B,
                                        true));
        public static final Supplier<ForceGroup> FLOATER_FORCE_GROUP = FORCE_GROUP_REGISTER.register(
                        "floater",
                        () -> new ForceGroup(
                                        Component.translatable("create_submarine.force_group.floater"),
                                        Component.translatable("create_submarine.force_group.floater.description"),
                                        0xADD8E6,
                                        true));
        public static final Logger LOGGER = LogUtils.getLogger();
        public static final boolean DISABLE_WATER_OCCLUSION = false;
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, MOD_ID);
        public static java.util.function.Function<dev.ryanhcode.sable.companion.SubLevelAccess, dev.ryanhcode.sable.companion.math.Pose3dc> clientPoseGetter = (
                        sub) -> sub.logicalPose();
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
                        .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
        public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT,
                        MOD_ID);
        public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister
                        .create(BuiltInRegistries.MENU, MOD_ID);
        public static final DeferredRegister<net.minecraft.world.effect.MobEffect> MOB_EFFECTS = DeferredRegister
                        .create(Registries.MOB_EFFECT, MOD_ID);
        public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITION_CODECS = DeferredRegister
                        .create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, MOD_ID);
        public static final net.neoforged.neoforge.registries.DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>, com.mojang.serialization.MapCodec<ConfigCondition>> CONFIG_CONDITION = CONDITION_CODECS
                        .register("config_enabled",
                                        () -> ConfigCondition.CODEC);
        public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS = DeferredRegister
                        .create(Registries.FLUID, MOD_ID);

        public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister
                        .create(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES, MOD_ID);

        public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.levelgen.DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister
                        .create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, MOD_ID);

        public static final Supplier<com.mojang.serialization.MapCodec<com.maxenonyme.createsubmarine.worldgen.OceanDepthOffset>> OCEAN_DEPTH_OFFSET = DENSITY_FUNCTIONS
                        .register("ocean_depth_offset",
                                        () -> com.maxenonyme.createsubmarine.worldgen.OceanDepthOffset.CODEC);

        public static final net.neoforged.neoforge.registries.DeferredHolder<FluidType, FluidType> OXYGEN_TYPE = FLUID_TYPES
                        .register("oxygen",
                                        () -> new FluidType(net.neoforged.neoforge.fluids.FluidType.Properties.create()
                                                        .descriptionId("fluid.create_submarine.oxygen")
                                                        .density(-1000)
                                                        .viscosity(1000)) {
                                                @Override
                                                public void initializeClient(
                                                                java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                                                        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                                                                @Override
                                                                public ResourceLocation getStillTexture() {
                                                                        return ResourceLocation.withDefaultNamespace(
                                                                                        "block/water_still");
                                                                }

                                                                @Override
                                                                public ResourceLocation getFlowingTexture() {
                                                                        return ResourceLocation.withDefaultNamespace(
                                                                                        "block/water_flow");
                                                                }

                                                                @Override
                                                                public int getTintColor() {
                                                                        return 0x88FFFFFF;
                                                                }
                                                        });
                                                }
                                        });

        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.material.Fluid, net.minecraft.world.level.material.FlowingFluid> OXYGEN = FLUIDS
                        .register("oxygen", () -> new net.neoforged.neoforge.fluids.BaseFlowingFluid.Source(
                                        makeOxygenProperties()));

        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.material.Fluid, net.minecraft.world.level.material.FlowingFluid> OXYGEN_FLOWING = FLUIDS
                        .register("oxygen_flowing", () -> new net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing(
                                        makeOxygenProperties()));

        private static net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties makeOxygenProperties() {
                return new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(
                                OXYGEN_TYPE, OXYGEN, OXYGEN_FLOWING).bucket(OXYGEN_BUCKET);
        }

        public static final Supplier<Item> OXYGEN_BUCKET = ITEMS.register("oxygen_bucket",
                        () -> new com.maxenonyme.createsubmarine.submarine.item.OxygenBucketItem(OXYGEN::get, new Item.Properties().stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET)));

        public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.effect.MobEffect, net.minecraft.world.effect.MobEffect> SUFFOCATION = MOB_EFFECTS
                        .register("suffocation",
                                        SuffocationEffect::new);
        public static final Supplier<Block> BAROMETER = BLOCKS.register("barometer",
                        () -> new BarometerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> BAROMETER_ITEM = ITEMS.register("barometer",
                        () -> new net.minecraft.world.item.BlockItem(BAROMETER.get(),
                                        new net.minecraft.world.item.Item.Properties()));
        public static final Supplier<BlockEntityType<BarometerBlockEntity>> BAROMETER_BE = BLOCK_ENTITIES
                        .register(
                                        "barometer",
                                        () -> BlockEntityType.Builder.of(
                                                        BarometerBlockEntity::new,
                                                        BAROMETER.get()).build(null));
        public static final Supplier<Block> COMMAND_SUB = BLOCKS.register("command_sub",
                        () -> new CommandSubBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> COMMAND_SUB_ITEM = ITEMS.register("command_sub",
                        () -> new net.minecraft.world.item.BlockItem(COMMAND_SUB.get(),
                                        new net.minecraft.world.item.Item.Properties()));
        public static final Supplier<BlockEntityType<CommandSubBlockEntity>> COMMAND_SUB_BE = BLOCK_ENTITIES
                        .register(
                                        "command_sub",
                                        () -> BlockEntityType.Builder.of(
                                                        CommandSubBlockEntity::new,
                                                        COMMAND_SUB.get()).build(null));
        public static final Supplier<Block> PUMP_CONTROLLER = BLOCKS.register("pump_controller",
                        () -> new PumpControllerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> PUMP_CONTROLLER_ITEM = ITEMS.register("pump_controller",
                        () -> new net.minecraft.world.item.BlockItem(PUMP_CONTROLLER.get(),
                                        new net.minecraft.world.item.Item.Properties()));
        public static final Supplier<BlockEntityType<PumpControllerBlockEntity>> PUMP_CONTROLLER_BE = BLOCK_ENTITIES
                        .register(
                                        "pump_controller",
                                        () -> BlockEntityType.Builder.of(
                                                        (pos, state) -> new PumpControllerBlockEntity(
                                                                        CreateSubmarine.PUMP_CONTROLLER_BE.get(), pos, state),
                                                        PUMP_CONTROLLER.get()).build(null));
        public static final Supplier<Block> CREATIVE_OXYGENATOR = BLOCKS.register("creative_oxygenator",
                        () -> new HullControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)));
        public static final Supplier<Item> CREATIVE_OXYGENATOR_ITEM = ITEMS.register("creative_oxygenator",
                        () -> new CreativeOxygenatorItem(
                                        CREATIVE_OXYGENATOR.get(), new net.minecraft.world.item.Item.Properties()
                                                        .rarity(net.minecraft.world.item.Rarity.EPIC)));
        public static final Supplier<BlockEntityType<HullControllerBlockEntity>> CREATIVE_OXYGENATOR_BE = BLOCK_ENTITIES
                        .register("creative_oxygenator",
                                        () -> BlockEntityType.Builder
                                                        .of(HullControllerBlockEntity::new, CREATIVE_OXYGENATOR.get())
                                                        .build(null));
        public static final Supplier<Block> BALLAST_TANK = BLOCKS.register("ballast_tank",
                        () -> new BallastTankBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        public static final Supplier<Item> BALLAST_TANK_ITEM = ITEMS.register("ballast_tank",
                        () -> new BallastTankItem(BALLAST_TANK.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<BallastTankBlockEntity>> BALLAST_TANK_BE = BLOCK_ENTITIES.register(
                        "ballast_tank",
                        () -> BlockEntityType.Builder.of(BallastTankBlockEntity::new, BALLAST_TANK.get()).build(null));
        public static final Supplier<Block> BALLAST_VENT = BLOCKS.register("ballast_vent",
                        () -> new BallastVentBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion()));
        public static final Supplier<Item> BALLAST_VENT_ITEM = ITEMS.register("ballast_vent",
                        () -> new net.minecraft.world.item.BlockItem(BALLAST_VENT.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<BallastVentBlockEntity>> BALLAST_VENT_BE = BLOCK_ENTITIES.register(
                        "ballast_vent",
                        () -> BlockEntityType.Builder.of(BallastVentBlockEntity::new, BALLAST_VENT.get()).build(null));
        public static final Supplier<Block> DECOMPRESSION_CHAMBER = BLOCKS.register("decompression_chamber",
                        () -> new DecompressionChamberBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion()));
        public static final Supplier<Item> DECOMPRESSION_CHAMBER_ITEM = ITEMS.register("decompression_chamber",
                        () -> new DecompressionChamberItem(DECOMPRESSION_CHAMBER.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<DecompressionChamberBlockEntity>> DECOMPRESSION_CHAMBER_BE = BLOCK_ENTITIES
                        .register(
                                        "decompression_chamber",
                                        () -> BlockEntityType.Builder.of(
                                                        DecompressionChamberBlockEntity::new,
                                                        DECOMPRESSION_CHAMBER.get()).build(null));
        public static final Supplier<Block> OXYGENE_DIFFUSER = BLOCKS.register("oxygene_diffuser",
                        () -> new OxygeneDiffuserBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion()));
        public static final Supplier<Item> OXYGENE_DIFFUSER_ITEM = ITEMS.register("oxygene_diffuser",
                        () -> new net.minecraft.world.item.BlockItem(OXYGENE_DIFFUSER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<OxygeneDiffuserBlockEntity>> OXYGENE_DIFFUSER_BE = BLOCK_ENTITIES
                        .register("oxygene_diffuser",
                                        () -> BlockEntityType.Builder
                                                        .of(OxygeneDiffuserBlockEntity::new, OXYGENE_DIFFUSER.get())
                                                        .build(null));
        public static final Supplier<SoundEvent> IMPLOSION_SOUND = SOUNDS.register("implosion",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "implosion")));
        public static final Supplier<SoundEvent> UNDERWATER_EXPLOSION_SOUND = SOUNDS.register("explosionunderwater",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "explosionunderwater")));
        public static final Supplier<SoundEvent> IMPACT_EXPLOSION_SOUND = SOUNDS.register("impact_explosion_03",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "impact_explosion_03")));
        public static final Supplier<Block> ELECTROLYZER = BLOCKS.register("electrolyzer",
                        () -> new ElectrolyzerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> ELECTROLYZER_ITEM = ITEMS.register("electrolyzer",
                        () -> new net.minecraft.world.item.BlockItem(ELECTROLYZER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER_BE = BLOCK_ENTITIES
                        .register(
                                        "electrolyzer",
                                        () -> BlockEntityType.Builder
                                                        .of(ElectrolyzerBlockEntity::new, ELECTROLYZER.get())
                                                        .build(null));
        public static final Supplier<Block> INDUSTRIAL_ALARM = BLOCKS.register("industrial_alarm",
                        () -> new IndustrialAlarmBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> INDUSTRIAL_ALARM_ITEM = ITEMS.register("industrial_alarm",
                        () -> new net.minecraft.world.item.BlockItem(INDUSTRIAL_ALARM.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<IndustrialAlarmBlockEntity>> INDUSTRIAL_ALARM_BE = BLOCK_ENTITIES
                        .register(
                                        "industrial_alarm",
                                        () -> BlockEntityType.Builder.of(
                                                        IndustrialAlarmBlockEntity::new,
                                                        INDUSTRIAL_ALARM.get()).build(null));
        public static final Supplier<Block> WATER_THRUSTER = BLOCKS.register("water_thruster",
                        () -> new WaterThrusterBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK).noOcclusion()));
        public static final Supplier<Item> WATER_THRUSTER_ITEM = ITEMS.register("water_thruster",
                        () -> new net.minecraft.world.item.BlockItem(WATER_THRUSTER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<WaterThrusterBlockEntity>> WATER_THRUSTER_BE = BLOCK_ENTITIES
                        .register(
                                        "water_thruster",
                                        () -> BlockEntityType.Builder
                                                        .of(WaterThrusterBlockEntity::new, WATER_THRUSTER.get())
                                                        .build(null));
        public static final Supplier<net.minecraft.world.inventory.MenuType<com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu>> ELECTROLYZER_MENU = MENUS
                        .register("electrolyzer",
                                        () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                                                        com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu::new));
        public static final Supplier<Block> IRON_PRESSURIZER = BLOCKS.register("iron_pressurizer",
                        () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                                        .strength(5.0F, 1200.0F)
                                        .requiresCorrectToolForDrops()
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> IRON_PRESSURIZER_ITEM = ITEMS.register("iron_pressurizer",
                        () -> new PressurizerItem(IRON_PRESSURIZER.get(),
                                        new Item.Properties()));

        public static final Supplier<Block> COPPER_PRESSURIZER = BLOCKS.register("copper_pressurizer",
                        () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                                        .strength(5.0F, 1200.0F)
                                        .requiresCorrectToolForDrops()
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> COPPER_PRESSURIZER_ITEM = ITEMS.register("copper_pressurizer",
                        () -> new PressurizerItem(
                                        COPPER_PRESSURIZER.get(), new Item.Properties()));

        public static final Supplier<Block> FLOATER = BLOCKS.register("floater",
                        () -> new FloaterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).noOcclusion()));
        public static final Supplier<Item> FLOATER_ITEM = ITEMS.register("floater",
                        () -> new FloaterItem(FLOATER.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<FloaterBlockEntity>> FLOATER_BE = BLOCK_ENTITIES.register(
                        "floater",
                        () -> BlockEntityType.Builder.of(FloaterBlockEntity::new, FLOATER.get()).build(null));
        public static final Supplier<Item> PHYCOLOGICAL_MEMBRANE = ITEMS.register("phycological_membrane",
                        () -> new PhycologicalMembraneItem(
                                        new net.minecraft.world.item.Item.Properties()
                                                        .rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        public static final Supplier<Item> STEEL_CABLE = ITEMS.register("steel_cable",
                        () -> new SteelCableItem(
                                        new net.minecraft.world.item.Item.Properties()));
        public static final boolean SUBMARINE_STAFF_ENABLED = !net.neoforged.fml.loading.FMLEnvironment.production;
        public static final Supplier<Item> SUBMARINE_STAFF = SUBMARINE_STAFF_ENABLED
                        ? ITEMS.register("submarine_staff",
                                        () -> new com.maxenonyme.createsubmarine.submarine.item.SubmarineStaffItem(
                                                        new net.minecraft.world.item.Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)))
                        : null;


        public static final Supplier<Block> PULLEY = BLOCKS.register("pulley",
                        () -> new PulleyBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> PULLEY_ITEM = ITEMS.register("pulley",
                        () -> new net.minecraft.world.item.BlockItem(PULLEY.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<PulleyBlockEntity>> PULLEY_BE = BLOCK_ENTITIES.register(
                        "pulley",
                        () -> BlockEntityType.Builder.of(PulleyBlockEntity::new, PULLEY.get()).build(null));

        public static final Supplier<Block> ARRESTING_HOOK = BLOCKS.register("arresting_hook",
                        () -> new ArrestingHookBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> ARRESTING_HOOK_ITEM = ITEMS.register("arresting_hook",
                        () -> new ArrestingHookItem(ARRESTING_HOOK.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<ArrestingHookBlockEntity>> ARRESTING_HOOK_BE = BLOCK_ENTITIES
                        .register(
                                        "arresting_hook",
                                        () -> BlockEntityType.Builder.of(
                                                        ArrestingHookBlockEntity::new,
                                                        ARRESTING_HOOK.get()).build(null));

        public static final Supplier<Block> UNDERWATER_MINE = BLOCKS.register("underwater_mine",
                        () -> new UnderwaterMineBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));
        public static final Supplier<Item> UNDERWATER_MINE_ITEM = ITEMS.register("underwater_mine",
                        () -> new net.minecraft.world.item.BlockItem(UNDERWATER_MINE.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<UnderwaterMineBlockEntity>> UNDERWATER_MINE_BE = BLOCK_ENTITIES
                        .register(
                                        "underwater_mine",
                                        () -> BlockEntityType.Builder
                                                        .of(UnderwaterMineBlockEntity::new, UNDERWATER_MINE.get())
                                                        .build(null));

        public static final Supplier<Block> SUBMARINE_PROPELLER = BLOCKS.register("submarine_propeller",
                        () -> new com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        public static final Supplier<Item> SUBMARINE_PROPELLER_ITEM = ITEMS.register("submarine_propeller",
                        () -> new net.minecraft.world.item.BlockItem(SUBMARINE_PROPELLER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerBlockEntity>> SUBMARINE_PROPELLER_BE = BLOCK_ENTITIES
                        .register(
                                        "submarine_propeller",
                                        () -> BlockEntityType.Builder.of(
                                                        com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerBlockEntity::new,
                                                        SUBMARINE_PROPELLER.get()).build(null));

        public CreateSubmarine(IEventBus modEventBus, ModContainer modContainer) {
                modContainer.registerConfig(ModConfig.Type.COMMON, SubmarineConfig.COMMON_SPEC);
                modContainer.registerConfig(ModConfig.Type.SERVER, SubmarineConfig.SERVER_SPEC);
                modContainer.registerConfig(ModConfig.Type.CLIENT, SubmarineConfig.CLIENT_SPEC);
                BLOCKS.register(modEventBus);
                FORCE_GROUP_REGISTER.register(modEventBus);
                ITEMS.register(modEventBus);
                BLOCK_ENTITIES.register(modEventBus);
                SOUNDS.register(modEventBus);
                MOB_EFFECTS.register(modEventBus);
                FLUID_TYPES.register(modEventBus);
                FLUIDS.register(modEventBus);
                MENUS.register(modEventBus);
                DENSITY_FUNCTIONS.register(modEventBus);
                CONDITION_CODECS.register(modEventBus);
                SubmarineDisplaySources.register(modEventBus);
                modEventBus.addListener(this::onCommonSetup);
                modEventBus.addListener(this::onConfigLoaded);
                modEventBus.addListener(this::registerPayloads);
                NeoForge.EVENT_BUS.addListener(SubmarinePressureSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubmarinePressureSystem::onBlockBroken);
                NeoForge.EVENT_BUS.addListener(SubmarineSinkingSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubmarineInteractionSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(PhysicsWakeSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(
                                SteelCablePhysicsSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(
                                CableElectrificationSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineInfoCommand::register);
                NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,
                                WrenchRepairHandler::onRightClickBlock);
                NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,
                                DiffuserZoneProtection::onRightClickBlock);
                NeoForge.EVENT_BUS.addListener(
                                DiffuserZoneProtection::onBlockPlace);
                NeoForge.EVENT_BUS.addListener(
                                DiffuserZoneProtection::onPistonMove);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineLifecycleHandler::onServerStopping);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineLifecycleHandler::onLevelUnload);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineLifecycleHandler::onPlayerLoggedIn);
                NeoForge.EVENT_BUS.addListener(
                                SubLevelCableCleanup::onLevelLoad);

                modEventBus.addListener(this::registerCapabilities);

                if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                        CreateSubmarineClient.init(modEventBus, modContainer);
                }
        }

        private void registerPayloads(final net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
                final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar = event.registrar(MOD_ID);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload::handle);
                registrar.playToServer(
                                com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload::handle);
                registrar.playToServer(
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigSyncPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigSyncPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigSyncPayload::handle);
                registrar.playToServer(
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigEditPayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigEditPayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.HullConfigEditPayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload::handle);
                registrar.playToClient(
                                com.maxenonyme.createsubmarine.submarine.network.CableStrandRemovePayload.TYPE,
                                com.maxenonyme.createsubmarine.submarine.network.CableStrandRemovePayload.CODEC,
                                com.maxenonyme.createsubmarine.submarine.network.CableStrandRemovePayload::handle);
        }

        private void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
                @SuppressWarnings("unchecked")
                net.minecraft.world.level.block.entity.BlockEntityType<dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity> ropeWinchType = (net.minecraft.world.level.block.entity.BlockEntityType<dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity>) net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .get(
                                                net.minecraft.resources.ResourceLocation
                                                                .fromNamespaceAndPath("simulated", "rope_winch"));
                if (ropeWinchType != null) {
                        event.registerBlockEntity(
                                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                        ropeWinchType,
                                        (be, side) -> CableElectrificationSystem
                                                        .getOrCreateStorage(be));
                }
                @SuppressWarnings("unchecked")
                net.minecraft.world.level.block.entity.BlockEntityType<dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity> ropeConnectorType = (net.minecraft.world.level.block.entity.BlockEntityType<dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity>) net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .get(
                                                net.minecraft.resources.ResourceLocation
                                                                .fromNamespaceAndPath("simulated", "rope_connector"));
                if (ropeConnectorType != null) {
                        event.registerBlockEntity(
                                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                        ropeConnectorType,
                                        (be, side) -> CableElectrificationSystem
                                                        .getOrCreateStorage(be));
                }
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                BALLAST_TANK_BE.get(),
                                (be, side) -> be.getClusterFluidHandler(side));
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                BALLAST_VENT_BE.get(),
                                (be, side) -> be.getFluidHandlerForSide(side));
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                DECOMPRESSION_CHAMBER_BE.get(),
                                (be, side) -> be.getFluidHandlerForSide(side));
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> be.combinedFluidHandler);
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                OXYGENE_DIFFUSER_BE.get(),
                                (be, side) -> be.oxygenTank);
                event.registerBlockEntity(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                                WATER_THRUSTER_BE.get(),
                                (be, side) -> {
                                        if (side == null || side == be.getBlockState().getValue(
                                                        net.minecraft.world.level.block.DirectionalBlock.FACING)
                                                        .getOpposite()) {
                                                return be.waterTank;
                                        }
                                        return null;
                                });
                event.registerBlockEntity(
                                        net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> {
                                        if (side != null && side != Direction.UP && side != Direction.DOWN)
                                                return be.energyStorage;
                                        return null;
                                });
                event.registerItem(
                                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                                (stack, ctx) -> new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack),
                                OXYGEN_BUCKET.get());
        }

        private void onConfigLoaded(net.neoforged.fml.event.config.ModConfigEvent event) {
                if (event.getConfig().getSpec() == SubmarineConfig.COMMON_SPEC) {
                        com.maxenonyme.createsubmarine.worldgen.OceanDepthOffset.refreshConfig();
                } else if (event.getConfig().getSpec() == SubmarineConfig.SERVER_SPEC) {
                        HullStrengthConfig.load();
                }
        }

        private void onCommonSetup(FMLCommonSetupEvent event) {
                event.enqueueWork(() -> {
                        HullStrengthConfig.load();
                        registerToSimulatedTab();
                        com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(SUBMARINE_PROPELLER.get(),
                                        () -> 4.0);
                        com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(PUMP_CONTROLLER.get(),
                                        () -> 4.0);
                        com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(ELECTROLYZER.get(),
                                        () -> 8.0);
                        com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(
                                        PUMP_CONTROLLER_ITEM.get(),
                                        com.simibubi.create.foundation.item.TooltipModifier.mapNull(
                                                        com.simibubi.create.foundation.item.KineticStats
                                                                        .create(PUMP_CONTROLLER_ITEM.get())));
                        com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK_ENTITY.register(
                                        COMMAND_SUB_BE.get(),
                                        java.util.List.of(
                                                        SubmarineDisplaySources.COMMAND_SUB
                                                                        .get()));
                        com.simibubi.create.foundation.item.TooltipModifier.REGISTRY.register(
                                        SUBMARINE_PROPELLER_ITEM.get(),
                                        com.simibubi.create.foundation.item.TooltipModifier.mapNull(
                                                        com.simibubi.create.foundation.item.KineticStats
                                                                        .create(SUBMARINE_PROPELLER_ITEM.get())));
                        com.simibubi.create.api.behaviour.display.DisplaySource.BY_BLOCK_ENTITY.register(
                                        BAROMETER_BE.get(),
                                        java.util.List.of(
                                                        SubmarineDisplaySources.BAROMETER
                                                                        .get()));
                });
        }

        @SuppressWarnings("unchecked")
        private void registerToSimulatedTab() {
                try {
                        Class<?> regClass = Class
                                        .forName("dev.simulated_team.simulated.registrate.SimulatedRegistrate");
                        List<Supplier<Item>> tabItems = (List<Supplier<Item>>) regClass.getField("TAB_ITEMS").get(null);
                        Map<ResourceLocation, ResourceLocation> itemToSection = (Map<ResourceLocation, ResourceLocation>) regClass
                                        .getField("ITEM_TO_SECTION").get(null);

                        tabItems.add(CREATIVE_OXYGENATOR_ITEM::get);
                        tabItems.add(BALLAST_TANK_ITEM::get);
                        tabItems.add(BALLAST_VENT_ITEM::get);
                        tabItems.add(DECOMPRESSION_CHAMBER_ITEM::get);
                        tabItems.add(OXYGENE_DIFFUSER_ITEM::get);
                        ResourceLocation subSection = ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine");
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "creative_oxygenator"),
                                        subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_tank"), subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_vent"), subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "decompression_chamber"),
                                        subSection);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygene_diffuser"),
                                        subSection);
                        tabItems.add(ELECTROLYZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "electrolyzer"), subSection);
                        tabItems.add(WATER_THRUSTER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "water_thruster"), subSection);
                        tabItems.add(IRON_PRESSURIZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "iron_pressurizer"),
                                        subSection);
                        tabItems.add(COPPER_PRESSURIZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "copper_pressurizer"),
                                        subSection);
                        tabItems.add(FLOATER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "floater"), subSection);
                        tabItems.add(PHYCOLOGICAL_MEMBRANE::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "phycological_membrane"),
                                        subSection);
                        tabItems.add(OXYGEN_BUCKET::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygen_bucket"),
                                        subSection);
                        tabItems.add(STEEL_CABLE::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "steel_cable"), subSection);
                        tabItems.add(PULLEY_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pulley"), subSection);
                        tabItems.add(UNDERWATER_MINE_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "underwater_mine"), subSection);
                        tabItems.add(SUBMARINE_PROPELLER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine_propeller"),
                                        subSection);
                        tabItems.add(BAROMETER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "barometer"), subSection);
                        tabItems.add(COMMAND_SUB_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "command_sub"), subSection);
                        tabItems.add(PUMP_CONTROLLER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pump_controller"), subSection);
                        tabItems.add(ARRESTING_HOOK_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "arresting_hook"), subSection);
                        if (SUBMARINE_STAFF_ENABLED) {
                                tabItems.add(SUBMARINE_STAFF::get);
                                itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine_staff"), subSection);
                        }
                } catch (Exception ignored) {
                }
        }
}
