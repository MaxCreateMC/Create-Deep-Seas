package com.maxenonyme.highseas.ponder;

import com.maxenonyme.highseas.CreateHighSeas;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class HighSeasPonderScenes {

    public static final ResourceLocation SAIL = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "white_sail");
    public static final ResourceLocation RUDDER = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "rudder");

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(SAIL)
                .addStoryBoard("sail", HighSeasPonderScenes::sail)
                .addStoryBoard("sail_lateen", HighSeasPonderScenes::lateen);

        helper.forComponents(RUDDER)
                .addStoryBoard("rudder", HighSeasPonderScenes::rudder);
    }

    public static void sail(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sail", "Sailing With the Wind");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.8f);

        BlockPos middle = util.grid().at(3, 5, 2);
        BlockPos bottom = util.grid().at(3, 4, 2);
        Selection sea = util.select().layer(1)
                .substract(util.select().fromTo(2, 1, 1, 4, 1, 5))
                .substract(util.select().position(3, 1, 6));
        Selection hull = util.select().fromTo(2, 1, 1, 4, 2, 5).add(util.select().position(3, 1, 6));
        Selection mast = util.select().fromTo(3, 3, 3, 3, 6, 3);
        Selection lowRow = util.select().fromTo(2, 4, 2, 4, 4, 2);
        Selection midRow = util.select().fromTo(2, 5, 2, 4, 5, 2);
        Selection topRow = util.select().fromTo(2, 6, 2, 4, 6, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(sea, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(hull, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(mast, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(topRow, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(midRow, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(lowRow, Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(80)
                .text("create_high_seas.ponder.sail.text_1")
                .pointAt(util.vector().blockSurface(middle, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().of(3.5, 5.5, 2), util.vector().of(3.5, 5.5, -1), 90);
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail.text_2")
                .pointAt(util.vector().centerOf(3, 4, 3))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        for (int i = 0; i < 3; i++) {
            double x = 2 + i;
            double y = 4.5 + (i % 2);
            scene.overlay().showLine(PonderPalette.WHITE, util.vector().of(x, y, 7), util.vector().of(x, y, 3.5), 90);
        }
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail.text_3")
                .pointAt(util.vector().blockSurface(middle, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail.text_4")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().blockSurface(bottom, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        ItemStack rope = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("simulated", "rope_coupling")));
        scene.overlay().showControls(util.vector().blockSurface(middle, Direction.NORTH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(rope);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail.text_5")
                .pointAt(util.vector().blockSurface(middle, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.world().hideSection(topRow, Direction.DOWN);
        scene.idle(4);
        scene.world().hideSection(midRow, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail.text_6")
                .pointAt(util.vector().blockSurface(bottom, Direction.NORTH))
                .placeNearTarget();
        scene.idle(100);
        scene.world().showSection(midRow, Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(topRow, Direction.DOWN);
        scene.idle(20);

        BlockState red = CreateHighSeas.SAILS.get(DyeColor.RED).get().defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        scene.overlay().showControls(util.vector().blockSurface(middle, Direction.NORTH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(Items.RED_DYE));
        scene.idle(10);
        scene.world().setBlock(middle, red, false);
        scene.overlay().showText(70)
                .text("create_high_seas.ponder.sail.text_7")
                .pointAt(util.vector().blockSurface(middle, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().blockSurface(middle, Direction.NORTH), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(Items.RED_DYE));
        scene.idle(10);
        scene.world().setBlocks(util.select().fromTo(2, 4, 2, 4, 6, 2), red, false);
        scene.overlay().showText(80)
                .text("create_high_seas.ponder.sail.text_8")
                .pointAt(util.vector().blockSurface(middle, Direction.NORTH))
                .placeNearTarget();
        scene.idle(90);

        scene.markAsFinished();
    }

    public static void lateen(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("sail_lateen", "Triangle Sails Along the Deck");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.8f);

        Selection sea = util.select().layer(1)
                .substract(util.select().fromTo(2, 1, 1, 4, 1, 5))
                .substract(util.select().position(3, 1, 6));
        Selection hull = util.select().fromTo(2, 1, 1, 4, 2, 5).add(util.select().position(3, 1, 6));
        Selection mast = util.select().fromTo(3, 3, 4, 3, 7, 4);
        Selection steps = util.select().position(3, 3, 0)
                .add(util.select().position(3, 4, 1))
                .add(util.select().position(3, 5, 2))
                .add(util.select().position(3, 6, 3));
        BlockPos middle = util.grid().at(3, 4, 2);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(sea, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(hull, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(mast, Direction.DOWN);
        scene.idle(10);
        for (int y = 3; y <= 6; y++) {
            scene.world().showSection(util.select().fromTo(3, y, 0, 3, y, 3), Direction.DOWN);
            scene.idle(4);
        }
        scene.idle(16);

        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail_lateen.text_1")
                .pointAt(util.vector().blockSurface(middle, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showOutline(PonderPalette.WHITE, new Object(), steps, 90);
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail_lateen.text_2")
                .pointAt(util.vector().blockSurface(util.grid().at(3, 4, 1), Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        for (int i = 0; i < 3; i++) {
            double y = 3.5 + i;
            double z = 1.5 + i * 0.8;
            scene.overlay().showLine(PonderPalette.WHITE, util.vector().of(-1.0, y, z), util.vector().of(2.6, y, z), 90);
        }
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail_lateen.text_3")
                .pointAt(util.vector().blockSurface(middle, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().of(3.5, 3.0, 2.0), util.vector().of(3.5, 3.0, -1.0), 90);
        scene.overlay().showText(90)
                .text("create_high_seas.ponder.sail_lateen.text_4")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().of(3.5, 3.0, -0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.markAsFinished();
    }

    public static void rudder(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("rudder", "Steering With a Rudder");
        scene.configureBasePlate(0, 0, 7);
        scene.scaleSceneView(0.8f);

        BlockPos top = util.grid().at(3, 2, 0);
        BlockPos low = util.grid().at(3, 1, 0);
        BlockPos bearing = util.grid().at(3, 3, 1);
        Selection blades = util.select().fromTo(3, 1, 0, 3, 2, 1);
        Selection sea = util.select().layers(1, 2)
                .substract(util.select().fromTo(2, 1, 2, 4, 2, 6))
                .substract(util.select().fromTo(3, 1, 0, 3, 2, 1));

        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);
        scene.world().showSection(sea, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 4, 3, 6), Direction.DOWN);
        scene.idle(15);
        ElementLink<WorldSectionElement> blade = scene.world().showIndependentSection(blades, Direction.DOWN);
        scene.world().configureCenterOfRotation(blade, util.vector().of(3.5, 2, 1.5));
        scene.idle(20);

        scene.overlay().showText(90)
                .text("create_high_seas.ponder.rudder.text_1")
                .pointAt(util.vector().blockSurface(top, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("create_high_seas.ponder.rudder.text_2")
                .pointAt(util.vector().blockSurface(top, Direction.NORTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showText(90)
                .text("create_high_seas.ponder.rudder.text_3")
                .colored(PonderPalette.BLUE)
                .pointAt(util.vector().blockSurface(low, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showControls(util.vector().blockSurface(top, Direction.WEST), Pointing.RIGHT, 40)
                .rightClick()
                .withItem(new ItemStack(Items.SPRUCE_PLANKS));
        scene.idle(10);
        scene.world().modifyBlockEntityNBT(blades, CopycatBlockEntity.class,
                nbt -> nbt.put("Material", NbtUtils.writeBlockState(Blocks.SPRUCE_PLANKS.defaultBlockState())), true);
        scene.overlay().showText(80)
                .text("create_high_seas.ponder.rudder.text_4")
                .pointAt(util.vector().blockSurface(top, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(bearing), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(110)
                .text("create_high_seas.ponder.rudder.text_5")
                .pointAt(util.vector().blockSurface(bearing, Direction.WEST))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);
        scene.world().rotateSection(blade, 0, 30, 0, 20);
        scene.idle(35);
        scene.world().rotateSection(blade, 0, -60, 0, 30);
        scene.idle(45);
        scene.world().rotateSection(blade, 0, 30, 0, 20);
        scene.idle(30);

        scene.markAsFinished();
    }
}
