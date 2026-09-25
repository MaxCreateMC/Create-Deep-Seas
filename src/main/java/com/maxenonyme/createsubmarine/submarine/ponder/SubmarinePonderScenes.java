package com.maxenonyme.createsubmarine.submarine.ponder;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.block.entity.CommandSubBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.maxenonyme.createsubmarine.submarine.block.ElectrolyzerBlock;
import com.maxenonyme.createsubmarine.submarine.block.PulleyBlock;
import com.maxenonyme.createsubmarine.submarine.block.entity.PulleyBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.simulated_team.simulated.ponder.instructions.CreateRopeStrandInstruction;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class SubmarinePonderScenes {

        public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "ballast_vent"))
                                .addStoryBoard("ballast_vent", SubmarinePonderScenes::ballastVent);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "oxygene_diffuser"))
                                .addStoryBoard("oxygen_diffuser", SubmarinePonderScenes::oxygeneDiffuser);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "electrolyzer"))
                                .addStoryBoard("electrolyzer", SubmarinePonderScenes::electrolyzer);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "command_sub"),
                                ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pump_controller"))
                                .addStoryBoard("onboard_computer", SubmarinePonderScenes::onboardComputer);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "water_thruster"))
                                .addStoryBoard("water_thruster", SubmarinePonderScenes::waterThruster);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pulley"))
                                .addStoryBoard("steel_cable", SubmarinePonderScenes::pulley);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "steel_cable"))
                                .addStoryBoard("steel_cable", SubmarinePonderScenes::steelCable)
                                .addStoryBoard("steel_cable", SubmarinePonderScenes::steelCableElectrified);

                helper.forComponents(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "barometer"))
                                .addStoryBoard("barometre1", SubmarinePonderScenes::barometer)
                                .addStoryBoard("barometre2", SubmarinePonderScenes::barometerDisplayLink);
        }

        public static void ballastVent(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("ballast_vent", "Ballast Vent Usage");
                scene.configureBasePlate(0, 0, 7);
                scene.scaleSceneView(0.8f);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(15);

                scene.world().showSection(util.select().fromTo(2, 1, 5, 4, 1, 5), Direction.DOWN);
                scene.idle(20);

                scene.addKeyframe();
                scene.overlay().showText(60)
                                .text("create_submarine.ponder.ballast_vent.text_1")
                                .pointAt(util.vector().centerOf(6, 1, 6))
                                .attachKeyFrame();

                scene.world().showSection(util.select().fromTo(5, 1, 6, 6, 2, 6).add(util.select().position(6, 2, 5)),
                                Direction.DOWN);
                scene.idle(15);
                scene.world().showSection(util.select().fromTo(4, 3, 4, 6, 3, 4), Direction.WEST);
                scene.idle(55);

                scene.addKeyframe();
                scene.overlay().showText(60)
                                .text("create_submarine.ponder.ballast_vent.text_2")
                                .pointAt(util.vector().centerOf(3, 1, 4))
                                .placeNearTarget();

                scene.world().showSection(util.select().fromTo(3, 1, 4, 3, 2, 4), Direction.DOWN);
                scene.idle(65);

                scene.addKeyframe();
                BlockPos ventPos = util.grid().at(3, 3, 4);
                BlockPos leverPos = util.grid().at(3, 3, 3);

                scene.world().showSection(util.select().position(ventPos), Direction.SOUTH);
                scene.idle(15);
                scene.world().showSection(util.select().position(leverPos), Direction.SOUTH);
                scene.idle(15);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.ballast_vent.text_3")
                                .pointAt(util.vector().topOf(ventPos))
                                .placeNearTarget();

                for (int i = 0; i <= 15; i++) {
                        final int value = i;
                        scene.world().modifyBlockEntityNBT(util.select().position(leverPos), BlockEntity.class, nbt -> {
                                nbt.putInt("State", value);
                        });
                        scene.idle(2);
                }

                scene.idle(60);

                scene.addKeyframe();
                scene.overlay().showText(80)
                                .text("create_submarine.ponder.ballast_vent.text_4")
                                .pointAt(util.vector().blockSurface(ventPos, Direction.NORTH))
                                .placeNearTarget();
                scene.idle(90);

                scene.world().setKineticSpeed(util.select().everywhere(), 64);

                scene.idle(40);

                scene.addKeyframe();
                scene.world().modifyKineticSpeed(util.select().everywhere(), f -> -f);

                scene.overlay().showText(90)
                                .text("create_submarine.ponder.ballast_vent.text_5")
                                .pointAt(util.vector().blockSurface(ventPos, Direction.NORTH))
                                .placeNearTarget();

                scene.idle(100);
                scene.markAsFinished();
        }

        public static void oxygeneDiffuser(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("oxygen_diffuser", "Oxygen Diffuser Usage");
                scene.configureBasePlate(0, 0, 6);
                scene.scaleSceneView(0.8f);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(15);

                scene.world().showSection(util.select().fromTo(3, 1, 4, 3, 3, 4), Direction.DOWN);

                scene.world().modifyBlockEntityNBT(util.select().fromTo(3, 1, 4, 3, 3, 4),
                                FluidTankBlockEntity.class, nbt -> {
                                        nbt.putBoolean("Window", true);
                                });

                scene.world().modifyBlockEntity(util.grid().at(3, 1, 4),
                                FluidTankBlockEntity.class, be -> {
                                        be.getTankInventory().fill(
                                                        new FluidStack(
                                                                        CreateSubmarine.OXYGEN.get(), 16000),
                                                        IFluidHandler.FluidAction.EXECUTE);
                                });

                scene.idle(15);

                scene.addKeyframe();
                scene.overlay().showText(70)
                                .text("create_submarine.ponder.oxygen_diffuser.text_1")
                                .pointAt(util.vector().blockSurface(util.grid().at(3, 2, 4), Direction.WEST))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(80);

                scene.world().showSection(util.select().position(3, 1, 3), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(3, 1, 2), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(3, 1, 1), Direction.DOWN);
                scene.idle(20);

                scene.addKeyframe();

                scene.world().showSection(util.select().position(4, 2, 2), Direction.WEST);
                scene.world().showSection(util.select().position(5, 3, 2), Direction.WEST);
                scene.idle(10);
                scene.world().setKineticSpeed(util.select().everywhere(), 64);
                scene.idle(20);

                scene.overlay().showText(70)
                                .text("create_submarine.ponder.oxygen_diffuser.text_2")
                                .pointAt(util.vector().topOf(3, 1, 1))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(80);

                scene.addKeyframe();

                scene.overlay().showText(70)
                                .text("create_submarine.ponder.oxygen_diffuser.text_3")
                                .pointAt(util.vector().topOf(3, 1, 1))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(80);

                for (int i = 0; i < 40; i++) {
                        scene.effects().emitParticles(
                                        util.vector().topOf(3, 1, 1).add(0, 0.2, 0),
                                        scene.effects().simpleParticleEmitter(
                                                        ParticleTypes.BUBBLE,
                                                        new Vec3(0, 0.1, 0)),
                                        1.0f,
                                        5);
                        scene.idle(2);
                }

                scene.markAsFinished();
        }

        public static void electrolyzer(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("electrolyzer", "Electrolyzer Usage");
                scene.configureBasePlate(0, 0, 9);
                scene.scaleSceneView(0.6f);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(10);

                scene.world().showSection(util.select().fromTo(1, 1, 4, 1, 2, 4), Direction.DOWN);

                scene.world().modifyBlockEntityNBT(util.select().fromTo(1, 1, 4, 1, 2, 4),
                                FluidTankBlockEntity.class, nbt -> {
                                        nbt.putBoolean("Window", true);
                                });
                scene.world().modifyBlockEntity(util.grid().at(1, 1, 4),
                                FluidTankBlockEntity.class, be -> {
                                        be.getTankInventory()
                                                        .fill(new FluidStack(
                                                                        Fluids.WATER,
                                                                        16000),
                                                                        IFluidHandler.FluidAction.EXECUTE);
                                });

                scene.idle(10);

                scene.addKeyframe();
                scene.overlay().showText(70)
                                .text("create_submarine.ponder.electrolyzer.text_1")
                                .pointAt(util.vector().blockSurface(util.grid().at(1, 2, 4), Direction.UP))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(80);

                scene.world().showSection(util.select().position(2, 1, 4), Direction.DOWN);
                scene.idle(3);
                scene.world().showSection(util.select().position(3, 1, 4), Direction.DOWN);
                scene.idle(3);

                scene.world().showSection(util.select().position(4, 1, 4), Direction.DOWN);
                scene.idle(10);

                scene.world().showSection(util.select().position(5, 1, 4), Direction.DOWN);
                scene.idle(3);
                scene.world().showSection(util.select().position(6, 1, 4), Direction.DOWN);
                scene.idle(10);

                scene.world().showSection(util.select().fromTo(7, 1, 4, 7, 2, 4), Direction.DOWN);
                scene.world().modifyBlockEntityNBT(util.select().fromTo(7, 1, 4, 7, 2, 4),
                                FluidTankBlockEntity.class, nbt -> {
                                        nbt.putBoolean("Window", true);
                                });

                scene.world().modifyBlockEntity(util.grid().at(7, 1, 4),
                                FluidTankBlockEntity.class, be -> {
                                        be.getTankInventory().drain(16000,
                                                        IFluidHandler.FluidAction.EXECUTE);
                                });
                scene.idle(15);

                scene.addKeyframe();

                Selection machinesZ4 = util.select().fromTo(1, 1, 4, 7, 2, 4);
                scene.world().showSection(util.select().layersFrom(1).substract(machinesZ4), Direction.NORTH);
                scene.idle(10);

                scene.overlay().showText(70)
                                .text("create_submarine.ponder.electrolyzer.text_2")
                                .pointAt(util.vector().topOf(4, 1, 4))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(80);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.electrolyzer.text_4")
                                .pointAt(util.vector().topOf(4, 1, 4))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(50);

                scene.addKeyframe();

                scene.world().setKineticSpeed(util.select().everywhere(), 64);
                scene.idle(10);

                scene.idle(10);

                scene.world().modifyBlock(
                                util.grid().at(4, 1, 4), state -> state
                                                .setValue(BlockStateProperties.POWERED,
                                                                true),
                                false);
                scene.idle(10);

                scene.idle(10);

                for (int i = 0; i < 40; i++) {
                        scene.world().modifyBlockEntity(util.grid().at(1, 1, 4),
                                        FluidTankBlockEntity.class, be -> {
                                                be.getTankInventory().drain(200,
                                                                IFluidHandler.FluidAction.EXECUTE);
                                        });
                        scene.world().modifyBlockEntity(util.grid().at(7, 1, 4),
                                        FluidTankBlockEntity.class, be -> {
                                                be.getTankInventory()
                                                                .fill(new FluidStack(
                                                                                CreateSubmarine.OXYGEN
                                                                                                .get(),
                                                                                200),
                                                                                IFluidHandler.FluidAction.EXECUTE);
                                        });
                        scene.idle(2);
                }

                scene.addKeyframe();
                scene.overlay().showText(80)
                                .text("create_submarine.ponder.electrolyzer.text_3")
                                .pointAt(util.vector().blockSurface(util.grid().at(7, 2, 4), Direction.WEST))
                                .placeNearTarget();
                scene.idle(90);

                BlockPos electrolyzer = util.grid().at(4, 1, 4);
                BlockPos shaftPos = util.grid().at(4, 1, 3);
                BlockPos motorPos = util.grid().at(4, 1, 2);

                scene.addKeyframe();
                scene.overlay().showControls(util.vector().topOf(electrolyzer), Pointing.DOWN, 60)
                                .withItem(new ItemStack(
                                                BuiltInRegistries.ITEM.get(
                                                                ResourceLocation.fromNamespaceAndPath("create", "electron_tube"))));
                scene.idle(10);
                scene.world().modifyBlock(electrolyzer,
                                state -> state.setValue(ElectrolyzerBlock.ALTERNATOR, true),
                                false);
                scene.overlay().showText(80)
                                .text("create_submarine.ponder.electrolyzer.text_5")
                                .pointAt(util.vector().blockSurface(electrolyzer, Direction.NORTH))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(90);

                scene.world().setBlock(shaftPos,
                                BuiltInRegistries.BLOCK
                                                .get(ResourceLocation.fromNamespaceAndPath("create", "shaft"))
                                                .defaultBlockState()
                                                .setValue(BlockStateProperties.AXIS,
                                                                Direction.Axis.Z),
                                true);
                scene.world().setBlock(motorPos,
                                BuiltInRegistries.BLOCK
                                                .get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"))
                                                .defaultBlockState()
                                                .setValue(BlockStateProperties.FACING,
                                                                Direction.SOUTH),
                                true);
                scene.world().setKineticSpeed(util.select().fromTo(4, 1, 2, 4, 1, 4), 64);
                scene.idle(15);

                scene.overlay().showText(90)
                                .text("create_submarine.ponder.electrolyzer.text_6")
                                .pointAt(util.vector().topOf(shaftPos))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);

                scene.markAsFinished();
        }

        public static void onboardComputer(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("onboard_computer", "Diving with the Onboard Computer");
                scene.configureBasePlate(-3, 0, 10);
                scene.scaleSceneView(0.7f);

                BlockPos computer = util.grid().at(2, 2, 1);
                BlockPos vent = util.grid().at(2, 1, 3);
                BlockPos pump = util.grid().at(2, 2, 4);
                Selection kinetics = util.select().fromTo(2, 2, 4, 3, 2, 5);

                scene.world().modifyBlockEntity(computer, CommandSubBlockEntity.class, be -> {
                        be.targetDepth = 0;
                        be.surfaceY = 2;
                        be.syncedFill = -1;
                        be.syncedPumps = 0;
                        be.syncedStatus = 1;
                });
                scene.world().setKineticSpeed(kinetics, 0);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(10);
                for (int z = 0; z <= 9; z++) {
                        scene.world().showSection(util.select().fromTo(0, 1, z, 3, 1, z), Direction.UP);
                        scene.idle(2);
                }
                scene.idle(10);
                scene.world().showSection(util.select().position(computer), Direction.DOWN);
                scene.idle(15);
                for (int depth = 2; depth <= 20; depth += 2) {
                        final int shown = depth;
                        scene.world().modifyBlockEntity(computer, CommandSubBlockEntity.class, be -> be.targetDepth = shown);
                        scene.idle(2);
                }

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.onboard_computer.text_1")
                                .pointAt(util.vector().blockSurface(computer, Direction.WEST))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(90);

                for (int z = 6; z <= 9; z++) {
                        scene.world().showSection(util.select().fromTo(1, 2, z, 2, 2, z), Direction.DOWN);
                        scene.idle(4);
                }
                scene.world().modifyBlockEntity(computer, CommandSubBlockEntity.class, be -> be.syncedFill = 50);
                scene.idle(10);

                scene.overlay().showText(60)
                                .text("create_submarine.ponder.onboard_computer.text_7")
                                .pointAt(util.vector().topOf(1, 2, 7))
                                .placeNearTarget();
                scene.idle(70);

                scene.world().showSection(util.select().position(2, 2, 3), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(pump), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().position(2, 2, 5), Direction.DOWN);
                scene.idle(5);
                scene.world().showSection(util.select().fromTo(3, 2, 4, 3, 2, 5), Direction.DOWN);
                scene.idle(10);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.onboard_computer.text_2")
                                .pointAt(util.vector().topOf(pump))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(90);

                scene.overlay().showText(90)
                                .text("create_submarine.ponder.onboard_computer.text_3")
                                .pointAt(util.vector().topOf(vent))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);

                scene.world().setKineticSpeed(kinetics, 32);
                scene.world().setKineticSpeed(util.select().position(pump), -32);
                scene.world().modifyBlockEntity(computer, CommandSubBlockEntity.class, be -> be.syncedPumps = 1);
                scene.idle(10);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.onboard_computer.text_4")
                                .pointAt(util.vector().blockSurface(computer, Direction.WEST))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(20);

                for (int i = 1; i <= 40; i++) {
                        final int step = i;
                        scene.world().modifyBlockEntity(computer, CommandSubBlockEntity.class, be -> {
                                be.surfaceY = 2 + step / 2;
                                if (step <= 15) {
                                        be.syncedStatus = 2;
                                        be.syncedFill = 50 + step;
                                } else if (step <= 30) {
                                        be.syncedStatus = 0;
                                        be.syncedFill = 65 - (step - 15);
                                } else {
                                        be.syncedStatus = 1;
                                        be.syncedFill = 50;
                                }
                        });
                        scene.idle(3);
                }
                scene.idle(20);

                scene.overlay().showText(90)
                                .text("create_submarine.ponder.onboard_computer.text_5")
                                .pointAt(util.vector().blockSurface(computer, Direction.WEST))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(100);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.onboard_computer.text_6")
                                .pointAt(util.vector().topOf(pump))
                                .placeNearTarget()
                                .attachKeyFrame();
                scene.idle(90);

                scene.markAsFinished();
        }

        public static void waterThruster(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("water_thruster", "Propulsion par Water Thruster");
                scene.configureBasePlate(0, 0, 7);
                scene.scaleSceneView(0.8f);

                Selection iceFloor = util.select().fromTo(0, 0, 0, 4, 0, 4);

                List<ElementLink<WorldSectionElement>> iceSegments = new ArrayList<>();
                for (int i = -9; i <= 3; i++) {
                        ElementLink<WorldSectionElement> segment = scene.world().showIndependentSection(iceFloor,
                                        Direction.UP);
                        scene.world().moveSection(segment, new Vec3(i * 5, 0, 1), 0);
                        iceSegments.add(segment);
                }

                Selection raftSelection = util.select().layersFrom(1);
                ElementLink<WorldSectionElement> raft = scene.world().showIndependentSection(raftSelection,
                                Direction.DOWN);
                scene.idle(20);

                scene.addKeyframe();
                scene.overlay().showText(70)
                                .text("create_submarine.ponder.water_thruster.text_1")
                                .pointAt(util.vector().centerOf(3, 2, 3))
                                .placeNearTarget();

                scene.idle(80);

                scene.overlay().showText(70)
                                .text("create_submarine.ponder.water_thruster.text_4")
                                .pointAt(util.vector().centerOf(3, 2, 4))
                                .placeNearTarget();

                scene.idle(80);

                scene.addKeyframe();
                scene.overlay().showText(70)
                                .text("create_submarine.ponder.water_thruster.text_2")
                                .pointAt(util.vector().centerOf(3, 2, 3))
                                .placeNearTarget();

                scene.idle(20);
                scene.world().setKineticSpeed(util.select().everywhere(), 64);
                scene.idle(20);

                int totalTicks = 120;
                for (ElementLink<WorldSectionElement> segment : iceSegments) {
                        scene.world().moveSection(segment, new Vec3(35, 0, 0), totalTicks);
                }

                scene.idle(totalTicks + 20);

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.water_thruster.text_3")
                                .pointAt(util.vector().centerOf(3, 2, 3))
                                .placeNearTarget();

                scene.idle(90);
                scene.markAsFinished();
        }

        public static void steelCable(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("steel_cable", "Steel Cable");
                scene.configureBasePlate(0, 0, 7);
                scene.scaleSceneView(0.65f);

                scene.world().showSection(util.select().everywhere(), Direction.UP);
                scene.idle(20);

                Vec3 from = new Vec3(0.5, 4.0, 2.5);
                Vec3 to = new Vec3(12.5, 4.0, 2.5);
                SteelCablePonderElement rope = new SteelCablePonderElement(from, to, 12.0, 0.3, 1.0f);
                scene.addInstruction(
                                new CreateRopeStrandInstruction(rope));

                scene.idle(10);
                scene.addKeyframe();

                ElementLink<EntityElement> villager = scene.world().createEntity(level -> {
                        Villager v = new Villager(
                                        EntityType.VILLAGER, level);
                        v.setPos(1.5, 4.1, 2.5);
                        v.setYRot(-90f);
                        return v;
                });

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.steel_cable.text_1")
                                .pointAt(util.vector().of(6.5, 2.8, 2.5))
                                .placeNearTarget()
                                .attachKeyFrame();

                for (int i = 0; i <= 60; i++) {
                        final double x = 1.5 + i * (10.0 / 60.0);
                        scene.world().modifyEntity(villager, e -> e.setPos(x, 4.1, 2.5));
                        scene.idle(1);
                }

                scene.idle(20);
                scene.markAsFinished();
        }

        public static void steelCableElectrified(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("steel_cable_electrified", "Steel Cable — Electrification");
                scene.configureBasePlate(0, 0, 7);
                scene.scaleSceneView(0.65f);

                scene.world().showSection(util.select().everywhere(), Direction.UP);
                scene.idle(20);

                Vec3 from = new Vec3(0.5, 4.0, 2.5);
                Vec3 to = new Vec3(12.5, 4.0, 2.5);
                SteelCablePonderElement rope = new SteelCablePonderElement(from, to, 12.0, 0.3, 1.0f);
                scene.addInstruction(
                                new CreateRopeStrandInstruction(rope));

                scene.idle(10);
                scene.addKeyframe();

                scene.overlay().showText(100)
                                .text("create_submarine.ponder.steel_cable_electrified.text_1")
                                .pointAt(util.vector().of(6.5, 4.8, 2.5))
                                .placeNearTarget()
                                .attachKeyFrame();

                for (int tick = 0; tick < 80; tick++) {
                        final double t = (tick % 12) / 12.0;
                        final double sx = 0.5 + t * 12.0;
                        scene.addInstruction(ponderScene -> {
                                ponderScene.getWorld().addParticle(
                                                ParticleTypes.ELECTRIC_SPARK,
                                                sx, 4.0, 2.5, 0, 0.05, 0);
                        });
                        scene.idle(1);
                }

                scene.idle(10);
                scene.markAsFinished();
        }

        public static void pulley(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("pulley", "Pulley");
                scene.configureBasePlate(0, 0, 13);
                scene.scaleSceneView(0.65f);

                Selection worldWithoutPulleys = util.select().fromTo(0, 0, 0, 12, 2, 4)
                        .add(util.select().fromTo(0, 3, 0, 0, 6, 4))
                        .add(util.select().fromTo(4, 3, 0, 12, 6, 4))
                        .add(util.select().fromTo(1, 4, 0, 3, 6, 4))
                        .add(util.select().fromTo(1, 3, 0, 3, 3, 1))
                        .add(util.select().fromTo(1, 3, 3, 3, 3, 4));
                scene.world().showSection(worldWithoutPulleys, Direction.UP);
                scene.idle(20);


                Vec3 from = new Vec3(0.5, 4.0, 2.5);
                Vec3 to   = new Vec3(12.5, 4.0, 2.5);
                SteelCablePonderElement rope = new SteelCablePonderElement(from, to, 12.0, 0.3, 1.0f);
                scene.addInstruction(
                        new CreateRopeStrandInstruction(rope));

                scene.idle(10);


                BlockPos posA = util.grid().at(1, 3, 2);
                BlockPos posB = util.grid().at(3, 3, 2);
                scene.world().setBlock(posA,
                        CreateSubmarine.PULLEY.get().defaultBlockState()
                                .setValue(PulleyBlock.FACING, Direction.NORTH),
                        false);
                scene.world().setBlock(posB,
                        CreateSubmarine.PULLEY.get().defaultBlockState()
                                .setValue(PulleyBlock.FACING, Direction.SOUTH),
                        false);

                ElementLink<WorldSectionElement> pulleySection = scene.world().showIndependentSection(
                        util.select().fromTo(1, 3, 2, 3, 3, 2), Direction.DOWN);

                scene.world().configureCenterOfRotation(pulleySection, util.vector().of(2, 3, 2));
                scene.world().rotateSection(pulleySection, 0, 0, 90, 0);

                scene.idle(15);
                scene.addKeyframe();

                scene.overlay().showText(80)
                        .text("create_submarine.ponder.poulis.text_1")
                        .pointAt(util.vector().of(2.5, 4.5, 2.5))
                        .placeNearTarget()
                        .attachKeyFrame();

                scene.idle(30);


                final float anglePerTick = (float)(0.08 / 0.6875);
                scene.world().moveSection(pulleySection, new Vec3(8, 0, 0), 100);
                for (int i = 0; i < 100; i++) {
                        scene.world().modifyBlockEntity(posA,
                                PulleyBlockEntity.class,
                                be -> be.clientWheelAngle += anglePerTick);
                        scene.world().modifyBlockEntity(posB,
                                PulleyBlockEntity.class,
                                be -> be.clientWheelAngle += anglePerTick);
                        scene.idle(1);
                }
                scene.idle(10);

                scene.addKeyframe();
                scene.overlay().showText(80)
                        .text("create_submarine.ponder.poulis.text_2")
                        .pointAt(util.vector().of(9.5, 4.5, 2.5))
                        .placeNearTarget()
                        .attachKeyFrame();

                scene.idle(90);
                scene.markAsFinished();
        }

        public static void barometer(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("barometer", "Barometer Usage");
                scene.configureBasePlate(0, 0, 5);
                scene.scaleSceneView(0.8f);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(15);

                scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
                scene.idle(20);

                scene.addKeyframe();

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.barometer.text_1")
                                .pointAt(util.vector().centerOf(2, 2, 2))
                                .placeNearTarget()
                                .attachKeyFrame();

                scene.idle(90);

                scene.addKeyframe();

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.barometer.text_2")
                                .pointAt(util.vector().centerOf(2, 2, 2))
                                .placeNearTarget()
                                .attachKeyFrame();

                scene.idle(90);

                scene.addKeyframe();

                scene.overlay().showText(80)
                                .text("create_submarine.ponder.barometer.text_3")
                                .pointAt(util.vector().centerOf(2, 2, 2))
                                .placeNearTarget()
                                .attachKeyFrame();

                scene.idle(90);

                scene.markAsFinished();
        }

        public static void barometerDisplayLink(SceneBuilder builder, SceneBuildingUtil util) {
                CreateSceneBuilder scene = new CreateSceneBuilder(builder);

                scene.title("barometer_display_link", "Display Link Integration");
                scene.configureBasePlate(0, 0, 7);
                scene.scaleSceneView(0.7f);

                scene.world().showSection(util.select().layer(0), Direction.UP);
                scene.idle(15);

                scene.world().showSection(util.select().layersFrom(1).substract(util.select().position(2, 2, 5)).substract(util.select().fromTo(3, 1, 0, 3, 2, 2)), Direction.DOWN);
                scene.idle(20);

                scene.addKeyframe();

                scene.overlay().showText(70)
                                .text("create_submarine.ponder.barometer_display_link.text_1")
                                .pointAt(util.vector().blockSurface(util.grid().at(2, 2, 4), Direction.SOUTH))
                                .placeNearTarget()
                                .attachKeyFrame();

                scene.idle(60);

                scene.world().showSection(util.select().position(2, 2, 5), Direction.NORTH);
                scene.idle(20);

                scene.addKeyframe();

                scene.world().showSection(util.select().fromTo(3, 1, 0, 3, 2, 2), Direction.SOUTH);
                scene.idle(20);

                scene.overlay().showOutline(PonderPalette.GREEN, new Object(), util.select().fromTo(3, 1, 0, 3, 2, 2), 80);
                scene.overlay().showText(80)
                                .text("create_submarine.ponder.barometer_display_link.text_2")
                                .pointAt(util.vector().centerOf(3, 2, 1))
                                .placeNearTarget()
                                .attachKeyFrame();

                scene.world().flashDisplayLink(util.grid().at(2, 2, 5));
                scene.idle(5);

                scene.addKeyframe();

                for (int y = 1; y <= 2; y++) {
                    for (int z = 0; z <= 2; z++) {
                        scene.world().setDisplayBoardText(util.grid().at(3, y, z), 1, Component.translatable("create_submarine.ponder.barometer_display_link.text_3"));
                    }
                }
                scene.idle(90);

                scene.markAsFinished();
        }
}