package com.maxenonyme.AbyssDimension.client.model;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.entities.AbyssalCuttlefishEntity;

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

public class AbyssalCuttlefish<T extends AbyssalCuttlefishEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "abyssal_cuttlefish"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart eyes;
    private final ModelPart top_beak;
    private final ModelPart bottom_beak;
    private final ModelPart tentacle_top;
    private final ModelPart tentacle_bottom;
    private final ModelPart tentacle_right;
    private final ModelPart tentacle_left;

    public AbyssalCuttlefish(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.eyes = this.head.getChild("eyes");
        this.top_beak = this.head.getChild("top_beak");
        this.bottom_beak = this.head.getChild("bottom_beak");
        this.tentacle_top = this.head.getChild("tentacle_top");
        this.tentacle_bottom = this.head.getChild("tentacle_bottom");
        this.tentacle_right = this.head.getChild("tentacle_right");
        this.tentacle_left = this.head.getChild("tentacle_left");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 16.0F, 13.33333F));

        body.addOrReplaceChild("body_mantel", CubeListBuilder.create()
            .texOffs(0, 0).addBox(-40.0F, 0.0F, -21.33333F, 80.0F, 0.0F, 112.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        body.addOrReplaceChild("body_main", CubeListBuilder.create()
            .texOffs(0, 112).addBox(-24.0F, -16.0F, -37.33333F, 48.0F, 32.0F, 80.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -37.33333F));

        head.addOrReplaceChild("head_cube", CubeListBuilder.create()
            .texOffs(0, 224).addBox(-18.0F, -10.0F, -16.0F, 36.0F, 20.0F, 16.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F));

        head.addOrReplaceChild("eyes", CubeListBuilder.create()
            .texOffs(208, 224).mirror().addBox(18.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F)
            .texOffs(208, 224).mirror().addBox(-34.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F));

        head.addOrReplaceChild("top_beak", CubeListBuilder.create()
            .texOffs(104, 240).addBox(-6.0F, 0.0F, -16.0F, 12.0F, 0.0F, 16.0F)
            .texOffs(256, 112).addBox(6.0F, -8.0F, -16.0F, 0.0F, 8.0F, 16.0F)
            .texOffs(256, 160).addBox(-6.0F, -8.0F, -16.0F, 12.0F, 8.0F, 0.0F)
            .texOffs(256, 112).mirror().addBox(-6.0F, -8.0F, -16.0F, 0.0F, 8.0F, 16.0F),
            PartPose.offset(0.0F, 6.0F, -16.0F));

        head.addOrReplaceChild("bottom_beak", CubeListBuilder.create()
            .texOffs(256, 136).addBox(6.0F, 0.0F, -16.0F, 0.0F, 8.0F, 16.0F)
            .texOffs(256, 168).addBox(-6.0F, 0.0F, -16.0F, 12.0F, 8.0F, 0.0F)
            .texOffs(104, 256).addBox(-6.0F, 0.0F, -16.0F, 12.0F, 0.0F, 16.0F)
            .texOffs(256, 136).mirror().addBox(-6.0F, 0.0F, -16.0F, 0.0F, 8.0F, 16.0F),
            PartPose.offset(0.0F, -7.0F, -16.0F));

        head.addOrReplaceChild("tentacle_top", CubeListBuilder.create()
            .texOffs(104, 224).addBox(-18.0F, 0.0F, -16.0F, 36.0F, 0.0F, 16.0F),
            PartPose.offset(0.0F, 10.0F, -16.0F));

        head.addOrReplaceChild("tentacle_bottom", CubeListBuilder.create()
            .texOffs(104, 224).addBox(-18.0F, 0.0F, -16.0F, 36.0F, 0.0F, 16.0F),
            PartPose.offset(0.0F, -10.0F, -16.0F));

        head.addOrReplaceChild("tentacle_right", CubeListBuilder.create()
            .texOffs(160, 240).addBox(0.0F, -10.0F, -16.0F, 0.0F, 20.0F, 16.0F),
            PartPose.offset(18.0F, 0.0F, -16.0F));

        head.addOrReplaceChild("tentacle_left", CubeListBuilder.create()
            .texOffs(160, 240).addBox(0.0F, -10.0F, -16.0F, 0.0F, 20.0F, 16.0F),
            PartPose.offset(-18.0F, 0.0F, -16.0F));

        return LayerDefinition.create(meshdefinition, 512, 512);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.swimAnimationState, AbyssalCuttlefishAnimation.swim, ageInTicks);
        this.animate(entity.idleAnimationState, AbyssalCuttlefishAnimation.idle, ageInTicks);
        this.animate(entity.biteAnimationState, AbyssalCuttlefishAnimation.bite, ageInTicks);
        this.animate(entity.jetUseAnimationState, AbyssalCuttlefishAnimation.jetUse, ageInTicks);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
