package com.maxenonyme.createsubmarine.submarine.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;

public final class CommandSubRenderTypes extends RenderType {

    private static final Int2ObjectOpenHashMap<RenderType> DIAGRAMS = new Int2ObjectOpenHashMap<>();

    public static RenderType diagram(int textureId) {
        return DIAGRAMS.computeIfAbsent(textureId, id -> create(
                "create_submarine_command_diagram",
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_TEXT_SHADER)
                        .setTextureState(new EmptyTextureStateShard(() -> RenderSystem.setShaderTexture(0, id), () -> {
                        }))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(LIGHTMAP)
                        .createCompositeState(false)));
    }

    public static void forget(int textureId) {
        DIAGRAMS.remove(textureId);
    }

    private CommandSubRenderTypes() {
        super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 256, false, false, () -> {
        }, () -> {
        });
    }
}
