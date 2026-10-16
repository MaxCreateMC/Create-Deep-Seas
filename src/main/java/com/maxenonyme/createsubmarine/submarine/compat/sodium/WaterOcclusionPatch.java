package com.maxenonyme.createsubmarine.submarine.compat.sodium;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WaterOcclusionPatch {

    private static final String UNIFORM_BLOCK = "uniform sampler2D SableCloseSampler;\n" +
            "uniform sampler2D SableFarSampler;\n" +
            "uniform float SableWaterOcclusionEnabled;\n";

    private static final String DISCARD_BLOCK = "    if (SableWaterOcclusionEnabled > 0.0) {\n" +
            "        float _csubCloseDepth = texelFetch(SableCloseSampler, %1$s, 0).r;\n" +
            "        float _csubFarDepth = texelFetch(SableFarSampler, %1$s, 0).r;\n" +
            "        float _csubFragDepth = gl_FragCoord.z;\n" +
            "        if (_csubCloseDepth < 1.0 && _csubFragDepth > _csubCloseDepth && _csubFragDepth < _csubFarDepth) {\n" +
            "            discard;\n" +
            "        }\n" +
            "    }\n";

    private static final Pattern SAFE_DIRECTIVE = Pattern.compile("^\\s*#(version|extension|define|undef|pragma|line|error|if|ifdef|ifndef|else|elif|endif)\\b");
    private static final Pattern MAIN_SIGNATURE = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void)?\\s*\\)\\s*\\{");

    private static final Map<Map<?, String>, Map<?, String>> UNTOUCHED = new WeakHashMap<>();

    public static String apply(String source, boolean atStart) {
        return apply(source, atStart, null);
    }

    public static String apply(String source, boolean atStart, String renderScale) {
        if (source == null || source.contains("SableWaterOcclusionEnabled"))
            return source;

        int insertPos = findAfterPreprocessor(source);
        if (source.contains("taau_render_scale"))
            renderScale = "taau_render_scale";
        String pixel = renderScale == null ? "ivec2(gl_FragCoord.xy)" : "ivec2(gl_FragCoord.xy / " + renderScale + ")";
        String body = injectIntoMain(source.substring(insertPos), atStart, DISCARD_BLOCK.formatted(pixel));
        if (body == null)
            return source;

        return source.substring(0, insertPos) + UNIFORM_BLOCK + body;
    }

    public static String declare(String source, String declaration) {
        int insertPos = findAfterPreprocessor(source);
        return source.substring(0, insertPos) + declaration + source.substring(insertPos);
    }

    public static synchronized void remember(Map<?, String> patched, Map<?, String> untouched) {
        UNTOUCHED.put(patched, untouched);
    }

    public static synchronized Map<?, String> untouched(Map<?, String> patched) {
        return UNTOUCHED.get(patched);
    }

    private static int findAfterPreprocessor(String source) {
        int pos = 0;
        boolean inBlockComment = false;

        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            boolean skip;

            if (inBlockComment) {
                skip = true;
                if (trimmed.contains("*/"))
                    inBlockComment = false;
            } else if (trimmed.startsWith("/*")) {
                skip = true;
                if (!trimmed.contains("*/"))
                    inBlockComment = true;
            } else {
                skip = trimmed.isEmpty() || trimmed.startsWith("//") || SAFE_DIRECTIVE.matcher(trimmed).find();
            }

            if (!skip)
                break;

            pos += line.length() + 1;
        }

        return Math.min(pos, source.length());
    }

    private static String injectIntoMain(String source, boolean atStart, String discard) {
        Matcher main = MAIN_SIGNATURE.matcher(source);
        if (!main.find())
            return null;

        int open = main.end() - 1;
        if (atStart)
            return source.substring(0, open + 1) + "\n" + discard + source.substring(open + 1);

        int close = matchingBrace(source, open);
        if (close < 0)
            return null;

        return source.substring(0, close) + "\n" + discard + source.substring(close);
    }

    private static int matchingBrace(String source, int open) {
        int depth = 0;
        int length = source.length();

        for (int i = open; i < length; i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    int eol = source.indexOf('\n', i);
                    if (eol < 0)
                        return -1;

                    i = eol;
                    continue;
                }

                if (next == '*') {
                    int end = source.indexOf("*/", i + 2);
                    if (end < 0)
                        return -1;

                    i = end + 1;
                    continue;
                }
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }

        return -1;
    }
}


// Doesn't work on a 3000-series Nvidia graphics card; if any developers are reading this, please fix the problem