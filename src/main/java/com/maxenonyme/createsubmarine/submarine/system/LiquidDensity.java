package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LiquidDensity {
    private LiquidDensity() {
    }

    private record TagDensity(TagKey<Fluid> tag, double density) {
    }

    private static final Map<Fluid, Double> CACHE = new ConcurrentHashMap<>();
    private static volatile List<? extends String> source;
    private static volatile Map<ResourceLocation, Double> byId = Map.of();
    private static volatile List<TagDensity> byTag = List.of();

    public static double of(Fluid fluid) {
        if (fluid == Fluids.EMPTY)
            return 1.0;
        refresh();
        return CACHE.computeIfAbsent(fluid, LiquidDensity::resolve);
    }

    private static void refresh() {
        if (!SubmarineConfig.SERVER_SPEC.isLoaded())
            return;
        List<? extends String> entries = SubmarineConfig.LIQUID_DENSITIES.get();
        if (entries == source)
            return;
        Map<ResourceLocation, Double> ids = new HashMap<>();
        List<TagDensity> tags = new ArrayList<>();
        for (String entry : entries) {
            int eq = entry.lastIndexOf('=');
            if (eq <= 0)
                continue;
            String key = entry.substring(0, eq).trim();
            double density;
            try {
                density = Double.parseDouble(entry.substring(eq + 1).trim());
            } catch (NumberFormatException e) {
                CreateSubmarine.LOGGER.warn("Ignoring liquid density '{}': not a number", entry);
                continue;
            }
            if (density <= 0)
                continue;
            boolean tag = key.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? key.substring(1) : key);
            if (id == null) {
                CreateSubmarine.LOGGER.warn("Ignoring liquid density '{}': bad id", entry);
                continue;
            }
            if (tag)
                tags.add(new TagDensity(TagKey.create(Registries.FLUID, id), density));
            else
                ids.put(id, density);
        }
        byId = ids;
        byTag = tags;
        CACHE.clear();
        source = entries;
    }

    private static double resolve(Fluid fluid) {
        Double direct = byId.get(BuiltInRegistries.FLUID.getKey(fluid));
        if (direct != null)
            return direct;
        for (TagDensity t : byTag) {
            if (fluid.is(t.tag()))
                return t.density();
        }
        return 1.0;
    }
}
