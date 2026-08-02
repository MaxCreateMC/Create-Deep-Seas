package com.maxenonyme.createsubmarine.submarine.client.renderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.createmod.catnip.render.SpriteShiftEntry;
import net.createmod.catnip.render.SpriteShifter;
import net.minecraft.resources.ResourceLocation;
public class AllPartialModels {
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
    public static final PartialModel LEAK_DETECTOR_CORE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/leak_detector/core"));
    public static final PartialModel LEAK_DETECTOR_CASING = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/leak_detector/casing"));
    public static final PartialModel LEAK_DETECTOR_EYE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/leak_detector/eye"));
    public static final PartialModel WARDING_STAFF = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/warding_staff/staff"));
    public static final PartialModel WARDING_STAFF_RING = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/warding_staff/ring"));
    public static final PartialModel WARDING_STAFF_ORB = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/warding_staff/orb"));
    public static final PartialModel PURGE_STAFF = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/core"));
    public static final PartialModel PURGE_STAFF_CASING = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/casing"));
    public static final PartialModel PURGE_STAFF_EYE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/purge_staff/eye"));
    public static final PartialModel HARPOON_GUN = PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "item/harpoon_gun/cannon"));
    public static final SpriteShiftEntry WINCH_STEEL_COIL_SCROLL = SpriteShifter.get(
        ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/winch_steel_coil"),
        ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "block/steel_cable/winch_steel_coil_scroll")
    );

    public static void init() {}
}