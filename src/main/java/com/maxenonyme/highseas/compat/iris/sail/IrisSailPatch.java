package com.maxenonyme.highseas.compat.iris.sail;

import com.maxenonyme.highseas.CreateHighSeas;
import com.maxenonyme.highseas.config.HighSeasConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IrisSailPatch {

    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)\\s*\\{");
    private static final Pattern POSITION_IN = Pattern.compile("\\bin\\s+vec3\\s+iris_Position\\s*;");
    private static final Pattern POSITION = Pattern.compile("\\biris_Position\\b");
    private static final Pattern UV_IN = Pattern.compile("\\bin\\s+vec2\\s+iris_UV0\\s*;");

    private static final String UNIFORMS = """
            uniform vec3 cds_offset;
            uniform float cds_time;
            uniform int cds_sailCount;
            uniform vec3 cds_sailMin[8];
            uniform vec3 cds_sailMax[8];
            uniform vec3 cds_sailAxis[8];
            uniform vec3 cds_supportDir[8];
            uniform vec3 cds_bulge[8];
            uniform float cds_furl[8];
            uniform vec4 cds_sailCut[8];
            uniform int cds_spriteCount;
            uniform vec4 cds_sprites[32];

            bool cds_onSailSprite(vec2 uv) {
                for (int i = 0; i < 32; i++) {
                    if (i >= cds_spriteCount)
                        break;
                    vec4 r = cds_sprites[i];
                    if (uv.x >= r.x && uv.x <= r.z && uv.y >= r.y && uv.y <= r.w)
                        return true;
                }
                return false;
            }

            vec3 cds_billow(vec3 raw) {
                if (cds_sailCount <= 0 || !(%s))
                    return raw;

                vec3 pos = raw + iris_ChunkOffset;
                vec3 localPos = pos - cds_offset;

                int found = -1;
                float nearest = 1.0e9;
                for (int i = 0; i < 8; i++) {
                    if (i >= cds_sailCount)
                        break;
                    bool inside = all(greaterThanEqual(localPos, cds_sailMin[i] - 0.5))
                            && all(lessThanEqual(localPos, cds_sailMax[i] + 0.5));
                    if (inside) {
                        vec3 normal = abs(cds_sailAxis[i]);
                        vec3 spill = max(max(cds_sailMin[i] - localPos, localPos - cds_sailMax[i]), 0.0);
                        vec3 middle = (cds_sailMin[i] + cds_sailMax[i]) * 0.5;
                        float d = abs(dot(localPos - middle, normal)) + 4.0 * dot(spill, vec3(1.0) - normal);
                        if (d < nearest) {
                            nearest = d;
                            found = i;
                        }
                    }
                }

                if (found < 0)
                    return raw;

                vec3 mn = cds_sailMin[found];
                vec3 mx = cds_sailMax[found];
                vec3 ax = cds_sailAxis[found];
                vec3 dims = max(mx - mn, vec3(0.001));
                vec3 t = clamp((localPos - mn) / dims, 0.0, 1.0);
                vec3 plane = vec3(1.0) - abs(ax);

                vec2 uv;
                if (plane.x < 0.5)
                    uv = vec2(t.y, t.z);
                else if (plane.y < 0.5)
                    uv = vec2(t.x, t.z);
                else
                    uv = vec2(t.x, t.y);

                float f = abs(cds_furl[found]);
                pos += cds_supportDir[found] * 0.375;

                float anchor = pow(sin(uv.x * 3.14159265), 0.5) * pow(sin(uv.y * 3.14159265), 0.5);
                vec4 cut = cds_sailCut[found];
                if (cut.w > 0.0) {
                    vec3 q = localPos - mn;
                    vec3 m = mix(dims - q, q, step(0.0, cut.xyz)) * abs(cut.xyz);
                    float s = cut.w - (m.x + m.y + m.z);
                    anchor *= pow(sin(clamp(s / max(cut.w * 0.5, 0.5), 0.0, 1.0) * 3.14159265 * 0.5), 0.5);
                }
                float ripple = sin((uv.x + uv.y) * 9.0 + cds_time * 1.6) * sin((uv.x - uv.y) * 5.0 - cds_time * 1.1);
                pos += cds_bulge[found] * (anchor * (1.0 + 0.18 * ripple)) * (1.0 - f);

                if (f > 0.0) {
                    float hfrac = clamp((localPos.y - mn.y) / max(mx.y - mn.y, 0.001), 0.0, 1.0);
                    float footY = mn.y + cds_offset.y;
                    pos.y = footY + (pos.y - footY) * (1.0 - f * 0.55);
                    pos += ax * sin(hfrac * 3.14159265 * 6.0 + cds_time * 0.15) * 0.22 * f;
                }

                return pos - iris_ChunkOffset;
            }

            """;

    public static String apply(String name, String vertex) {
        if (!HighSeasConfig.shaderpackSails)
            return null;

        String patched = patch(vertex);
        return patched != null && compiles(name, patched) ? patched : null;
    }

    public static String patch(String vertex) {
        if (vertex == null || vertex.contains("cds_billow") || !vertex.contains("iris_ChunkOffset"))
            return null;
        if (!UV_IN.matcher(vertex).find())
            return null;

        Matcher in = POSITION_IN.matcher(vertex);
        if (!in.find())
            return null;

        String head = vertex.substring(0, in.end());
        String body = POSITION.matcher(vertex.substring(in.end())).replaceAll("cds_sailPosition");

        Matcher main = MAIN.matcher(body);
        if (!main.find())
            return null;

        return head + "\nvec3 cds_sailPosition;\n"
                + body.substring(0, main.start())
                + UNIFORMS.formatted("cds_onSailSprite(iris_UV0)")
                + body.substring(main.start(), main.end())
                + "\n    cds_sailPosition = cds_billow(iris_Position);\n"
                + body.substring(main.end());
    }

    private static boolean compiles(String name, String source) {
        if (!RenderSystem.isOnRenderThreadOrInit())
            return false;

        int shader = GL20C.glCreateShader(GL20C.GL_VERTEX_SHADER);

        try {
            GL20C.glShaderSource(shader, source);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == GL11C.GL_TRUE)
                return true;

            CreateHighSeas.LOGGER.warn("The shader pack's {} program did not compile with billowing sails, "
                    + "its sails stay flat: {}", name, GL20C.glGetShaderInfoLog(shader));
            return false;
        } finally {
            GL20C.glDeleteShader(shader);
        }
    }
}
