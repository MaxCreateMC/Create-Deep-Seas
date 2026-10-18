package com.maxenonyme.createsubmarine.submarine.compat.iris;

import com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BslSupport {

    private static final Pattern UNDERWATER = Pattern.compile(
            "if\\s*\\(\\s*isEyeInWater\\s*==\\s*1\\.0\\s*\\)\\s*\\{\\s*vec4\\s+waterFog\\s*=\\s*GetWaterFog\\s*\\(\\s*viewPos\\.xyz\\s*,\\s*vec3\\s*\\(\\s*0\\.0\\s*\\)\\s*\\)\\s*;");
    private static final Pattern ALBEDO = Pattern.compile(
            "if\\s*\\(\\s*isEyeInWater\\s*==\\s*0\\s*\\)\\s*waterFogColor\\s*=\\s*waterAlbedo\\s*;");

    private static final String FOG = """
            float cdsWater = cdsWaterBehindHull(texCoord, z1);
            	if (isEyeInWater == 0.0 && cdsWater > 0.0) {
            		cdsBeyondHull = true;
            		vec4 cdsFog = GetWaterFog(normalize(viewPos.xyz) * cdsWater, vec3(0.0));
            		cdsBeyondHull = false;
            		color.rgb = mix(sqrt(color.rgb), sqrt(cdsFog.rgb), cdsFog.a);
            		color.rgb *= color.rgb;
            	}

            	""";

    static String patch(String source) {
        Matcher underwater = UNDERWATER.matcher(source);
        if (!underwater.find() || !ShaderpackSupport.uses(source, "texCoord", "z1", "gbufferProjectionInverse"))
            return source;

        String result = source.substring(0, underwater.start()) + FOG + source.substring(underwater.start());
        result = ALBEDO.matcher(result)
                .replaceFirst("if (isEyeInWater == 0 && !cdsBeyondHull) waterFogColor = waterAlbedo;");
        return WaterOcclusionPatch.declare(result, "bool cdsBeyondHull = false;\n");
    }
}
