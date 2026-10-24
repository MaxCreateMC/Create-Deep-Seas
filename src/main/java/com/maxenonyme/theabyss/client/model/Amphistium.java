package com.maxenonyme.theabyss.client.model;

import com.maxenonyme.theabyss.entities.AmphistiumEntity;
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

public class Amphistium<T extends AmphistiumEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "amphistium"), "main");
    private final ModelPart root;
    private final ModelPart anglerfish;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart bone;
    private final ModelPart lower_fins;
    private final ModelPart r;
    private final ModelPart l;
    private final ModelPart bone2;
    private final ModelPart tail_fin;

    public Amphistium(ModelPart root) {
        this.root = root;
        this.anglerfish = root.getChild("anglerfish");
        this.body = this.anglerfish.getChild("body");
        this.head = this.body.getChild("head");
        this.bone = this.head.getChild("bone");
        this.lower_fins = this.head.getChild("lower_fins");
        this.r = this.lower_fins.getChild("r");
        this.l = this.lower_fins.getChild("l");
        this.bone2 = this.head.getChild("bone2");
        this.tail_fin = this.body.getChild("tail_fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition anglerfish = partdefinition.addOrReplaceChild("anglerfish", CubeListBuilder.create(), PartPose.offset(0.0F, 26.0F, 0.0F));

        PartDefinition body = anglerfish.addOrReplaceChild("body", CubeListBuilder.create().texOffs(12, 26).addBox(-1.5F, -1.0F, 1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(0, 29).addBox(0.0F, -5.0F, 1.0F, 0.0F, 4.0F, 2.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -5.0F, -4.0F));

        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(16, 18).addBox(-1.5F, -3.0F, -3.0F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(0, 24).addBox(-1.5F, -3.0F, -6.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
        .texOffs(22, 26).addBox(-1.5F, -1.0F, -6.0F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 1.0F));

        PartDefinition bone = head.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(14, 11).addBox(-1.5F, -1.0F, -4.5F, 3.0F, 2.0F, 5.0F, new CubeDeformation(0.01F))
        .texOffs(0, 18).addBox(-1.5F, -2.0F, -4.5F, 3.0F, 1.0F, 5.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 1.0F, -2.0F));

        PartDefinition lower_fins = head.addOrReplaceChild("lower_fins", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -1.0F));

        PartDefinition r = lower_fins.addOrReplaceChild("r", CubeListBuilder.create().texOffs(28, 18).addBox(-0.01F, 0.0F, -1.0F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(-1.5F, 0.0F, 0.0F));

        PartDefinition l = lower_fins.addOrReplaceChild("l", CubeListBuilder.create().texOffs(28, 0).addBox(0.01F, 0.0F, -1.0F, 0.0F, 4.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offset(1.5F, 0.0F, 0.0F));

        PartDefinition bone2 = head.addOrReplaceChild("bone2", CubeListBuilder.create().texOffs(14, 0).addBox(0.0F, -4.0F, -5.0F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, -2.0F, -4.0F));

        PartDefinition tail_fin = body.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, -5.0F, 0.0F, 0.0F, 10.0F, 7.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 1.0F, 3.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.animate(entity.swimAnimationState, AmphistiumAnimation.swim, ageInTicks);
        this.animate(entity.idleAnimationState, AmphistiumAnimation.idle, ageInTicks);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
