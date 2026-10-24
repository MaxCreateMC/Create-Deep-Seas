package com.maxenonyme.theabyss.client.renderer;

import com.maxenonyme.theabyss.client.model.CookiecutterShark;
import com.maxenonyme.theabyss.entities.CookiecutterSharkEntity;
import com.maxenonyme.theabyss.CreateAbyss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CookiecutterSharkRenderer extends MobRenderer<CookiecutterSharkEntity, CookiecutterShark<CookiecutterSharkEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "textures/entity/cookiecutter_shark_texture.png");

    public CookiecutterSharkRenderer(EntityRendererProvider.Context context) {
        super(context, new CookiecutterShark<>(context.bakeLayer(CookiecutterShark.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(CookiecutterSharkEntity entity) {
        return TEXTURE;
    }
}
