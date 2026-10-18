package com.maxenonyme.highseas.client;

import com.maxenonyme.highseas.CreateHighSeas;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class SailRenderTypes extends RenderType {

    public static final ResourceLocation SAIL_SHADER = ResourceLocation.fromNamespaceAndPath(CreateHighSeas.MOD_ID, "sail/sail");

    private static final RenderType SAIL = RenderType.create(
            CreateHighSeas.MOD_ID + ":sail",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            TRANSIENT_BUFFER_SIZE,
            false,
            true,
            VeilRenderBridge.create(
                            RenderType.CompositeState.builder()
                                    .setShaderState(VeilRenderBridge.shaderState(SAIL_SHADER))
                                    .setTransparencyState(NO_TRANSPARENCY)
                                    .setCullState(NO_CULL)
                                    .setTextureState(RenderStateShard.BLOCK_SHEET)
                                    .setLightmapState(LightmapStateShard.LIGHTMAP)
                    )
                    .addLayer(VeilRenderBridge.patchState(4))
                    .create(false)
    );

    public SailRenderTypes(final String name,
                           final VertexFormat format,
                           final VertexFormat.Mode mode,
                           final int bufferSize,
                           final boolean affectsCrumbling,
                           final boolean sortOnUpload,
                           final Runnable setupState,
                           final Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType sail() {
        return SAIL;
    }
}
