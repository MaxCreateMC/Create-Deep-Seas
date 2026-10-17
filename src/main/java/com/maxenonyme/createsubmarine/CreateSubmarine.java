package com.maxenonyme.createsubmarine;

import com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerBlock;
import com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller.SubmarinePropellerBlockEntity;
import com.maxenonyme.createsubmarine.submarine.compartment.BreachLedger;
import com.maxenonyme.createsubmarine.submarine.compartment.FloodSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import com.maxenonyme.createsubmarine.submarine.block.*;
import com.maxenonyme.createsubmarine.submarine.block.entity.*;
import com.maxenonyme.createsubmarine.submarine.effect.SuffocationEffect;
import com.maxenonyme.createsubmarine.submarine.system.*;
import com.maxenonyme.createsubmarine.submarine.config.HullStrengthConfig;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.createsubmarine.submarine.gui.ElectrolyzerMenu;
import com.maxenonyme.createsubmarine.submarine.item.DivingSuitItem;
import com.maxenonyme.createsubmarine.submarine.item.OxygenBucketItem;
import com.maxenonyme.createsubmarine.submarine.item.PressureGogglesItem;
import com.maxenonyme.createsubmarine.submarine.item.SubmarineStaffItem;
import com.maxenonyme.createsubmarine.submarine.network.AbyssFogPayload;
import com.maxenonyme.createsubmarine.submarine.network.AlarmConfigurePayload;
import com.maxenonyme.createsubmarine.submarine.network.AlarmOpenPayload;
import com.maxenonyme.createsubmarine.submarine.network.AlarmSoundChunkPayload;
import com.maxenonyme.createsubmarine.submarine.network.AlarmSoundRequestPayload;
import com.maxenonyme.createsubmarine.submarine.network.BreachSyncPayload;
import com.maxenonyme.createsubmarine.submarine.network.CableStrandRemovePayload;
import com.maxenonyme.createsubmarine.submarine.network.CameraShakePayload;
import com.maxenonyme.createsubmarine.submarine.network.CommandSubPayload;
import com.maxenonyme.createsubmarine.submarine.network.DiagnosticPayload;
import com.maxenonyme.createsubmarine.submarine.network.ElectrolyzerTogglePayload;
import com.maxenonyme.createsubmarine.submarine.network.HullConfigEditPayload;
import com.maxenonyme.createsubmarine.submarine.network.HullConfigSyncPayload;
import com.maxenonyme.createsubmarine.submarine.network.ImplosionFxPayload;
import com.maxenonyme.createsubmarine.submarine.network.LightFailurePayload;
import com.maxenonyme.createsubmarine.submarine.network.PressureModePayload;
import com.maxenonyme.createsubmarine.submarine.network.SubCrackPayload;
import com.maxenonyme.createsubmarine.submarine.network.SubLevelBoundsPayload;
import com.maxenonyme.createsubmarine.submarine.system.GirderFailure;
import com.maxenonyme.createsubmarine.submarine.system.HullStressFx;
import com.maxenonyme.createsubmarine.submarine.system.ImplosionSequence;
import com.maxenonyme.createsubmarine.submarine.system.LightFailure;
import com.maxenonyme.createsubmarine.submarine.system.SubLevelFireSystem;
import com.maxenonyme.createsubmarine.worldgen.OceanDepthOffset;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.minecraft.network.chat.Component;
import foundry.veil.platform.registry.RegistrationProvider;
import foundry.veil.platform.registry.RegistryObject;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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
        public static Function<SubLevelAccess, Pose3dc> clientPoseGetter = (
                        sub) -> sub.logicalPose();
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MOD_ID);
        public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister
                        .create(Registries.ARMOR_MATERIAL, MOD_ID);
        public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DIVING_SUIT_MATERIAL = ARMOR_MATERIALS
                        .register("diving_suit", () -> new ArmorMaterial(
                                        Map.of(ArmorItem.Type.HELMET, 2,
                                                        ArmorItem.Type.CHESTPLATE, 6,
                                                        ArmorItem.Type.LEGGINGS, 5,
                                                        ArmorItem.Type.BOOTS, 2),
                                        9, SoundEvents.ARMOR_EQUIP_IRON,
                                        () -> Ingredient.of(Items.COPPER_INGOT),
                                        List.of(new ArmorMaterial.Layer(
                                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "diving_suit"))),
                                        0f, 0f));
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister
                        .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, MOD_ID);
        // Legacy entities/items kept through the experimental merge (sonar pinger, harpoon, leak detector, staffs).
        public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister
                        .create(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, MOD_ID);
        public static final Supplier<EntityType<com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerEntity>> SONAR_PINGER_ENTITY = ENTITY_TYPES
                        .register("sonar_pinger", () -> EntityType.Builder
                                        .<com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerEntity>of(
                                                        com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerEntity::new,
                                                        net.minecraft.world.entity.MobCategory.MISC)
                                        .setShouldReceiveVelocityUpdates(false)
                                        .sized(0.5F, 0.5F)
                                        .build(CreateSubmarine.MOD_ID + ":sonar_pinger"));
        public static final Supplier<Item> SONAR_PINGER_ITEM = ITEMS.register("sonar_pinger",
                        () -> new com.maxenonyme.createsubmarine.submarine.sonar.SonarPingerItem(
                                        new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        public static final Supplier<EntityType<com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity>> HARPOON_ENTITY = ENTITY_TYPES
                        .register("harpoon", () -> EntityType.Builder
                                        .<com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity>of(
                                                        com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity::new,
                                                        net.minecraft.world.entity.MobCategory.MISC)
                                        .sized(0.25F, 0.25F)
                                        .clientTrackingRange(8)
                                        .build(CreateSubmarine.MOD_ID + ":harpoon"));
        public static final Supplier<Item> LEAK_DETECTOR = ITEMS.register("leak_detector",
                        () -> new com.maxenonyme.createsubmarine.submarine.item.leak_detector.LeakDetectorItem(
                                        new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).stacksTo(1)));
        public static final Supplier<Item> WARDING_STAFF = ITEMS.register("warding_staff",
                        () -> new com.maxenonyme.createsubmarine.submarine.item.warding_staff.WardingStaffItem(
                                        new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).stacksTo(1)));
        public static final Supplier<Item> HARPOON_GUN = ITEMS.register("harpoon_gun",
                        () -> new com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonGunItem(
                                        new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));
        public static final Supplier<Item> PURGE_STAFF = ITEMS.register("purge_staff",
                        () -> new com.maxenonyme.createsubmarine.submarine.item.purge_staff.PurgeStaffItem(
                                        new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC).stacksTo(1)));
        public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT,
                        MOD_ID);
        public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister
                        .create(BuiltInRegistries.MENU, MOD_ID);
        public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister
                        .create(Registries.MOB_EFFECT, MOD_ID);
        public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS = DeferredRegister
                        .create(NeoForgeRegistries.Keys.CONDITION_CODECS, MOD_ID);
        public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ConfigCondition>> CONFIG_CONDITION = CONDITION_CODECS
                        .register("config_enabled",
                                        () -> ConfigCondition.CODEC);
        public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister
                        .create(Registries.FLUID, MOD_ID);

        public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister
                        .create(NeoForgeRegistries.FLUID_TYPES, MOD_ID);

        public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS = DeferredRegister
                        .create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, MOD_ID);

        public static final Supplier<MapCodec<OceanDepthOffset>> OCEAN_DEPTH_OFFSET = DENSITY_FUNCTIONS
                        .register("ocean_depth_offset",
                                        () -> OceanDepthOffset.CODEC);

        public static final TagKey<Fluid> OXYGEN_TAG = TagKey
                        .create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygen"));

        public static final DeferredHolder<FluidType, FluidType> OXYGEN_TYPE = FLUID_TYPES
                        .register("oxygen",
                                        () -> new FluidType(FluidType.Properties.create()
                                                        .descriptionId("fluid.create_submarine.oxygen")
                                                        .density(-1000)
                                                        .viscosity(1000)) {
                                                @Override
                                                public void initializeClient(
                                                                Consumer<IClientFluidTypeExtensions> consumer) {
                                                        consumer.accept(new IClientFluidTypeExtensions() {
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

        public static final DeferredHolder<Fluid, FlowingFluid> OXYGEN = FLUIDS
                        .register("oxygen", () -> new BaseFlowingFluid.Source(
                                        makeOxygenProperties()));

        public static final DeferredHolder<Fluid, FlowingFluid> OXYGEN_FLOWING = FLUIDS
                        .register("oxygen_flowing", () -> new BaseFlowingFluid.Flowing(
                                        makeOxygenProperties()));

        private static BaseFlowingFluid.Properties makeOxygenProperties() {
                return new BaseFlowingFluid.Properties(
                                OXYGEN_TYPE, OXYGEN, OXYGEN_FLOWING).bucket(OXYGEN_BUCKET);
        }

        public static final Supplier<Item> OXYGEN_BUCKET = ITEMS.register("oxygen_bucket",
                        () -> new OxygenBucketItem(OXYGEN::get, new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET)));

        public static final DeferredHolder<MobEffect, MobEffect> SUFFOCATION = MOB_EFFECTS
                        .register("suffocation",
                                        SuffocationEffect::new);
        public static final Supplier<Block> BAROMETER = BLOCKS.register("barometer",
                        () -> new BarometerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> BAROMETER_ITEM = ITEMS.register("barometer",
                        () -> new BlockItem(BAROMETER.get(),
                                        new Item.Properties()));
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
                        () -> new BlockItem(COMMAND_SUB.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<CommandSubBlockEntity>> COMMAND_SUB_BE = BLOCK_ENTITIES
                        .register(
                                        "command_sub",
                                        () -> BlockEntityType.Builder.of(
                                                        CommandSubBlockEntity::new,
                                                        COMMAND_SUB.get()).build(null));
        public static final Supplier<Block> SONAR = BLOCKS.register("sonar",
                        () -> new SonarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                        .requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 6)));
        public static final Supplier<Item> SONAR_ITEM = ITEMS.register("sonar",
                        () -> new BlockItem(SONAR.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<SonarBlockEntity>> SONAR_BE = BLOCK_ENTITIES.register("sonar",
                        () -> BlockEntityType.Builder.of(SonarBlockEntity::new, SONAR.get()).build(null));
        public static final Supplier<Block> PUMP_CONTROLLER = BLOCKS.register("pump_controller",
                        () -> new PumpControllerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> PUMP_CONTROLLER_ITEM = ITEMS.register("pump_controller",
                        () -> new BlockItem(PUMP_CONTROLLER.get(),
                                        new Item.Properties()));
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
                                        CREATIVE_OXYGENATOR.get(), new Item.Properties()
                                                        .rarity(Rarity.EPIC)));
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
                        () -> new BlockItem(BALLAST_VENT.get(), new Item.Properties()));
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
                        () -> new BlockItem(OXYGENE_DIFFUSER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<OxygeneDiffuserBlockEntity>> OXYGENE_DIFFUSER_BE = BLOCK_ENTITIES
                        .register("oxygene_diffuser",
                                        () -> BlockEntityType.Builder
                                                        .of(OxygeneDiffuserBlockEntity::new, OXYGENE_DIFFUSER.get())
                                                        .build(null));
        public static final Supplier<SoundEvent> INDUSTRIAL_ALARM_SOUND = SOUNDS.register("industrial_alarm",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "industrial_alarm")));
        public static final Supplier<SoundEvent> IMPLOSION_SOUND = SOUNDS.register("implosion",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "implosion")));
        public static final Supplier<SoundEvent> UNDERWATER_EXPLOSION_SOUND = SOUNDS.register("explosionunderwater",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "explosionunderwater")));
        public static final Supplier<SoundEvent> TINNITUS_SOUND = SOUNDS.register("acouphene",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "acouphene")));
        public static final Supplier<SoundEvent> HEARTBEAT_SOUND = SOUNDS.register("heartbeat",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "heartbeat")));
        public static final Supplier<SoundEvent> IMPACT_EXPLOSION_SOUND = SOUNDS.register("impact_explosion_03",
                        () -> SoundEvent.createVariableRangeEvent(
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID, "impact_explosion_03")));
        public static final Supplier<Block> ELECTROLYZER = BLOCKS.register("electrolyzer",
                        () -> new ElectrolyzerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                                        .noOcclusion()
                                        .isViewBlocking((state, level, pos) -> false)
                                        .isSuffocating((state, level, pos) -> false)));
        public static final Supplier<Item> ELECTROLYZER_ITEM = ITEMS.register("electrolyzer",
                        () -> new BlockItem(ELECTROLYZER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER_BE = BLOCK_ENTITIES
                        .register(
                                        "electrolyzer",
                                        () -> BlockEntityType.Builder
                                                        .of(ElectrolyzerBlockEntity::new, ELECTROLYZER.get())
                                                        .build(null));
        public static final Supplier<Block> INDUSTRIAL_ALARM = BLOCKS.register("industrial_alarm",
                        () -> new IndustrialAlarmBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()
                                                        .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)));
        public static final Supplier<Item> INDUSTRIAL_ALARM_ITEM = ITEMS.register("industrial_alarm",
                        () -> new IndustrialAlarmItem(INDUSTRIAL_ALARM.get(), new Item.Properties()));
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
                        () -> new BlockItem(WATER_THRUSTER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<WaterThrusterBlockEntity>> WATER_THRUSTER_BE = BLOCK_ENTITIES
                        .register(
                                        "water_thruster",
                                        () -> BlockEntityType.Builder
                                                        .of(WaterThrusterBlockEntity::new, WATER_THRUSTER.get())
                                                        .build(null));
        public static final Supplier<MenuType<ElectrolyzerMenu>> ELECTROLYZER_MENU = MENUS
                        .register("electrolyzer",
                                        () -> IMenuTypeExtension.create(
                                                        ElectrolyzerMenu::new));
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
                        () -> new FloaterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)));
        public static final Supplier<Item> FLOATER_ITEM = ITEMS.register("floater",
                        () -> new FloaterItem(FLOATER.get(),
                                        new Item.Properties()));
        public static final Supplier<BlockEntityType<FloaterBlockEntity>> FLOATER_BE = BLOCK_ENTITIES.register(
                        "floater",
                        () -> BlockEntityType.Builder.of(FloaterBlockEntity::new, FLOATER.get()).build(null));
        public static final Supplier<Item> PHYCOLOGICAL_MEMBRANE = ITEMS.register("phycological_membrane",
                        () -> new PhycologicalMembraneItem(
                                        new Item.Properties()
                                                        .rarity(Rarity.UNCOMMON)));
        public static final Supplier<Item> STEEL_CABLE = ITEMS.register("steel_cable",
                        () -> new SteelCableItem(
                                        new Item.Properties()));
        public static final Supplier<Item> DIVING_CHESTPLATE = ITEMS.register("diving_chestplate",
                        () -> new DivingSuitItem(DIVING_SUIT_MATERIAL,
                                        ArmorItem.Type.CHESTPLATE,
                                        new Item.Properties().stacksTo(1)
                                                        .durability(ArmorItem.Type.CHESTPLATE.getDurability(15)),
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID,
                                                        "textures/models/armor/copper_diving_chestplate.png")));
        public static final Supplier<Item> DIVING_LEGGINGS = ITEMS.register("diving_leggings",
                        () -> new DivingSuitItem(DIVING_SUIT_MATERIAL,
                                        ArmorItem.Type.LEGGINGS,
                                        new Item.Properties().stacksTo(1)
                                                        .durability(ArmorItem.Type.LEGGINGS.getDurability(15)),
                                        ResourceLocation.fromNamespaceAndPath(MOD_ID,
                                                        "textures/models/armor/copper_diving_leggings.png")));
        public static final Supplier<Item> PRESSURE_GOGGLES = ITEMS.register("pressure_goggles",
                        () -> new PressureGogglesItem(
                                        new Item.Properties().stacksTo(1)));
        public static final boolean SUBMARINE_STAFF_ENABLED = !FMLEnvironment.production;
        public static final Supplier<Item> SUBMARINE_STAFF = SUBMARINE_STAFF_ENABLED
                        ? ITEMS.register("submarine_staff",
                                        () -> new SubmarineStaffItem(
                                                        new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)))
                        : null;


        public static final Supplier<Block> PULLEY = BLOCKS.register("pulley",
                        () -> new PulleyBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                        .requiresCorrectToolForDrops().noOcclusion()));
        public static final Supplier<Item> PULLEY_ITEM = ITEMS.register("pulley",
                        () -> new BlockItem(PULLEY.get(), new Item.Properties()));
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
                        () -> new BlockItem(UNDERWATER_MINE.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<UnderwaterMineBlockEntity>> UNDERWATER_MINE_BE = BLOCK_ENTITIES
                        .register(
                                        "underwater_mine",
                                        () -> BlockEntityType.Builder
                                                        .of(UnderwaterMineBlockEntity::new, UNDERWATER_MINE.get())
                                                        .build(null));

        public static final Supplier<Block> SUBMARINE_PROPELLER = BLOCKS.register("submarine_propeller",
                        () -> new SubmarinePropellerBlock(
                                        BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)));
        public static final Supplier<Item> SUBMARINE_PROPELLER_ITEM = ITEMS.register("submarine_propeller",
                        () -> new BlockItem(SUBMARINE_PROPELLER.get(), new Item.Properties()));
        public static final Supplier<BlockEntityType<SubmarinePropellerBlockEntity>> SUBMARINE_PROPELLER_BE = BLOCK_ENTITIES
                        .register(
                                        "submarine_propeller",
                                        () -> BlockEntityType.Builder.of(
                                                        SubmarinePropellerBlockEntity::new,
                                                        SUBMARINE_PROPELLER.get()).build(null));

        public CreateSubmarine(IEventBus modEventBus, ModContainer modContainer) {
                modContainer.registerConfig(ModConfig.Type.COMMON, SubmarineConfig.COMMON_SPEC);
                modContainer.registerConfig(ModConfig.Type.SERVER, SubmarineConfig.SERVER_SPEC);
                modContainer.registerConfig(ModConfig.Type.CLIENT, SubmarineConfig.CLIENT_SPEC);
                BLOCKS.register(modEventBus);
                FORCE_GROUP_REGISTER.register(modEventBus);
                ENTITY_TYPES.register(modEventBus);
                // Cogfly solver force groups (kept alongside the DeferredRegister groups above).
                net.minecraft.core.Registry.register(
                                dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                                com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem.STRESS_ID,
                                new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.stress"),
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.stress.desc"),
                                                0xFFFF4444,
                                                true));
                net.minecraft.core.Registry.register(
                                dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                                com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem.INTERNAL_STRESS_ID,
                                new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.internal_stress"),
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.internal_stress.desc"),
                                                0xFFFFAA00,
                                                false));
                net.minecraft.core.Registry.register(
                                dev.ryanhcode.sable.api.physics.force.ForceGroups.REGISTRY,
                                com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem.BUOYANCY_ID,
                                new dev.ryanhcode.sable.api.physics.force.ForceGroup(
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.buoyancy"),
                                                net.minecraft.network.chat.Component.translatable("force.create_submarine.buoyancy.desc"),
                                                0xFF44DDBB,
                                                true));
                ITEMS.register(modEventBus);
                ARMOR_MATERIALS.register(modEventBus);
                BLOCK_ENTITIES.register(modEventBus);
                SOUNDS.register(modEventBus);
                MOB_EFFECTS.register(modEventBus);
                FLUID_TYPES.register(modEventBus);
                FLUIDS.register(modEventBus);
                MENUS.register(modEventBus);
                DENSITY_FUNCTIONS.register(modEventBus);
                CONDITION_CODECS.register(modEventBus);
                SubmarineDisplaySources.register(modEventBus);
                GogglesItem.addIsWearingPredicate(
                                PressureGogglesItem::isWearing);
                modEventBus.addListener(this::onCommonSetup);
                modEventBus.addListener(this::onConfigLoaded);
                modEventBus.addListener(this::registerPayloads);
                NeoForge.EVENT_BUS.addListener(SubmarinePressureSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(GirderFailure::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubLevelFireSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(SubmarinePressureSystem::onBlockBroken);
                NeoForge.EVENT_BUS.addListener(SubmarineSinkingSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(ImplosionSequence::onServerTick);
                NeoForge.EVENT_BUS.addListener(HullStressFx::onServerTick);
                NeoForge.EVENT_BUS.addListener(LightFailure::onServerTick);
                NeoForge.EVENT_BUS.addListener(LightFailure::onLogin);
                NeoForge.EVENT_BUS.addListener(LightFailure::onChangeDimension);
                NeoForge.EVENT_BUS.addListener(FloodSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(FloodSystem::onPhysicsTick);
                NeoForge.EVENT_BUS.addListener(SubmarineInteractionSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(PhysicsWakeSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(DiverPressure::onPlayerTick);
                NeoForge.EVENT_BUS.addListener(
                                SteelCablePhysicsSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(
                                CableElectrificationSystem::onServerTick);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineInfoCommand::register);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineAbyssCommand::register);
                NeoForge.EVENT_BUS.addListener(
                                SubmarineAbyssCommand::onPlayerLoggedIn);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,
                                WrenchRepairHandler::onRightClickBlock);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,
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
                                BreachLedger::onServerStarted);
                NeoForge.EVENT_BUS.addListener(
                                BreachLedger::onLogin);
                NeoForge.EVENT_BUS.addListener(
                                SubLevelCableCleanup::onLevelLoad);
                // Cogfly solver wiring (kept through the experimental merge).
                com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem.register();
                com.maxenonyme.createsubmarine.submarine.stress.StressForceFeedSystem.registerListeners();
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.stress.SubLevelStressAnalyzer::onGlobalServerTick);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.stress.StressCommand::register);
                NeoForge.EVENT_BUS.addListener(com.maxenonyme.createsubmarine.submarine.util.HarpoonRopeHandler::onServerTick);

                modEventBus.addListener(this::registerCapabilities);
                modEventBus.addListener(this::moveDivingGear);

                if (FMLEnvironment.dist.isClient()) {
                        CreateSubmarineClient.init(modEventBus, modContainer);
                }
        }

        private void registerPayloads(final RegisterPayloadHandlersEvent event) {
                final PayloadRegistrar registrar = event.registrar(MOD_ID);
                registrar.playToClient(
                                SubLevelBoundsPayload.TYPE,
                                SubLevelBoundsPayload.CODEC,
                                SubLevelBoundsPayload::handle);
                registrar.playToClient(
                                DiagnosticPayload.TYPE,
                                DiagnosticPayload.CODEC,
                                DiagnosticPayload::handle);
                registrar.playToClient(
                                BreachSyncPayload.TYPE,
                                BreachSyncPayload.CODEC,
                                BreachSyncPayload::handle);
                registrar.playToClient(
                                LightFailurePayload.TYPE,
                                LightFailurePayload.CODEC,
                                LightFailurePayload::handle);
                registrar.playToClient(
                                AbyssFogPayload.TYPE,
                                AbyssFogPayload.CODEC,
                                AbyssFogPayload::handle);
                registrar.playToClient(
                                SubCrackPayload.TYPE,
                                SubCrackPayload.CODEC,
                                SubCrackPayload::handle);
                registrar.playToServer(
                                CommandSubPayload.TYPE,
                                CommandSubPayload.CODEC,
                                CommandSubPayload::handle);
                registrar.playToClient(
                                AlarmOpenPayload.TYPE,
                                AlarmOpenPayload.CODEC,
                                AlarmOpenPayload::handle);
                registrar.playToServer(
                                AlarmConfigurePayload.TYPE,
                                AlarmConfigurePayload.CODEC,
                                AlarmConfigurePayload::handle);
                registrar.playToServer(
                                AlarmSoundRequestPayload.TYPE,
                                AlarmSoundRequestPayload.CODEC,
                                AlarmSoundRequestPayload::handle);
                registrar.playToClient(
                                AlarmSoundChunkPayload.TYPE,
                                AlarmSoundChunkPayload.CODEC,
                                AlarmSoundChunkPayload::handle);
                registrar.playToServer(
                                PressureModePayload.TYPE,
                                PressureModePayload.CODEC,
                                PressureModePayload::handle);
                registrar.playToServer(
                                ElectrolyzerTogglePayload.TYPE,
                                ElectrolyzerTogglePayload.CODEC,
                                ElectrolyzerTogglePayload::handle);
                registrar.playToClient(
                                HullConfigSyncPayload.TYPE,
                                HullConfigSyncPayload.CODEC,
                                HullConfigSyncPayload::handle);
                registrar.playToServer(
                                HullConfigEditPayload.TYPE,
                                HullConfigEditPayload.CODEC,
                                HullConfigEditPayload::handle);
                registrar.playToClient(
                                CameraShakePayload.TYPE,
                                CameraShakePayload.CODEC,
                                CameraShakePayload::handle);
                registrar.playToClient(
                                ImplosionFxPayload.TYPE,
                                ImplosionFxPayload.CODEC,
                                ImplosionFxPayload::handle);
                registrar.playToClient(
                                CableStrandRemovePayload.TYPE,
                                CableStrandRemovePayload.CODEC,
                                CableStrandRemovePayload::handle);
        }

        private void moveDivingGear(BuildCreativeModeTabContentsEvent event) {
                if (event.getTabKey() != AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
                        return;
                for (String name : List.of("copper_diving_helmet", "copper_diving_boots"))
                        event.remove(new ItemStack(createItem(name)),
                                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        private static Item createItem(String name) {
                return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", name));
        }

        private void registerCapabilities(RegisterCapabilitiesEvent event) {
                @SuppressWarnings("unchecked")
                BlockEntityType<RopeWinchBlockEntity> ropeWinchType = (BlockEntityType<RopeWinchBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .get(
                                                ResourceLocation
                                                                .fromNamespaceAndPath("simulated", "rope_winch"));
                if (ropeWinchType != null) {
                        event.registerBlockEntity(
                                        Capabilities.EnergyStorage.BLOCK,
                                        ropeWinchType,
                                        (be, side) -> CableElectrificationSystem
                                                        .getOrCreateStorage(be));
                }
                @SuppressWarnings("unchecked")
                BlockEntityType<RopeConnectorBlockEntity> ropeConnectorType = (BlockEntityType<RopeConnectorBlockEntity>) BuiltInRegistries.BLOCK_ENTITY_TYPE
                                .get(
                                                ResourceLocation
                                                                .fromNamespaceAndPath("simulated", "rope_connector"));
                if (ropeConnectorType != null) {
                        event.registerBlockEntity(
                                        Capabilities.EnergyStorage.BLOCK,
                                        ropeConnectorType,
                                        (be, side) -> CableElectrificationSystem
                                                        .getOrCreateStorage(be));
                }
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                BALLAST_TANK_BE.get(),
                                (be, side) -> be.getClusterFluidHandler(side));
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                BALLAST_VENT_BE.get(),
                                (be, side) -> be.getFluidHandlerForSide(side));
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                DECOMPRESSION_CHAMBER_BE.get(),
                                (be, side) -> be.getFluidHandlerForSide(side));
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> be.combinedFluidHandler);
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                OXYGENE_DIFFUSER_BE.get(),
                                (be, side) -> be.oxygenTank);
                event.registerBlockEntity(
                                Capabilities.FluidHandler.BLOCK,
                                WATER_THRUSTER_BE.get(),
                                (be, side) -> {
                                        if (side == null || side == be.getBlockState().getValue(
                                                        DirectionalBlock.FACING)
                                                        .getOpposite()) {
                                                return be.waterTank;
                                        }
                                        return null;
                                });
                event.registerBlockEntity(
                                        Capabilities.EnergyStorage.BLOCK,
                                ELECTROLYZER_BE.get(),
                                (be, side) -> {
                                        if (side != null && side != Direction.UP && side != Direction.DOWN)
                                                return be.energyStorage;
                                        return null;
                                });
                event.registerItem(
                                Capabilities.FluidHandler.ITEM,
                                (stack, ctx) -> new FluidBucketWrapper(stack),
                                OXYGEN_BUCKET.get());
        }

        private void onConfigLoaded(ModConfigEvent event) {
                if (event instanceof ModConfigEvent.Unloading)
                        return;
                if (event.getConfig().getSpec() == SubmarineConfig.COMMON_SPEC) {
                        OceanDepthOffset.refreshConfig();
                } else if (event.getConfig().getSpec() == SubmarineConfig.SERVER_SPEC) {
                        HullStrengthConfig.load();
                }
        }

        private void onCommonSetup(FMLCommonSetupEvent event) {
                event.enqueueWork(() -> {
                        HullStrengthConfig.load();
                        registerToSimulatedTab();
                        BacktankUtil.addBacktankSupplier(DivingSuitItem::carriedBacktanks);
                        BlockStressValues.IMPACTS.register(SUBMARINE_PROPELLER.get(),
                                        () -> 4.0);
                        BlockStressValues.IMPACTS.register(PUMP_CONTROLLER.get(),
                                        () -> 4.0);
                        BlockStressValues.IMPACTS.register(ELECTROLYZER.get(),
                                        () -> 8.0);
                        TooltipModifier.REGISTRY.register(
                                        PUMP_CONTROLLER_ITEM.get(),
                                        TooltipModifier.mapNull(
                                                        KineticStats
                                                                        .create(PUMP_CONTROLLER_ITEM.get())));
                        DisplaySource.BY_BLOCK_ENTITY.register(
                                        COMMAND_SUB_BE.get(),
                                        List.of(
                                                        SubmarineDisplaySources.COMMAND_SUB
                                                                        .get()));
                        TooltipModifier.REGISTRY.register(
                                        SUBMARINE_PROPELLER_ITEM.get(),
                                        TooltipModifier.mapNull(
                                                        KineticStats
                                                                        .create(SUBMARINE_PROPELLER_ITEM.get())));
                        DisplaySource.BY_BLOCK_ENTITY.register(
                                        BAROMETER_BE.get(),
                                        List.of(
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

                        ResourceLocation subSection = ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine");
                        tabItems.add(CREATIVE_OXYGENATOR_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "creative_oxygenator"), subSection);
                        tabItems.add(BALLAST_TANK_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_tank"), subSection);
                        tabItems.add(BALLAST_VENT_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "ballast_vent"), subSection);
                        tabItems.add(DECOMPRESSION_CHAMBER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "decompression_chamber"), subSection);
                        tabItems.add(OXYGENE_DIFFUSER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygene_diffuser"), subSection);
                        tabItems.add(ELECTROLYZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "electrolyzer"), subSection);
                        tabItems.add(WATER_THRUSTER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "water_thruster"), subSection);
                        tabItems.add(IRON_PRESSURIZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "iron_pressurizer"), subSection);
                        tabItems.add(COPPER_PRESSURIZER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "copper_pressurizer"), subSection);
                        tabItems.add(FLOATER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "floater"), subSection);
                        tabItems.add(STEEL_CABLE::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "steel_cable"), subSection);
                        tabItems.add(PULLEY_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pulley"), subSection);
                        tabItems.add(UNDERWATER_MINE_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "underwater_mine"), subSection);
                        tabItems.add(SUBMARINE_PROPELLER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "submarine_propeller"), subSection);
                        tabItems.add(BAROMETER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "barometer"), subSection);
                        tabItems.add(SONAR_PINGER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sonar_pinger"), subSection);
                        tabItems.add(HARPOON_GUN::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "harpoon_gun"), subSection);
                        tabItems.add(LEAK_DETECTOR::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "leak_detector"), subSection);
                        tabItems.add(WARDING_STAFF::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "warding_staff"), subSection);
                        tabItems.add(PURGE_STAFF::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "purge_staff"), subSection);
                        tabItems.add(COMMAND_SUB_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "command_sub"), subSection);
                        tabItems.add(SONAR_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "sonar"), subSection);
                        tabItems.add(INDUSTRIAL_ALARM_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "industrial_alarm"), subSection);
                        tabItems.add(PUMP_CONTROLLER_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pump_controller"), subSection);
                        tabItems.add(ARRESTING_HOOK_ITEM::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "arresting_hook"), subSection);
                        tabItems.add(PHYCOLOGICAL_MEMBRANE::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "phycological_membrane"), subSection);
                        tabItems.add(OXYGEN_BUCKET::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "oxygen_bucket"), subSection);
                        tabItems.add(PRESSURE_GOGGLES::get);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "pressure_goggles"), subSection);
                        tabItems.add(() -> createItem("copper_diving_helmet"));
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath("create", "copper_diving_helmet"), subSection);
                        tabItems.add(DIVING_CHESTPLATE);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "diving_chestplate"), subSection);
                        tabItems.add(DIVING_LEGGINGS);
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath(MOD_ID, "diving_leggings"), subSection);
                        tabItems.add(() -> createItem("copper_diving_boots"));
                        itemToSection.put(ResourceLocation.fromNamespaceAndPath("create", "copper_diving_boots"), subSection);
                } catch (ReflectiveOperationException | ClassCastException e) {
                        LOGGER.warn("Couldn't add our items to the Simulated creative section", e);
                }
        }
}
