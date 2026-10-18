package com.maxenonyme.highseas.mixin;

import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.BitSet;
import java.util.List;

@Mixin(ChunkRenderTypeSet.class)
public interface ChunkRenderTypeSetAccessor {

    @Mutable
    @Accessor("CHUNK_RENDER_TYPES_LIST")
    static void setChunkRenderTypesList(final List<RenderType> data) {
        throw new AssertionError();
    }

    @Mutable
    @Accessor("CHUNK_RENDER_TYPES")
    static void setChunkRenderTypes(final RenderType[] data) {
        throw new AssertionError();
    }

    @Accessor("bits")
    BitSet getBits();
}
