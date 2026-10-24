package com.maxenonyme.vortexengine;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.highseas.compat.iris.IrisCompat;
import com.maxenonyme.vortexengine.light.LightField;
import com.maxenonyme.vortexengine.light.LightIndex;
import com.maxenonyme.vortexengine.light.ShadowGrid;
import com.maxenonyme.vortexengine.light.VortexLights;
import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.FogType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

//j'ajouterai plus tard un bench avec Veil, pour check les perf



public class VortexProfiler {

    public enum Pass {
        UPLOAD("Grid upload"),
        INJECTION("Moving light injection"),
        PROPAGATION("Light propagation"),
        LIGHTS("Light volumes"),
        SURFACE("Light on blocks"),
        COMPOSITE("Composite and bloom"),
        ATMOSPHERE("Atmosphere"),
        MIST("Mist puffs");

        final String label;

        Pass(String label) {
            this.label = label;
        }
    }

    public enum Counter {
        BEAMS("Moving lights drawn as volumes"),
        SPREAD("Moving lights spread in the field"),
        TEXEL_WRITES("Emission texels written"),
        MIST_VOLUMES("Voxel mist volumes"),
        PUFFS("Mist puffs drawn"),
        PIXEL_LIGHTS("Lights in the pixel pass");

        final String label;

        Counter(String label) {
            this.label = label;
        }
    }

    private static class Timing {
        final ArrayDeque<Integer> pending = new ArrayDeque<>();
        final ArrayDeque<Integer> spare = new ArrayDeque<>();
        long cpu, cpuWorst, gpu, gpuWorst;
        int cpuRuns, gpuRuns, query;
        long started;
    }

    private static class Tally {
        long sum;
        int worst, frames;
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Map<Pass, Timing> TIMINGS = new EnumMap<>(Pass.class);
    private static final Map<Counter, Tally> TALLIES = new EnumMap<>(Counter.class);
    private static final AtomicLong BUILD_TIME = new AtomicLong();
    private static final AtomicInteger BUILDS = new AtomicInteger();

    private static volatile boolean running;
    private static long[] frames = new long[8192];
    private static int frameCount;
    private static long lastFrame;
    private static long startedAt, endsAt;
    private static int asked;
    private static boolean gpuTimers;
    private static boolean gpuBusy;
    private static int blindFrames, wetFrames, uploads;
    private static long uploadBytes;
    private static final Map<VortexConfig.LightStyle, Integer> LIGHT_FRAMES =
            new EnumMap<>(VortexConfig.LightStyle.class);
    private static final Map<VortexConfig.MistStyle, Integer> MIST_FRAMES = new EnumMap<>(VortexConfig.MistStyle.class);

    public static boolean running() {
        return running;
    }

    public static void start(int seconds) {
        TIMINGS.clear();
        TALLIES.clear();
        for (Pass pass : Pass.values())
            TIMINGS.put(pass, new Timing());
        for (Counter counter : Counter.values())
            TALLIES.put(counter, new Tally());

        LIGHT_FRAMES.clear();
        MIST_FRAMES.clear();
        BUILD_TIME.set(0);
        BUILDS.set(0);
        frameCount = 0;
        lastFrame = 0;
        blindFrames = wetFrames = uploads = 0;
        uploadBytes = 0;
        asked = seconds;
        gpuTimers = GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_timer_query;
        gpuBusy = false;
        startedAt = System.nanoTime();
        endsAt = startedAt + seconds * 1_000_000_000L;
        running = true;
    }

    public static void stop() {
        if (running)
            finish(true);
    }

    public static void begin(Pass pass) {
        if (!running)
            return;

        Timing timing = TIMINGS.get(pass);
        timing.started = System.nanoTime();
        if (!gpuTimers || gpuBusy || Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            return;

        timing.query = timing.spare.isEmpty() ? GL15.glGenQueries() : timing.spare.poll();
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, timing.query);
        gpuBusy = true;
    }

    public static void end(Pass pass) {
        if (!running)
            return;

        Timing timing = TIMINGS.get(pass);
        if (timing.started == 0)
            return;

        long spent = System.nanoTime() - timing.started;
        timing.started = 0;
        timing.cpu += spent;
        timing.cpuWorst = Math.max(timing.cpuWorst, spent);
        timing.cpuRuns++;

        if (timing.query != 0) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            timing.pending.add(timing.query);
            timing.query = 0;
            gpuBusy = false;
        }
    }

    public static void count(Counter counter, int value) {
        if (!running)
            return;

        Tally tally = TALLIES.get(counter);
        tally.sum += value;
        tally.worst = Math.max(tally.worst, value);
        tally.frames++;
    }

    public static void recordBuild(long nanos) {
        if (!running)
            return;

        BUILD_TIME.addAndGet(nanos);
        BUILDS.incrementAndGet();
    }

    public static void recordUpload(long bytes, boolean complete) {
        if (!running)
            return;

        if (complete)
            uploads++;
        uploadBytes += bytes;
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!running || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL)
            return;

        long now = System.nanoTime();
        if (lastFrame != 0) {
            if (frameCount == frames.length)
                frames = Arrays.copyOf(frames, frames.length * 2);
            frames[frameCount++] = now - lastFrame;
        }

        lastFrame = now;
        LIGHT_FRAMES.merge(VortexConfig.lightStyle(), 1, Integer::sum);
        MIST_FRAMES.merge(VortexConfig.MIST_STYLE.get(), 1, Integer::sum);
        if (event.getCamera().getFluidInCamera() == FogType.WATER)
            wetFrames++;
        if (Minecraft.getInstance().getDebugOverlay().showDebugScreen())
            blindFrames++;

        poll(false);
        if (now >= endsAt)
            finish(false);
    }

    private static void poll(boolean wait) {
        for (Timing timing : TIMINGS.values()) {
            while (!timing.pending.isEmpty()) {
                int query = timing.pending.peek();
                if (!wait && GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == 0)
                    break;

                long spent = GL33.glGetQueryObjecti64(query, GL15.GL_QUERY_RESULT);
                timing.gpu += spent;
                timing.gpuWorst = Math.max(timing.gpuWorst, spent);
                timing.gpuRuns++;
                timing.spare.add(timing.pending.poll());
            }
        }
    }

    private static void finish(boolean early) {
        poll(true);
        running = false;

        for (Timing timing : TIMINGS.values()) {
            for (int query : timing.spare)
                GL15.glDeleteQueries(query);
            timing.spare.clear();
        }

        Minecraft mc = Minecraft.getInstance();
        Path file = FMLPaths.GAMEDIR.get().resolve("vortex-profiles")
                .resolve("vortex-profile-" + LocalDateTime.now().format(STAMP) + ".txt");

        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report(early, System.nanoTime() - startedAt));
        } catch (IOException e) {
            CreateSubmarine.LOGGER.warn("Couldn't write the Vortex profile", e);
            if (mc.player != null)
                mc.player.displayClientMessage(Component.literal("Couldn't write the Vortex profile: " + e.getMessage())
                        .withStyle(ChatFormatting.RED), false);
            return;
        }

        if (mc.player == null)
            return;

        String path = file.toAbsolutePath().toString();
        Component link = Component.literal(file.getFileName().toString()).withStyle(style -> style
                .withUnderlined(true)
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path)));
        mc.player.displayClientMessage(Component.literal("Vortex profile saved: ").append(link), false);
    }

    private static String report(boolean early, long elapsed) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int measured = Math.max(frameCount, 1);
        StringBuilder out = new StringBuilder();

        line(out, "Vortex Engine profile");
        line(out, "=====================");
        line(out, "Date: %s", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        line(out, "Duration: asked %d s, measured %.1f s%s", asked, elapsed / 1e9, early ? " (stopped early)" : "");
        line(out, "Frames: %d, camera under water in %.0f %% of them", frameCount, 100.0 * wetFrames / measured);
        line(out, "");

        line(out, "Settings");
        line(out, "- Vortex usable: %s%s", VortexLights.usable() ? "yes" : "no",
                IrisCompat.isShaderPackActive() ? " (shader pack active)" : "");
        line(out, "- Styles: light %s, halo %s, water %s, mist %s, motes %s",
                VortexConfig.lightStyle(), VortexConfig.HALO_STYLE.get(), VortexConfig.WATER_STYLE.get(),
                VortexConfig.MIST_STYLE.get(), VortexConfig.MOTES_STYLE.get());
        line(out, "- pixelLighting %s, shadows %s, haze %s, autoGlow %s, bloom %s, mist %s",
                VortexConfig.PIXEL_LIGHTING.get(), VortexConfig.SHADOWS.get(), VortexConfig.HAZE.get(),
                VortexConfig.AUTO_GLOW.get(), VortexConfig.BLOOM.get(), VortexConfig.MIST.get());
        line(out, "- lightDistance %d, maxLights %d, maxPuffs %d", VortexConfig.LIGHT_DISTANCE.get(),
                VortexConfig.MAX_LIGHTS.get(), VortexConfig.MAX_PUFFS.get());
        line(out, "- Light style over the profile: %s", shares(LIGHT_FRAMES));
        line(out, "- Mist style over the profile: %s", shares(MIST_FRAMES));
        line(out, "");

        line(out, "Machine");
        line(out, "- GPU: %s (%s), OpenGL %s", GlUtil.getRenderer(), GlUtil.getVendor(), GlUtil.getOpenGLVersion());
        line(out, "- CPU: %s, %d threads", GlUtil.getCpuInfo(), Runtime.getRuntime().availableProcessors());
        line(out, "- Java %s, heap %d / %d MB", System.getProperty("java.version"),
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) >> 20,
                Runtime.getRuntime().maxMemory() >> 20);
        line(out, "- Screen %d x %d, render distance %d, graphics %s", main.width, main.height,
                mc.options.getEffectiveRenderDistance(), mc.options.graphicsMode().get());
        line(out, "- Mods: create_submarine %s, sodium %s, iris %s, veil %s, distanthorizons %s", version(),
                loaded("sodium"), loaded("iris"), loaded("veil"), loaded("distanthorizons"));
        line(out, "");

        appendFrames(out);
        appendPasses(out, measured);

        line(out, "Per frame (average / worst)");
        for (Counter counter : Counter.values()) {
            Tally tally = TALLIES.get(counter);
            line(out, "- %-36s %8.1f / %d", counter.label, tally.frames == 0 ? 0 : (double) tally.sum / tally.frames,
                    tally.worst);
        }
        line(out, "");

        LightIndex.Census census = LightIndex.census();
        int builds = BUILDS.get();
        line(out, "Grid and index");
        line(out, "- Grid rebuilds on the worker: %d, %.2f ms each", builds,
                builds == 0 ? 0 : BUILD_TIME.get() / 1e6 / builds);
        line(out, "- Grid uploads: %d, %.1f MB in total", uploads, uploadBytes / 1048576.0);
        line(out, "- Indexed sections: %d with glows, %d with solids, %d glowing blocks", census.glowSections(),
                census.solidSections(), census.emitters());
        line(out, "- Video memory held: about %.0f MB", memory(main) / 1048576.0);
        line(out, "");

        line(out, "Notes");
        if (!gpuTimers)
            line(out, "- This driver has no GPU timer queries: only CPU times are measured.");
        if (blindFrames > 0)
            line(out, "- GPU times skipped on %d frames because F3 was open.", blindFrames);
        line(out, "- CPU times are the render thread time spent sending each pass, GPU times are its real cost.");
        return out.toString();
    }

    private static void appendFrames(StringBuilder out) {
        line(out, "Frames");
        if (frameCount == 0) {
            line(out, "- No frame was drawn.");
            line(out, "");

            return;
        }

        long[] sorted = Arrays.copyOf(frames, frameCount);
        Arrays.sort(sorted);
        long total = 0;
        for (long frame : sorted)
            total += frame;

        int slowest = Math.max(1, frameCount / 100);
        long slow = 0;
        for (int i = frameCount - slowest; i < frameCount; i++)
            slow += sorted[i];

        line(out, "- Average %.1f FPS (%.2f ms)", 1e9 * frameCount / total, total / 1e6 / frameCount);
        long median = sorted[frameCount / 2];
        long high = sorted[(int) (frameCount * 0.95)];
        long higher = sorted[Math.min(frameCount - 1, (int) (frameCount * 0.99))];
        line(out, "- Median %.2f ms, 95th %.2f ms, 99th %.2f ms, worst %.2f ms", median / 1e6, high / 1e6,
                higher / 1e6, sorted[frameCount - 1] / 1e6);
        line(out, "- 1%% low %.1f FPS", 1e9 * slowest / slow);
        line(out, "");

    }

    private static void appendPasses(StringBuilder out, int measured) {
        int gpuFrames = Math.max(measured - blindFrames, 1);
        double gpuTotal = 0, cpuTotal = 0;

        line(out, "Vortex passes, milliseconds (per frame = spread over every frame, per run = when it ran)");
        line(out, "%-24s %10s %10s %10s %10s %10s %8s", "Pass", "GPU frame", "GPU run", "GPU worst", "CPU frame",
                "CPU worst", "Runs");

        for (Pass pass : Pass.values()) {
            Timing timing = TIMINGS.get(pass);
            double gpu = timing.gpu / 1e6 / gpuFrames;
            double run = timing.gpuRuns == 0 ? 0 : timing.gpu / 1e6 / timing.gpuRuns;
            double cpu = timing.cpu / 1e6 / measured;
            gpuTotal += gpu;
            cpuTotal += cpu;
            line(out, "%-24s %10.3f %10.3f %10.3f %10.3f %10.3f %8d", pass.label, gpu, run, timing.gpuWorst / 1e6,
                    cpu, timing.cpuWorst / 1e6, timing.cpuRuns);
        }

        double frame = frameCount == 0 ? 0 : Arrays.stream(frames, 0, frameCount).sum() / 1e6 / frameCount;
        line(out, "%-24s %10.3f %10s %10s %10.3f", "Total", gpuTotal, "", "", cpuTotal);
        if (frame > 0)
            line(out, "Vortex GPU time is %.0f %% of the average frame.", 100 * gpuTotal / frame);
        line(out, "");

    }

    private static String shares(Map<? extends Enum<?>, Integer> counted) {
        int total = counted.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0)
            return "none";

        StringBuilder out = new StringBuilder();
        counted.forEach((style, frames) -> out.append(out.isEmpty() ? "" : ", ").append(style)
                .append(String.format(Locale.ROOT, " %.0f %%", 100.0 * frames / total)));
        return out.toString();
    }

    private static long memory(RenderTarget main) {
        long bytes = 0;
        if (ShadowGrid.ready())
            bytes += 128L * 128 * 128 * 4;
        if (ShadowGrid.hasField())
            bytes += 128L * 128 * 128 * 4 + 128 * 128;
        if (LightField.ready())
            bytes += 2L * 128 * 128 * 128 * 8;

        long screen = (long) main.width * main.height * 4;
        return bytes + screen * 4 / 3 + screen / 4 + screen * 2;
    }

    private static String version() {
        return ModList.get().getModContainerById(CreateSubmarine.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    private static String loaded(String mod) {
        return ModList.get().isLoaded(mod) ? "yes" : "no";
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(args.length == 0 ? format : String.format(Locale.ROOT, format, args)).append('\n');
    }
}
