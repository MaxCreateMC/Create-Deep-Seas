package com.maxenonyme.createsubmarine.submarine.client.renderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.minecraft.resources.ResourceLocation;
public class AllPartialModels {
    public static final PartialModel PUMP_CONTROLLER_COG = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/pump_controller/cog"));
    public static final PartialModel BALLAST_WHEEL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/ballast_vent/gear"));
    public static final PartialModel ELECTROLYZER_GLASS = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/electrolyzer_glass"));
    public static final PartialModel STEEL_CABLE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/steel_cable"));
    public static final PartialModel STEEL_CABLE_KNOT = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/knot"));
    public static final PartialModel PULLEY_CORE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/pulley_core"));
    public static final PartialModel ARRESTING_HOOK_ARM = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/arresting_hook/arm"));
    public static final PartialModel WINCH_STEEL_COIL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/winch_steel_coil"));
    public static final PartialModel CONNECTOR_STEEL_KNOT = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/connector_steel_knot"));
    public static final PartialModel SUBMARINE_PROPELLER = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/submarine_propeller/propeller"));
    public static final PartialModel SUBMARINE_PROPELLER_REVERSED = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/submarine_propeller/propeller_reversed"));
    public static final PartialModel SUBMARINE_PROPELLER_CONTRA = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/submarine_propeller/propeller_contra"));
    public static final PartialModel SUBMARINE_PROPELLER_REVERSED_CONTRA = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/submarine_propeller/propeller_reversed_contra"));
    public static final PartialModel SUBMARINE_STAFF_BASE_MODEL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_base_model"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_1 = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_1"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_2 = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_2"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_3 = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_3"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_4 = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_4"));

    public static final PartialModel SUBMARINE_STAFF_BASE_MODEL_CRYSTAL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_base_model_crystal"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_1_CRYSTAL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_1_crystal"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_2_CRYSTAL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_2_crystal"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_3_CRYSTAL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_3_crystal"));
    public static final PartialModel SUBMARINE_STAFF_TOPCORE_4_CRYSTAL = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/submarine_staff_topcore_4_crystal"));
    public static final SpriteShiftEntry WINCH_STEEL_COIL_SCROLL = SpriteShifter.get(
        ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/winch_steel_coil"),
        ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/winch_steel_coil_scroll")
    );

    public static void init() {}
}