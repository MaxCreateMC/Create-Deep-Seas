package com.maxenonyme.AbyssDimension.client.model;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.entities.IsopodEntity;
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
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Isopod<T extends IsopodEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "giant_isopod"), "main");

    private final ModelPart rootGroup;
    private final List<ModelPart> legsSorted;

    public Isopod(ModelPart root) {
        this.rootGroup = root.getChild("root_group");
        ModelPart legsGroup = this.rootGroup.getChild("legs");
        this.legsSorted = new ArrayList<>();
        String[] legNames = {"elem_2","elem_3","elem_4","elem_5","elem_6","elem_11","elem_12","elem_13","elem_14","elem_15","elem_16","elem_17","elem_18","elem_19","elem_20","elem_21","elem_22","elem_23","elem_24","elem_25"};
        for (String name : legNames) {
            this.legsSorted.add(legsGroup.getChild(name));
        }
        this.legsSorted.sort(Comparator.comparingDouble(p -> p.z));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition def = mesh.getRoot();

        float r = (float)Math.PI / 180.0F;

        PartDefinition rootGroup = def.addOrReplaceChild("root_group", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
        PartDefinition shell = rootGroup.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0F, 0F, -16F));
        shell.addOrReplaceChild("elem_0", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, -4F, -12F, 12F, 4F, 20F, new CubeDeformation(0.0F)), PartPose.offset(2F, -4F, 16F));
        shell.addOrReplaceChild("elem_10", CubeListBuilder.create().texOffs(72, 36).addBox(-4F, -8F, -12F, 8F, 4F, 20F, new CubeDeformation(0.0F)), PartPose.offset(0F, -0.4F, 16F));
        shell.addOrReplaceChild("elem_29", CubeListBuilder.create().texOffs(64, 4).addBox(-4F, -8F, -12F, 4F, 0F, 20F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-1.6F, 3.2F, 16F, 0.0F, 0.0F, 17.5F * r));
        shell.addOrReplaceChild("elem_30", CubeListBuilder.create().mirror().texOffs(64, 4).addBox(0F, -8F, -12F, 4F, 0F, 20F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(1.6F, 3.2F, 16F, 0.0F, 0.0F, -17.5F * r));

        PartDefinition legs = rootGroup.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.ZERO);
        legs.addOrReplaceChild("elem_2", CubeListBuilder.create().texOffs(40, 40).addBox(0F, -4F, 2.4F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-2F, 0F, -2.4F));
        legs.addOrReplaceChild("elem_3", CubeListBuilder.create().texOffs(0, 44).addBox(0F, -4F, -3.6F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-2F, 0F, -2.4F));
        legs.addOrReplaceChild("elem_4", CubeListBuilder.create().texOffs(32, 44).addBox(-4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(8F, 0F, 2F));
        legs.addOrReplaceChild("elem_5", CubeListBuilder.create().texOffs(8, 44).addBox(-4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(8F, 0F, -4F));
        legs.addOrReplaceChild("elem_12", CubeListBuilder.create().texOffs(8, 44).addBox(0F, -4F, -2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(4F, 0F, 0.8F, 0.0F, -180F * r, 0.0F));
        legs.addOrReplaceChild("elem_6", CubeListBuilder.create().texOffs(16, 44).addBox(-4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(8F, 0F, -10.4F));
        legs.addOrReplaceChild("elem_11", CubeListBuilder.create().texOffs(16, 44).addBox(0F, -4F, -2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(4F, 0F, -5.2F, 0.0F, -180F * r, 0.0F));
        legs.addOrReplaceChild("elem_13", CubeListBuilder.create().texOffs(8, 44).addBox(0F, -4F, -3.6F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(2F, 0F, -2.4F));
        legs.addOrReplaceChild("elem_14", CubeListBuilder.create().texOffs(16, 44).addBox(0F, -4F, 2.4F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(2F, 0F, -2.4F));
        legs.addOrReplaceChild("elem_15", CubeListBuilder.create().texOffs(16, 44).addBox(0F, -4F, -9.6F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2F, 0F, -2.4F, 180F * r, 0.0F, 180F * r));
        legs.addOrReplaceChild("elem_16", CubeListBuilder.create().texOffs(8, 44).addBox(0F, -4F, -3.2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2F, 0F, -2.4F, 180F * r, 0.0F, 180F * r));
        legs.addOrReplaceChild("elem_17", CubeListBuilder.create().texOffs(32, 44).addBox(0F, -4F, 2.8F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2F, 0F, -2.4F, 180F * r, 0.0F, 180F * r));
        legs.addOrReplaceChild("elem_18", CubeListBuilder.create().mirror().texOffs(8, 44).addBox(0F, -4F, -2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-4F, 0F, 0.8F, 0.0F, 180F * r, 0.0F));
        legs.addOrReplaceChild("elem_19", CubeListBuilder.create().mirror().texOffs(16, 44).addBox(0F, -4F, -2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-4F, 0F, -5.2F, 0.0F, 180F * r, 0.0F));
        legs.addOrReplaceChild("elem_20", CubeListBuilder.create().mirror().texOffs(16, 44).addBox(4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-8F, 0F, -10.4F));
        legs.addOrReplaceChild("elem_21", CubeListBuilder.create().mirror().texOffs(8, 44).addBox(4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-8F, 0F, -4F));
        legs.addOrReplaceChild("elem_22", CubeListBuilder.create().mirror().texOffs(32, 44).addBox(4F, -4F, 0F, 0F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-8F, 0F, 2F));
        legs.addOrReplaceChild("elem_23", CubeListBuilder.create().mirror().texOffs(16, 44).addBox(0F, -4F, -9.6F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-2F, 0F, -2.4F, 180F * r, 0.0F, -180F * r));
        legs.addOrReplaceChild("elem_25", CubeListBuilder.create().mirror().texOffs(32, 44).addBox(0F, -4F, 2.8F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-2F, 0F, -2.4F, 180F * r, 0.0F, -180F * r));
        legs.addOrReplaceChild("elem_24", CubeListBuilder.create().mirror().texOffs(8, 44).addBox(0F, -4F, -3.2F, 0F, 4F, 4F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(-2F, 0F, -2.4F, 180F * r, 0.0F, -180F * r));

        PartDefinition tail = rootGroup.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0F, 0F, -16F));
        tail.addOrReplaceChild("elem_7", CubeListBuilder.create().texOffs(40, 24).addBox(-8F, -4F, 0F, 12F, 0F, 8F, new CubeDeformation(0.0F)), PartPose.offset(2F, -2.4F, 24F));
        tail.addOrReplaceChild("elem_9", CubeListBuilder.create().texOffs(0, 36).addBox(-8F, -4F, 0F, 12F, 0F, 8F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(9.2F, -2F, 23.2F, 0.0F, -52.5F * r, 0.0F));
        tail.addOrReplaceChild("elem_8", CubeListBuilder.create().texOffs(0, 24).addBox(-8F, -4F, 0F, 8F, 0F, 12F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(2F, -1.99F, 21.6F, 0.0F, -35F * r, 0.0F));

        PartDefinition head = rootGroup.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, -1.2F, -20F));
        head.addOrReplaceChild("elem_28", CubeListBuilder.create().texOffs(4, 76).addBox(-4F, -4F, -4F, 8F, 0F, 8F, new CubeDeformation(0.0F)), PartPose.offset(0F, -0.4F, 1.2F));
        head.addOrReplaceChild("elem_1", CubeListBuilder.create().texOffs(40, 32).addBox(-4F, -8F, 0F, 8F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(0F, 1.2F, 4F));
        head.addOrReplaceChild("elem_26", CubeListBuilder.create().texOffs(56, 4).addBox(0F, -4F, 0F, 4F, 4F, 4F, new CubeDeformation(0.0F)), PartPose.offset(-2F, -2.8F, 3.6F));
        head.addOrReplaceChild("elem_27", CubeListBuilder.create().texOffs(4, 56).addBox(-4F, -4F, -4F, 8F, 0F, 8F, new CubeDeformation(0.0F)), PartPose.ZERO);

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        float vx = (float)entity.getDeltaMovement().x;
        float vy = (float)entity.getDeltaMovement().y;
        float vz = (float)entity.getDeltaMovement().z;
        float speed = Mth.sqrt(vx * vx + vy * vy + vz * vz);

        if (entity.onGround()) {
            this.rootGroup.xRot = 0.0F;
            this.rootGroup.zRot = 0.0F;
            if (limbSwingAmount > 0.01F) {
                float amp = Mth.clamp(limbSwingAmount * 0.6F, 0.05F, 0.6F);
                for (int i = 0; i < legsSorted.size(); i++) {
                    float dir = legsSorted.get(i).x > 0 ? -1.0F : 1.0F;
                    float phase = (float)i / legsSorted.size() * (float)Math.PI * 2;
                    legsSorted.get(i).xRot += Mth.sin(limbSwing * 1.5F + phase) * amp * dir;
                }
            }
        } else {
            this.rootGroup.xRot = headPitch * ((float)Math.PI / 180.0F);
            this.rootGroup.zRot = Mth.clamp(vx * 0.05F, -0.5F, 0.5F);

            if (speed > 0.01F) {
                this.rootGroup.xRot += -0.05F - 0.05F * Mth.cos(ageInTicks * 0.3F);
            }

            if (entity.hurtTime > 0) {
                this.animate(entity.burstAnimationState, IsopodAnimation.swimTail, ageInTicks);
            }

            boolean ascending = vy >= 0;
            float legSpeed = ascending ? 2.5F : 1.5F;
            float amp = Mth.clamp(speed * 1.5F, 0.0F, ascending ? 0.7F : 0.6F);
            if (amp > 0.01F) {
                for (int i = 0; i < legsSorted.size(); i++) {
                    float dir = legsSorted.get(i).x > 0 ? -1.0F : 1.0F;
                    float phase = (float)i / legsSorted.size() * (float)Math.PI * 2;
                    legsSorted.get(i).xRot += Mth.sin(ageInTicks * legSpeed + phase) * amp * dir;
                }
            }
        }
    }

    @Override
    public ModelPart root() {
        return this.rootGroup;
    }
}
