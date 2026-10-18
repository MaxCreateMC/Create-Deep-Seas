package com.maxenonyme.createsubmarine.submarine.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BlissSupport {

    private static final Pattern SCREEN_UV = Pattern.compile(
            "vec3\\s+fragpos\\s*=\\s*toScreenSpace_DH\\s*\\(\\s*(.+?)\\s*,\\s*z\\s*,\\s*DH_depth0\\s*\\)\\s*;");
    private static final Pattern WATER_COEFFICIENTS = Pattern.compile(
            "if\\s*\\(\\s*isEyeInWater\\s*==\\s*1\\s*\\)\\s*\\{\\s*(float\\s+dirtAmount\\s*=[^;]+;\\s*vec3\\s+waterEpsilon\\s*=[^;]+;"
                    + "\\s*vec3\\s+dirtEpsilon\\s*=[^;]+;\\s*vec3\\s+totEpsilon\\s*=[^;]+;)");
    private static final Pattern SCENE_COLOR = Pattern.compile(
            "vec3\\s+color\\s*=\\s*texture2D\\s*\\(\\s*colortex3\\s*,\\s*refractedCoord\\s*\\)\\s*\\.rgb\\s*;");

    private static final String FOG = """

              float cdsWater = cdsWaterBehindHull(%s, z2);
              if (isEyeInWater == 0 && cdsWater > 0.0) {
                %s
                vec3 cdsAbsorb = exp(-totEpsilon * min(cdsWater, 256.0));
                float cdsEyeLight = clamp(eyeBrightnessSmooth.y / 240.0, 0.1, 1.0);
                vec3 cdsAmbient = texelFetch2D(colortex4, ivec2(0, 37), 0).rgb / 30.0 * pow(clamp(np3.y, -1.0, 1.0) * 0.3 + 0.7, 1.5);
                vec3 cdsSun = texelFetch2D(colortex4, ivec2(6, 37), 0).rgb / 80.0 * clamp(WsunVec.y, 0.0, 1.0) * 0.5;
                vec3 cdsScatter = (cdsAmbient + cdsSun) * cdsEyeLight * dirtAmount * 0.7 / 3.14;
                color = color * cdsAbsorb + cdsScatter * (1.0 - cdsAbsorb) / totEpsilon;
              }
            """;

    static String patch(String source) {
        Matcher uv = SCREEN_UV.matcher(source);
        Matcher coefficients = WATER_COEFFICIENTS.matcher(source);
        Matcher scene = SCENE_COLOR.matcher(source);
        if (!uv.find() || !coefficients.find() || !scene.find() || scene.start() < uv.end()
                || !ShaderpackSupport.uses(source, "z2", "np3", "WsunVec", "colortex4", "eyeBrightnessSmooth", "gbufferProjectionInverse"))
            return source;
        String fog = FOG.formatted("(" + uv.group(1) + ")", coefficients.group(1));
        return source.substring(0, scene.end()) + fog + source.substring(scene.end());
    }
}
