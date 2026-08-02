package com.maxenonyme.createsubmarine.submarine.client;

import com.maxenonyme.createsubmarine.worldgen.SeafloorGenerator;
import com.maxenonyme.createsubmarine.worldgen.TectonicPlate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public class PlateDebugOverlay {

    private static final ResourceKey<Level> ABYSS_KEY = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("create_submarine", "abyss"));

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (!mc.level.dimension().equals(ABYSS_KEY)) return;

        BlockPos pos = player.blockPosition();
        TectonicPlate plate = SeafloorGenerator.getPlateAt(pos.getX(), pos.getZ());
        if (plate != null) {
            String label = "§6Tectonic Plate: §f" + plate.name()
                + " §8[" + (plate.oceanic() ? "Oceanic" : "Continental") + "]";
            event.getLeft().add(label);
        }
    }
}
