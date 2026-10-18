package com.maxenonyme.createsubmarine.submarine.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ComplementarySupport {

    private static final Pattern UNDERWATER = Pattern.compile(
            "if\\s*\\(\\s*isEyeInWater\\s*==\\s*1\\s*\\)\\s*\\{\\s*if\\s*\\(\\s*z0\\s*==\\s*1\\.0\\s*\\)\\s*color\\.rgb\\s*=\\s*waterFogColor\\s*;");

    private static final String FOG = """
            float cdsWater = cdsWaterBehindHull(texCoord, z1);
                if (isEyeInWater == 0 && cdsWater > 0.0)
                    color.rgb = mix(color.rgb, waterFogColor, z1 >= 1.0 ? 1.0 : GetWaterFog(cdsWater));

                """;

    static String patch(String source) {
        Matcher underwater = UNDERWATER.matcher(source);
        if (!underwater.find() || !ShaderpackSupport.uses(source, "texCoord", "z1", "GetWaterFog", "gbufferProjectionInverse"))
            return source;

        return source.substring(0, underwater.start()) + FOG + source.substring(underwater.start());
    }
}
