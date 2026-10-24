package com.maxenonyme.createsubmarine.submarine.compat.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PhotonSupport {

    private static final Pattern VL_END = Pattern.compile("vec3\\s+world_end_pos\\s*=\\s*world_pos\\s*;");
    private static final Pattern AIR_BEHIND_GLASS = Pattern.compile(
            "if\\s*\\(\\s*is_translucent\\s*\\)\\s*\\{(\\s*mat2x3\\s+analytic_fog\\s*=\\s*air_fog_analytic\\s*\\(\\s*front_position_world\\s*,)");
    private static final Pattern WATER_DISTANCE = Pattern.compile(
            "(water_fog_simple\\s*\\(\\s*light_color\\s*,\\s*ambient_color\\s*,\\s*water_absorption_coeff\\s*,\\s*"
                    + "vec2\\s*\\(\\s*0\\.0\\s*,\\s*eye_skylight\\s*\\)\\s*,\\s*)view_distance");
    private static final Pattern BLEND = Pattern.compile("vec3\\s+blend_layers_with_fog\\s*\\(");
    private static final Pattern MINIMUM_WATER = Pattern.compile(
            "dist\\s*=\\s*max\\s*\\(\\s*dist\\s*,\\s*2\\.0\\s*-\\s*1\\.0\\s*\\*\\s*skylight_factor\\s*\\)\\s*;");

    private static final String VL_START = """

                float cdsHull = CdsHullReady > 0.5 ? texture(CdsHullFar, uv).r : 1.0;
                if (cdsHull < 1.0) {
                    vec3 cdsDir = normalize(scene_pos - gbufferModelViewInverse[3].xyz + 1e-4);
                    if (depth1 > cdsHull) {
                        world_start_pos = view_to_scene_space(screen_to_view_space(vec3(uv, cdsHull), true)) + cameraPosition;
                        world_end_pos = world_back_pos;
                    }
                    if (depth1 <= cdsHull || !(distance(world_start_pos, world_end_pos) > 0.01))
                        world_start_pos = world_end_pos - cdsDir * 0.01;
                }
            """;

    private static final String WATER_DISTANCE_FUNCTION = """
            float cdsWaterDistance(float viewDistance, float backDistance) {
                float hull = CdsHullReady > 0.5 ? texture(CdsHullFar, uv).r : 1.0;
                if (hull >= 1.0)
                    return viewDistance;
                vec4 hullView = gbufferProjectionInverse * vec4(vec3(uv, hull) * 2.0 - 1.0, 1.0);
                return max(backDistance - length(hullView.xyz / hullView.w), 0.0);
            }

            """;

    static String patch(String source) {
        String result = source;
        Matcher vl = VL_END.matcher(result);
        if (vl.find() && result.contains("raymarch_water_fog"))
            result = result.substring(0, vl.end()) + VL_START + result.substring(vl.end());

        Matcher blend = BLEND.matcher(result);
        if (blend.find()) {
            result = result.substring(0, blend.start()) + WATER_DISTANCE_FUNCTION + result.substring(blend.start());
            result = AIR_BEHIND_GLASS.matcher(result).replaceFirst(
                    "if (is_translucent && (CdsHullReady > 0.5 ? texture(CdsHullFar, uv).r : 1.0) >= 1.0) {$1");
            result = WATER_DISTANCE.matcher(result)
                    .replaceFirst("$1cdsWaterDistance(view_distance, min(length(back_position_view), far))");
            result = MINIMUM_WATER.matcher(result)
                    .replaceFirst("if (dist > 0.0) dist = max(dist, 2.0 - 1.0 * skylight_factor);");
        }

        return result;
    }
}
