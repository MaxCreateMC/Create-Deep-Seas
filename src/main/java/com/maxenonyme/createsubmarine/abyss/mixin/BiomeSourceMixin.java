package com.maxenonyme.createsubmarine.abyss.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeSource.class)
public class BiomeSourceMixin {
    private static boolean createsubmarine$abyssWarned = false;

    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void createsubmarine$onPossibleBiomes(CallbackInfoReturnable<java.util.Set<Holder<Biome>>> cir) {
        //commented out for testing because of a bug re enable later
        //if (!com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig.ENABLE_ABYSS_GENERATION.get())
        //    return;
        java.util.Set<Holder<Biome>> original = cir.getReturnValue();
        Holder<Biome> abyssHolder = createsubmarine$getAbyssBiomeHolder();
        if (abyssHolder != null) {
            if (!original.contains(abyssHolder)) {
                java.util.Set<Holder<Biome>> updated = new java.util.HashSet<>(original);
                updated.add(abyssHolder);
                cir.setReturnValue(java.util.Collections.unmodifiableSet(updated));
            }
        } else if (!createsubmarine$abyssWarned) {
            createsubmarine$abyssWarned = true;
            com.maxenonyme.createsubmarine.CreateSubmarine.LOGGER.warn("[Abyss] Could not find abyss biome holder in registry! Check that create_abyss:abyss biome JSON is loaded.");
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
