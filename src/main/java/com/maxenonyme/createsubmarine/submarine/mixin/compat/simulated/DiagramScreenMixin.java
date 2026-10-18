package com.maxenonyme.createsubmarine.submarine.mixin.compat.simulated;

import dev.simulated_team.simulated.content.entities.diagram.screen.DiagramScreen;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.simulated_team.simulated.content.entities.diagram.screen.ForceClusterFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import dev.ryanhcode.sable.api.physics.force.ForceGroups;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.joml.Vector3d;
import org.joml.Vector3dc;

@Mixin(value = DiagramScreen.class, remap = false)
public class DiagramScreenMixin {

    @Inject(method = "renderArrows", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 1))
    private void createsubmarine$alwaysMergeBallast(
            GuiGraphics graphics, int mouseX, int mouseY, int areaOriginX, int areaOriginY,
            Quaternionfc orientation, Vector3dc cameraPos, Matrix4fc projMatrix, int areaWidth, int areaHeight,
            CallbackInfo ci,
            @Local Map<ForceGroup, List<ForceClusterFinder.Cluster>> clusters,
            @Local com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef maxArrowLengthSquaredRef
    ) {
        ForceGroup ballastGroup = null;
        for (ForceGroup group : clusters.keySet()) {
            ResourceLocation id = ForceGroups.REGISTRY.getKey(group);
            if (id != null && id.getNamespace().equals("create_submarine") && id.getPath().equals("ballast")) {
                ballastGroup = group;
                break;
            }
        }
        if (ballastGroup != null) {
            List<ForceClusterFinder.Cluster> list = clusters.get(ballastGroup);
            if (list != null && !list.isEmpty()) {
                Vector3d totalForce = new Vector3d();
                Vector3d averagePos = new Vector3d();
                int totalCount = 0;
                for (ForceClusterFinder.Cluster c : list) {
                    totalForce.add(c.force());
                    averagePos.add(c.pos().mul(c.groupSize().getValue(), new Vector3d()));
                    totalCount += c.groupSize().getValue();
                }
                if (totalCount > 0) {
                    averagePos.div(totalCount);
                }
                ForceClusterFinder.Cluster merged = new ForceClusterFinder.Cluster(
                    averagePos, totalForce, new org.apache.commons.lang3.mutable.MutableInt(totalCount)
                );
                List<ForceClusterFinder.Cluster> newList = new ArrayList<>();
                newList.add(merged);
                clusters.put(ballastGroup, newList);
                double maxVal = 0.0;
                for (List<ForceClusterFinder.Cluster> clList : clusters.values()) {
                    for (ForceClusterFinder.Cluster c : clList) {
                        maxVal = Math.max(maxVal, c.force().lengthSquared());
                    }
                }
                maxArrowLengthSquaredRef.set(maxVal);
            }
        }
    }
}
