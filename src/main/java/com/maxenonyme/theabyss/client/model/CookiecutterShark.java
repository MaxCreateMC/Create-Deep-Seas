package com.maxenonyme.theabyss.client.model;

import com.maxenonyme.theabyss.entities.CookiecutterSharkEntity;
import com.maxenonyme.theabyss.CreateAbyss;
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

public class CookiecutterShark<T extends CookiecutterSharkEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "cookiecutter_shark"), "main");
    private final ModelPart root;

    public CookiecutterShark(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition bone2 = partdefinition.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        PartDefinition body_front = bone2.addOrReplaceChild("body_front", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -1.5F, -9.0F, 3.0F, 5.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.5F, 1.0F));

        PartDefinition head = body_front.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 25).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(14, 28).addBox(-2.0F, -2.0F, -1.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -9.0F));

        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(24, 10).addBox(-1.5F, -0.01F, -2.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(31, 38).addBox(-1.5F, -1.01F, -2.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, -1.0F));

        PartDefinition fin_left = body_front.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(24, 0).addBox(0.0F, 0.0F, 0.0F, 3.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(1.5F, 1.5F, -6.0F));

        PartDefinition fin_right = body_front.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(24, 5).addBox(-3.0F, 0.0F, 0.0F, 3.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.5F, 1.5F, -6.0F));

        PartDefinition body_back = bone2.addOrReplaceChild("body_back", CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.5F, 1.0F));

        PartDefinition fin_back_2 = body_back.addOrReplaceChild("fin_back_2", CubeListBuilder.create().texOffs(30, 14).addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 4.0F));

        PartDefinition fin_back_3 = body_back.addOrReplaceChild("fin_back_3", CubeListBuilder.create().texOffs(29, 18).addBox(0.0F, 0.0F, -2.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.5F, 4.0F));

        PartDefinition tail = body_back.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(18, 14).addBox(0.0F, -3.5F, 0.0F, 0.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 7.0F));

        PartDefinition fin_back_1 = body_back.addOrReplaceChild("fin_back_1", CubeListBuilder.create().texOffs(30, 24).addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, 1.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.swimAnimationState, CookiecutterSharkAnimation.swim, ageInTicks);
        this.animate(entity.idleAnimationState, CookiecutterSharkAnimation.idle, ageInTicks);
        this.animate(entity.latchAnimationState, CookiecutterSharkAnimation.latching, ageInTicks);
        this.animate(entity.latchIdleAnimationState, CookiecutterSharkAnimation.latchingIdle, ageInTicks);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
