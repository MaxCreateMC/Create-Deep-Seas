package com.maxenonyme.createsubmarine.submarine.client;

import com.mojang.blaze3d.vertex.VertexBuffer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class SonarScan {
    AdvancedFbo fbo;
    AdvancedFbo outline;
    AdvancedFbo result;
    int width;
    int height;
    int wantWidth;
    int wantHeight;

    VertexBuffer[] shells;
    VertexBuffer[] previous;
    BlockPos origin;
    BlockPos previousOrigin;
    boolean swept;

    long lastDraw;
    long lastPing;
    int idlePings;
    long lastSeen;

    float yaw;
    float unitsPerPixel;
    float floor = Float.NaN;
    final List<SonarContact> contacts = new ArrayList<>();

    public float floor() {
        return floor;
    }

    public List<SonarContact> contacts() {
        return contacts;
    }

    public float u0() {
        return (float) SonarView.PAD / (width + SonarView.PAD);
    }

    public int textureId() {
        return result == null ? -1 : result.getColorTextureAttachment(0).getId();
    }

    float front(long now) {
        return (now - lastPing) / (float) SonarView.SWEEP_NANOS * SonarView.SHELLS;
    }

    void resize(int w, int h) {
        if (fbo != null && w == width && h == height)
            return;
        freeTargets();
        width = w;
        height = h;
        int paddedWidth = w + SonarView.PAD;
        fbo = AdvancedFbo.withSize(paddedWidth, h).addColorTextureBuffer().setDepthTextureBuffer().build(true);
        outline = AdvancedFbo.withSize(paddedWidth, h).addColorTextureBuffer().build(true);
        result = AdvancedFbo.withSize(paddedWidth, h).addColorTextureBuffer().build(true);
    }

    void replace(VertexBuffer[] fresh, BlockPos at) {
        close(previous);
        previous = shells;
        previousOrigin = origin;
        if (previous != null)
            swept = true;
        shells = fresh;
        origin = at;
    }

    void free() {
        freeTargets();
        close(shells);
        close(previous);
        shells = previous = null;
    }

    private void freeTargets() {
        if (result != null) {
            CommandSubRenderTypes.forget(result.getColorTextureAttachment(0).getId());
            result.free();
        }
        if (fbo != null)
            fbo.free();
        if (outline != null)
            outline.free();
        fbo = outline = result = null;
    }

    private static void close(VertexBuffer[] buffers) {
        if (buffers == null)
            return;
        for (VertexBuffer buffer : buffers) {
            if (buffer != null)
                buffer.close();
        }
    }
}
