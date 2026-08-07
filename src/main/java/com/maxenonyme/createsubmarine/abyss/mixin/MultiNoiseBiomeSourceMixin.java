package com.maxenonyme.createsubmarine.abyss.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public class MultiNoiseBiomeSourceMixin {
    @Inject(method = "getNoiseBiome", at = @At("RETURN"), cancellable = true)
    private void createsubmarine$onGetNoiseBiome(int x, int y, int z, Climate.Sampler sampler,
            CallbackInfoReturnable<Holder<Biome>> cir) {
        //commented out for testing because of a bug re enable later
        //if (!com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.ENABLE_ABYSS_GENERATION.get())
        //    return;
        int blockX = x * 4;
        int blockZ = z * 4;

        Climate.TargetPoint targetPoint = sampler.sample(x, y, z);
        float c = Climate.unquantizeCoord(targetPoint.continentalness());

        double S = 0.0;
        if (c >= -0.8 && c <= -0.35) {
            S = 1.0;
        } else if (c > -1.05 && c < -0.8) {
            S = (c + 1.05) / 0.25;
        } else if (c > -0.35 && c < -0.15) {
            S = (-0.15 - c) / 0.20;
        }

        if (S <= 0.0) {
            return;
        }

        long cellX = Math.floorDiv(blockX, 4000);
        long cellZ = Math.floorDiv(blockZ, 4000);

        double minDistance = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cx = cellX + dx;
                long cz = cellZ + dz;
                long hash = (cx * 31273709L) ^ (cz * 43903207L);
                java.util.Random rand = new java.util.Random(hash);
                double centerX = cx * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                double centerZ = cz * 4000.0 + 500.0 + rand.nextDouble() * 3000.0;
                double dist = Math
                        .sqrt((blockX - centerX) * (blockX - centerX) + (blockZ - centerZ) * (blockZ - centerZ));
                if (dist < minDistance) {
                    minDistance = dist;
                }
            }
        }

        double t = 0.0;
        if (minDistance < 480) {
            t = 1.0;
        } else if (minDistance < 520) {
            t = (520 - minDistance) / 40.0;
            t = t * t * (3 - 2 * t);
        }

        if (t * S > 0.5) {
            Holder<Biome> holder = createsubmarine$getAbyssBiomeHolder();
            if (holder != null) {
                cir.setReturnValue(holder);
            }
        }
    }

    private static Holder<Biome> createsubmarine$getAbyssBiomeHolder() {
        net.minecraft.core.RegistryAccess registryAccess = null;
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks
                .getCurrentServer();
        if (server != null) {
            registryAccess = server.registryAccess();
        } else {
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    registryAccess = mc.level.registryAccess();
                }
            } catch (Throwable ignored) {
            }
        }
        if (registryAccess != null) {
            var registry = registryAccess.registry(Registries.BIOME);
            if (registry.isPresent()) {
                var key = ResourceKey.create(Registries.BIOME,
                        ResourceLocation.fromNamespaceAndPath("create_abyss", "abyss"));
                var holderOpt = registry.get().getHolder(key);
                if (holderOpt.isPresent()) {
                    return holderOpt.get();
                }
            }
        }
        return null;
    }
}
