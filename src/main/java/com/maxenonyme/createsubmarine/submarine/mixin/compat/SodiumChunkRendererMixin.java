package com.maxenonyme.createsubmarine.submarine.mixin.compat;

import com.maxenonyme.createsubmarine.submarine.client.renderer.SodiumWaterOcclusionBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumChunkRendererMixin {

    private static Field createsubmarine$activeProgramField;
    private static boolean createsubmarine$lookupFailed;

    @Inject(method = "begin", at = @At("TAIL"), remap = false, require = 0)
    private void createsubmarine$applyOcclusionUniforms(@Coerce Object pass, CallbackInfo ci) {
        if (createsubmarine$lookupFailed)
            return;
        try {
            Field field = createsubmarine$activeProgramField;
            if (field == null) {
                field = findActiveProgramField(this.getClass());
                if (field == null) {
                    createsubmarine$lookupFailed = true;
                    return;
                }
                field.setAccessible(true);
                createsubmarine$activeProgramField = field;
            }
            Object program = field.get(this);
            if (program == null)
                return;
            int handle = (int) program.getClass().getMethod("handle").invoke(program);
            boolean translucent = (boolean) pass.getClass().getMethod("isTranslucent").invoke(pass);
            SodiumWaterOcclusionBridge.applyToProgram(handle, translucent);
        } catch (ReflectiveOperationException e) {
            createsubmarine$lookupFailed = true;
        }
    }

    private static Field findActiveProgramField(Class<?> from) {
        for (Class<?> c = from; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField("activeProgram");
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
