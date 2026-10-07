package com.maxenonyme.AbyssDimension.experimental.worldgen.client;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.experimental.worldgen.ExperimentalWorldgen;
import com.maxenonyme.AbyssDimension.experimental.worldgen.SeafloorGenerator;
import com.maxenonyme.AbyssDimension.experimental.worldgen.TectonicPlate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public final class PlateDebugOverlay {
    private PlateDebugOverlay() {
    }

    private static final ResourceKey<Level> ABYSS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "abyss"));

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!ExperimentalWorldgen.enabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !mc.level.dimension().equals(ABYSS))
            return;
        BlockPos pos = player.blockPosition();
        TectonicPlate plate = SeafloorGenerator.getPlateAt(pos.getX(), pos.getZ());
        if (plate != null)
            event.getLeft().add("§6Tectonic Plate: §f" + plate.name() + " §8[" + (plate.oceanic() ? "Oceanic" : "Continental") + "]");
    }
}
