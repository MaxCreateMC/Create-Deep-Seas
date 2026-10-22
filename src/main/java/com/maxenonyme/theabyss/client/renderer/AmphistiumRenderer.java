package com.maxenonyme.theabyss.client.renderer;

import com.maxenonyme.theabyss.client.model.Amphistium;
import com.maxenonyme.theabyss.entities.AmphistiumEntity;
import com.maxenonyme.theabyss.CreateAbyss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;

public class AmphistiumRenderer extends MobRenderer<AmphistiumEntity, Amphistium<AmphistiumEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "textures/entity/amphistium.png");

    public AmphistiumRenderer(EntityRendererProvider.Context context) {
        super(context, new Amphistium<>(context.bakeLayer(Amphistium.LAYER_LOCATION)), 0.3F);
        this.addLayer(new AmphistiumGlowingLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AmphistiumEntity entity) {
        return TEXTURE;
    }

    private static class AmphistiumGlowingLayer extends EyesLayer<AmphistiumEntity, Amphistium<AmphistiumEntity>> {
        private static final RenderType GLOWING_RENDER_TYPE = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "textures/entity/amphistium_glowing.png"));

        public AmphistiumGlowingLayer(RenderLayerParent<AmphistiumEntity, Amphistium<AmphistiumEntity>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOWING_RENDER_TYPE;
        }
    }
}
