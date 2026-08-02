package com.maxenonyme.createsubmarine.submarine.client.model;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.item.harpoon_gun.HarpoonEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

public class HarpoonModel<T extends HarpoonEntity> extends HierarchicalModel<T> {

    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "harpoon"), "main");

    private final ModelPart root;
    private final ModelPart harpoon;

    public HarpoonModel(ModelPart root) {
        this.root = root;
        this.harpoon = root.getChild("harpoon");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition harpoon = root.addOrReplaceChild("harpoon", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        // harpoon shaft (from [7,18.5,7] to [9,20.5,23]) -> size 2x2x16
        harpoon.addOrReplaceChild("shaft", CubeListBuilder.create()
            .texOffs(4, 14).addBox(-1.0F, -1.0F, -8.0F, 2.0F, 2.0F, 16.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        // harpoon fin/tail vertical (from [8,14.5,17] to [8,24.5,27]) -> 0x10x10, rotated -45 on x
        harpoon.addOrReplaceChild("fin_vertical", CubeListBuilder.create()
            .texOffs(0, 6).addBox(0.0F, -5.0F, -5.0F, 0.0F, 10.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, -0.7854F, 0.0F, 0.0F));

        // harpoon fin/tail horizontal (from [3,19.5,17] to [13,19.5,27]) -> 10x0x10, rotated 45 on y
        harpoon.addOrReplaceChild("fin_horizontal", CubeListBuilder.create()
            .texOffs(9, 0).addBox(-5.0F, 0.0F, -5.0F, 10.0F, 0.0F, 10.0F),
            PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.0F, 0.7854F, 0.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
