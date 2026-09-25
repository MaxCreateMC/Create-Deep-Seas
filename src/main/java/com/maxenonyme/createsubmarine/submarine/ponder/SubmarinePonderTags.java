package com.maxenonyme.createsubmarine.submarine.ponder;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class SubmarinePonderTags {

    public static final ResourceLocation DEEP_SEAS = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "deep_seas");

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(DEEP_SEAS)
              .item(CreateSubmarine.BAROMETER.get(), true, false)
              .title("Deep Seas Components")
              .description("Submarine engineering and mechanics")
              .addToIndex()
              .register();

        helper.addToTag(DEEP_SEAS)
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "ballast_vent"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "oxygene_diffuser"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "electrolyzer"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "water_thruster"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "barometer"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "steel_cable"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pulley"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "command_sub"))
              .add(ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "pump_controller"));
    }
}
