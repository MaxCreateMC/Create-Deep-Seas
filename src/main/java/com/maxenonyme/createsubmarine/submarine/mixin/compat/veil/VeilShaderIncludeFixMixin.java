package com.maxenonyme.createsubmarine.submarine.mixin.compat.veil;

import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(value = ShaderInstance.class, remap = false)
public abstract class VeilShaderIncludeFixMixin {

    @ModifyVariable(method = "veil$recompile(ZLjava/lang/String;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private String createsubmarine$stripBadIncludes(String source) {
        if (source == null || !source.contains("#include"))
            return source;

        StringBuilder out = new StringBuilder(source.length());

        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String rest = trimmed.substring(8).trim();
                if (!rest.startsWith("\"") && !rest.startsWith("<")) {
                    out.append("// stripped: ").append(trimmed).append('\n');
                    continue;
                }
            }

            out.append(line).append('\n');
        }

        if (out.length() > 0 && source.charAt(source.length() - 1) != '\n') {
            out.setLength(out.length() - 1);
        }

        return out.toString();
    }
}
