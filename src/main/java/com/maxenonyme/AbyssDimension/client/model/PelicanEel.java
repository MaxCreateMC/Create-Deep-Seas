package com.maxenonyme.AbyssDimension.client.model;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.entities.PelicanEelEntity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

public class PelicanEel<T extends PelicanEelEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "pelican_eel"), "main");

    private final ModelPart root;
    public final ModelPart headGroup, neckPart, body02Part, body03Part;
    public final ModelPart tail01Part, tail02Part, tail03Part;
    public final ModelPart tail04Part, tailCurvePart, tailTipPart;

    public PelicanEel(ModelPart root) {
        this.root = root;
        var model = root.getChild("model");
        headGroup = model.getChild("head_group");
        neckPart = model.getChild("neck_part");
        body02Part = neckPart.getChild("body02_part");
        body03Part = body02Part.getChild("body03_part");
        tail01Part = body03Part.getChild("tail01_part");
        tail02Part = tail01Part.getChild("tail02_part");
        tail03Part = tail02Part.getChild("tail03_part");
        tail04Part = tail03Part.getChild("tail04_part");
        tailCurvePart = tail04Part.getChild("tail_curve_part");
        tailTipPart = tailCurvePart.getChild("tail_tip_part");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition model = root.addOrReplaceChild("model", CubeListBuilder.create(),
            PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

        PartDefinition headGrp = model.addOrReplaceChild("head_group", CubeListBuilder.create(),
            PartPose.offsetAndRotation(-14.15F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        headGrp.addOrReplaceChild("upper_head_core", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-20.25F, 13.7F, -5.0F, 19.8F, 4.2F, 10.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("upper_lip_left", CubeListBuilder.create()
            .texOffs(58, 0).addBox(-19.85F, 13.75F, 3.7F, 19.4F, 1.2F, 1.7F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("upper_lip_right", CubeListBuilder.create()
            .texOffs(98, 0).addBox(-19.85F, 13.75F, -5.4F, 19.4F, 1.2F, 1.7F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("forehead_slope_1", CubeListBuilder.create()
            .texOffs(138, 0).addBox(-11.25F, 17.55F, -4.4F, 9.4F, 1.45F, 8.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("left_eye", CubeListBuilder.create()
            .texOffs(172, 0).addBox(-11.55F, 15.5F, 5.02F, 1.5F, 1.2F, 0.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("right_eye", CubeListBuilder.create()
            .texOffs(176, 0).addBox(-11.55F, 15.5F, -5.22F, 1.5F, 1.2F, 0.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("lower_jaw_plate", CubeListBuilder.create()
            .texOffs(180, 0).addBox(-20.05F, 11.75F, -4.7F, 20.1F, 2.0F, 9.4F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("lower_lip_left", CubeListBuilder.create()
            .texOffs(0, 14).addBox(-19.55F, 13.1F, 4.2F, 19.6F, 1.15F, 1.15F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("lower_lip_right", CubeListBuilder.create()
            .texOffs(40, 14).addBox(-19.55F, 13.1F, -5.35F, 19.6F, 1.15F, 1.15F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("jaw_chin_tip", CubeListBuilder.create()
            .texOffs(80, 14).addBox(-21.85F, 11.85F, -2.1F, 2.6F, 1.7F, 4.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("mandible_side_left", CubeListBuilder.create()
            .texOffs(92, 14).addBox(-6.85F, 11.1F, 4.15F, 7.0F, 3.15F, 1.05F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("mandible_side_right", CubeListBuilder.create()
            .texOffs(108, 14).addBox(-6.85F, 11.1F, -5.2F, 7.0F, 3.15F, 1.05F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        headGrp.addOrReplaceChild("jaw_floor_rear", CubeListBuilder.create()
            .texOffs(124, 14).addBox(-7.85F, 11.15F, -4.1F, 8.0F, 1.0F, 8.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition neckPart = model.addOrReplaceChild("neck_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(-14.15F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("neck_body_01", CubeListBuilder.create()
            .texOffs(156, 14).addBox(-0.65F, 11.7F, -3.2F, 8.1F, 4.8F, 6.4F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body02_part = neckPart.addOrReplaceChild("body02_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(7.3F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        body02_part.addOrReplaceChild("body_02_core", CubeListBuilder.create()
            .texOffs(184, 14).addBox(-0.15F, 11.8F, -3.2F, 9.2F, 5.2F, 6.4F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition body03_part = body02_part.addOrReplaceChild("body03_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(8.95F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        body03_part.addOrReplaceChild("body_03_core", CubeListBuilder.create()
            .texOffs(214, 14).addBox(-0.1F, 12.0F, -2.8F, 9.0F, 4.7F, 5.6F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail01_part = body03_part.addOrReplaceChild("tail01_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(8.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail01_part.addOrReplaceChild("tail_01_core", CubeListBuilder.create()
            .texOffs(0, 25).addBox(-0.1F, 12.1F, -2.4F, 8.7F, 4.1F, 4.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail02_part = tail01_part.addOrReplaceChild("tail02_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(8.45F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail02_part.addOrReplaceChild("tail_02_core", CubeListBuilder.create()
            .texOffs(24, 25).addBox(-0.15F, 12.0F, -1.9F, 8.1F, 3.6F, 3.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail03_part = tail02_part.addOrReplaceChild("tail03_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(7.8F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail03_part.addOrReplaceChild("tail_03_core", CubeListBuilder.create()
            .texOffs(46, 25).addBox(-0.15F, 11.7F, -1.5F, 7.6F, 3.2F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail04_part = tail03_part.addOrReplaceChild("tail04_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(7.25F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail04_part.addOrReplaceChild("tail_04_taper", CubeListBuilder.create()
            .texOffs(66, 25).addBox(-0.2F, 11.1F, -1.1F, 6.0F, 2.7F, 2.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail_curve_part = tail04_part.addOrReplaceChild("tail_curve_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(5.3F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail_curve_part.addOrReplaceChild("tail_curve_down", CubeListBuilder.create()
            .texOffs(82, 25).addBox(-0.5F, 9.6F, -0.9F, 3.6F, 2.6F, 1.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition tail_tip_part = tail_curve_part.addOrReplaceChild("tail_tip_part", CubeListBuilder.create(),
            PartPose.offsetAndRotation(2.7F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        tail_tip_part.addOrReplaceChild("tail_tip_lure_core", CubeListBuilder.create()
            .texOffs(90, 25).addBox(-0.4F, 8.8F, -1.1F, 3.0F, 2.0F, 2.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        tail_tip_part.addOrReplaceChild("tail_tip_lure_glow", CubeListBuilder.create()
            .texOffs(100, 25).addBox(1.8F, 9.15F, -1.4F, 1.8F, 1.3F, 2.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        tail01_part.addOrReplaceChild("left_pectoral_fin_outer", CubeListBuilder.create()
            .texOffs(90, 33).addBox(-22.1F, 10.4F, 3.1F, 4.8F, 1.3F, 2.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        tail01_part.addOrReplaceChild("left_pectoral_fin_tip", CubeListBuilder.create()
            .texOffs(102, 33).addBox(-18.3F, 9.8F, 5.4F, 4.8F, 1.1F, 1.7F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        tail01_part.addOrReplaceChild("right_pectoral_fin_outer", CubeListBuilder.create()
            .texOffs(112, 33).addBox(-22.1F, 10.4F, -5.9F, 4.8F, 1.3F, 2.8F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        tail01_part.addOrReplaceChild("right_pectoral_fin_tip", CubeListBuilder.create()
            .texOffs(124, 33).addBox(-18.3F, 9.8F, -7.1F, 4.8F, 1.1F, 1.7F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        neckPart.addOrReplaceChild("dorsal_head_ridge", CubeListBuilder.create()
            .texOffs(106, 25).addBox(-7.85F, 19.0F, -0.35F, 8.0F, 2.0F, 0.7F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_01", CubeListBuilder.create()
            .texOffs(124, 25).addBox(1.35F, 16.8F, -0.18F, 8.3F, 2.5F, 0.36F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_02", CubeListBuilder.create()
            .texOffs(142, 25).addBox(9.65F, 16.6F, -0.18F, 9.1F, 2.2F, 0.36F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_03", CubeListBuilder.create()
            .texOffs(162, 25).addBox(18.75F, 16.1F, -0.16F, 9.0F, 2.1F, 0.32F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_04", CubeListBuilder.create()
            .texOffs(182, 25).addBox(27.65F, 15.7F, -0.14F, 8.7F, 1.7F, 0.28F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_05", CubeListBuilder.create()
            .texOffs(200, 25).addBox(36.15F, 15.1F, -0.12F, 9.0F, 1.7F, 0.24F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("dorsal_fin_06", CubeListBuilder.create()
            .texOffs(220, 25).addBox(44.75F, 14.3F, -0.1F, 9.6F, 1.5F, 0.2F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        neckPart.addOrReplaceChild("anal_fin_01", CubeListBuilder.create()
            .texOffs(0, 33).addBox(12.15F, 10.8F, -0.16F, 10.0F, 1.2F, 0.32F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("anal_fin_02", CubeListBuilder.create()
            .texOffs(22, 33).addBox(22.15F, 10.6F, -0.15F, 10.5F, 1.2F, 0.3F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("anal_fin_03", CubeListBuilder.create()
            .texOffs(44, 33).addBox(32.65F, 10.4F, -0.13F, 11.0F, 1.1F, 0.26F),
            PartPose.offset(0.0F, 0.0F, 0.0F));
        neckPart.addOrReplaceChild("anal_fin_04", CubeListBuilder.create()
            .texOffs(68, 33).addBox(43.35F, 9.9F, -0.11F, 10.3F, 1.1F, 0.22F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        headGroup.yRot = (float) entity.getIncrementalYaw(0);
        headGroup.xRot = (float) entity.getIncrementalPitch(0);
        neckPart.yRot = (float) entity.getIncrementalYaw(1);
        neckPart.xRot = (float) entity.getIncrementalPitch(1);
        body02Part.yRot = (float) entity.getIncrementalYaw(2);
        body02Part.xRot = (float) entity.getIncrementalPitch(2);
        body03Part.yRot = (float) entity.getIncrementalYaw(3);
        body03Part.xRot = (float) entity.getIncrementalPitch(3);
        tail01Part.yRot = (float) entity.getIncrementalYaw(4);
        tail01Part.xRot = (float) entity.getIncrementalPitch(4);
        tail02Part.yRot = (float) entity.getIncrementalYaw(5);
        tail02Part.xRot = (float) entity.getIncrementalPitch(5);
        tail03Part.yRot = (float) entity.getIncrementalYaw(6);
        tail03Part.xRot = (float) entity.getIncrementalPitch(6);
        tail04Part.yRot = (float) entity.getIncrementalYaw(7);
        tail04Part.xRot = (float) entity.getIncrementalPitch(7);
        tailCurvePart.yRot = (float) entity.getIncrementalYaw(8);
        tailCurvePart.xRot = (float) entity.getIncrementalPitch(8);
        tailTipPart.yRot = (float) entity.getIncrementalYaw(9);
        tailTipPart.xRot = (float) entity.getIncrementalPitch(9);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
