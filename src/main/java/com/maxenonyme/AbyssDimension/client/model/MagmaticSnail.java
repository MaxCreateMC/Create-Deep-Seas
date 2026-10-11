package com.maxenonyme.AbyssDimension.client.model;

import com.maxenonyme.AbyssDimension.CreateAbyss;
import com.maxenonyme.AbyssDimension.entities.MagmaticSnailEntity;
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

public class MagmaticSnail<T extends MagmaticSnailEntity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "magmatic_snail"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart shell;

    public MagmaticSnail(ModelPart root) {
        this.root = root;
        this.body = root.getChild("snail").getChild("body");
        this.shell = this.body.getChild("shell");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition def = mesh.getRoot();

        float r = (float)Math.PI / 180.0F;

        PartDefinition snail = def.addOrReplaceChild("snail", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
        PartDefinition body = snail.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, -7F));

        body.addOrReplaceChild("cube_0", CubeListBuilder.create().texOffs(0, 50).addBox(-5F, -12F, -6F, 10F, 12F, 26F, new CubeDeformation(0.0F)), PartPose.ZERO);

        PartDefinition bodyLip = body.addOrReplaceChild("bodyLip", CubeListBuilder.create(), PartPose.offset(0F, 0F, -3F));
        bodyLip.addOrReplaceChild("decor_1", CubeListBuilder.create().texOffs(72, 50).addBox(-8F, -1F, -8F, 16F, 2F, 16F, new CubeDeformation(0.0F)), PartPose.offset(0F, -0.975F, 2F));

        PartDefinition left_eyestalk = body.addOrReplaceChild("left_eyestalk", CubeListBuilder.create(), PartPose.ZERO);
        left_eyestalk.addOrReplaceChild("decor_9", CubeListBuilder.create().mirror().texOffs(72, 68).addBox(0F, -5F, -14F, 0F, 10F, 14F, new CubeDeformation(0.0F)), PartPose.offset(-2F, -6F, -6F));

        PartDefinition right_eyestalk = body.addOrReplaceChild("right_eyestalk", CubeListBuilder.create(), PartPose.offset(2F, -4F, -6F));
        right_eyestalk.addOrReplaceChild("decor_2", CubeListBuilder.create().texOffs(72, 68).addBox(0F, -5F, -14F, 0F, 10F, 14F, new CubeDeformation(0.0F)), PartPose.offset(0F, -2F, 0F));

        PartDefinition coating = body.addOrReplaceChild("coating", CubeListBuilder.create(), PartPose.offset(-5F, -12F, 18F));
        coating.addOrReplaceChild("decor_3", CubeListBuilder.create().texOffs(0, 88).addBox(0F, 0F, -11F, 0F, 12F, 13F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0F, 0F, 0F, 0.0F, 0.0F, -7.5F * r));
        coating.addOrReplaceChild("decor_6", CubeListBuilder.create().texOffs(0, 88).addBox(0F, 0F, -11F, 0F, 12F, 13F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(0F, 0F, -8F, 0.0F, 0.0F, -2.5F * r));
        coating.addOrReplaceChild("decor_4", CubeListBuilder.create().texOffs(88, 0).addBox(-5F, 0F, 0F, 10F, 12F, 0F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(5F, 0F, 2F, -5F * r, 0.0F, 0.0F));
        coating.addOrReplaceChild("decor_7", CubeListBuilder.create().mirror().texOffs(0, 88).addBox(0F, 0F, -11F, 0F, 12F, 13F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(10F, 0F, -8F, 0.0F, 0.0F, 2.5F * r));
        coating.addOrReplaceChild("decor_8", CubeListBuilder.create().mirror().texOffs(0, 88).addBox(0F, 0F, -11F, 0F, 12F, 13F, new CubeDeformation(0.0F)),
            PartPose.offsetAndRotation(10F, 0F, 0F, 0.0F, 0.0F, 7.5F * r));

        PartDefinition shell = body.addOrReplaceChild("shell", CubeListBuilder.create(), PartPose.offset(0F, -12F, 0F));
        shell.addOrReplaceChild("cube_5", CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -20F, -8F, 14F, 20F, 30F, new CubeDeformation(0.0F)), PartPose.ZERO);

        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        if (entity.hurtTime > 0) {
            if (entity.hurtTime > 5) {
                this.animate(entity.hideAnimationState, MagmaticSnailAnimation.hide, ageInTicks);
            } else {
                this.animate(entity.hiddenAnimationState, MagmaticSnailAnimation.hidden, ageInTicks);
            }
        } else {
            this.animate(entity.crawlAnimationState, MagmaticSnailAnimation.crawl, ageInTicks);
        }
    }

    @Override
    public ModelPart root() {
        return this.root;
    }
}
