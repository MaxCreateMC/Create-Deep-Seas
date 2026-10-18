package com.maxenonyme.createsubmarine.compat.sodium;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.maxenonyme.highseas.config.HighSeasConfig;
import com.maxenonyme.vortexengine.VortexConfig;
import com.maxenonyme.vortexengine.VortexEngine;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

@ConfigEntryPointForge(CreateSubmarine.MOD_ID)
public class VideoOptions implements ConfigEntryPoint {

    private static final String KEY = "create_submarine.options.";

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .setNonTintedIcon(icon("deep_seas"))
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x4FB3FF))
                .addPage(effectsPage(builder));

        builder.registerModOptions("create_high_seas")
                .setNonTintedIcon(icon("high_seas"))
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0x7FD3E8))
                .addPage(seaPage(builder));

        if (!VortexEngine.installed())
            return;

        String version = ModList.get().getModContainerById(CreateSubmarine.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("1.0");

        builder.registerModOptions("vortex_engine", "Vortex Engine", version)
                .setNonTintedIcon(icon("vortex"))
                .setColorTheme(builder.createColorTheme().setBaseThemeRGB(0xFFA040))
                .addPage(lightsPage(builder))
                .addPage(waterPage(builder));
    }

    private static OptionPageBuilder lightsPage(ConfigBuilder builder) {
        ModConfigSpec spec = VortexConfig.SPEC;

        return builder.createOptionPage()
                .setName(Component.translatable(KEY + "page.lights"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.vortex"))
                        .addOption(toggle(builder, spec, VortexConfig.ENABLED, "vortex_enabled", OptionImpact.HIGH))
                        .addOption(toggle(builder, spec, VortexConfig.LIGHTS, "vortex_lights", OptionImpact.HIGH))
                        .addOption(number(builder, spec, VortexConfig.BRIGHTNESS, "brightness", 10, 200, 10,
                                OptionImpact.LOW, "percent"))
                        .addOption(number(builder, spec, VortexConfig.REACH, "reach", 50, 200, 10,
                                OptionImpact.LOW, "percent"))
                        .addOption(number(builder, spec, VortexConfig.SATURATION, "saturation", 0, 200, 10,
                                OptionImpact.LOW, "percent"))
                        .addOption(toggle(builder, spec, VortexConfig.ANIMATED_LIGHTS, "animated_lights",
                                OptionImpact.LOW))
                        .addOption(choice(builder, spec, VortexConfig.LIGHT_STYLE, VortexConfig.LightStyle.class,
                                "light_style", OptionImpact.VARIES)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.quality"))
                        .addOption(number(builder, spec, VortexConfig.LIGHT_DISTANCE, "light_distance", 16, 256, 8,
                                OptionImpact.MEDIUM, "blocks"))
                        .addOption(number(builder, spec, VortexConfig.MAX_LIGHTS, "max_lights", 16, 8192, 16,
                                OptionImpact.MEDIUM, "count"))
                        .addOption(toggle(builder, spec, VortexConfig.PIXEL_LIGHTING, "pixel_lighting",
                                OptionImpact.LOW))
                        .addOption(toggle(builder, spec, VortexConfig.SHADOWS, "shadows", OptionImpact.MEDIUM))
                        .addOption(toggle(builder, spec, VortexConfig.AUTO_GLOW, "auto_glow", OptionImpact.LOW))
                        .addOption(toggle(builder, spec, VortexConfig.BLOOM, "bloom", OptionImpact.LOW))
                        .addOption(number(builder, spec, VortexConfig.BLOOM_STRENGTH, "bloom_strength", 0, 200, 10,
                                OptionImpact.LOW, "percent")));
    }

    private static OptionPageBuilder waterPage(ConfigBuilder builder) {
        ModConfigSpec spec = VortexConfig.SPEC;

        return builder.createOptionPage()
                .setName(Component.translatable(KEY + "page.water"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.water"))
                        .addOption(toggle(builder, spec, VortexConfig.HAZE, "haze", OptionImpact.MEDIUM))
                        .addOption(number(builder, spec, VortexConfig.HALO_STRENGTH, "halo_strength", 0, 200, 10,
                                OptionImpact.LOW, "percent"))
                        .addOption(number(builder, spec, VortexConfig.SUNLIGHT, "sunlight", 0, 200, 10,
                                OptionImpact.LOW, "percent"))
                        .addOption(choice(builder, spec, VortexConfig.HALO_STYLE, VortexConfig.HaloStyle.class,
                                "halo_style", OptionImpact.MEDIUM))
                        .addOption(choice(builder, spec, VortexConfig.WATER_STYLE, VortexConfig.WaterStyle.class,
                                "water_style", OptionImpact.MEDIUM))
                        .addOption(choice(builder, spec, VortexConfig.MOTES_STYLE, VortexConfig.MotesStyle.class,
                                "motes_style", OptionImpact.LOW))
                        .addOption(choice(builder, spec, VortexConfig.WAKE_STYLE, VortexConfig.WakeStyle.class,
                                "wake_style", OptionImpact.LOW)))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.mist"))
                        .addOption(toggle(builder, spec, VortexConfig.MIST, "mist", OptionImpact.MEDIUM))
                        .addOption(choice(builder, spec, VortexConfig.MIST_STYLE, VortexConfig.MistStyle.class,
                                "mist_style", OptionImpact.MEDIUM))
                        .addOption(number(builder, spec, VortexConfig.MAX_PUFFS, "max_puffs", 64, 8000, 64,
                                OptionImpact.MEDIUM, "count")));
    }

    private static OptionPageBuilder effectsPage(ConfigBuilder builder) {
        return builder.createOptionPage()
                .setName(Component.translatable(KEY + "page.effects"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.submarine"))
                        .addOption(toggle(builder, SubmarineConfig.CLIENT_SPEC, SubmarineConfig.PRESSURE_EFFECTS,
                                "pressure_effects", OptionImpact.LOW))
                        .addOption(toggle(builder, SubmarineConfig.CLIENT_SPEC, SubmarineConfig.PHOTOSENSITIVE_MODE,
                                "photosensitive_mode", OptionImpact.LOW)));
    }

    private static OptionPageBuilder seaPage(ConfigBuilder builder) {
        IntegerOptionBuilder subdivisions = number(builder, HighSeasConfig.CLIENT_SPEC,
                HighSeasConfig.SAIL_SUBDIVISIONS, "sail_subdivisions", 1, 8, 1, OptionImpact.LOW, "count");
        follow(subdivisions, HighSeasConfig.SAIL_SUBDIVISIONS, value -> HighSeasConfig.sailSubdivisions = value);

        BooleanOptionBuilder shaderpack = toggle(builder, HighSeasConfig.CLIENT_SPEC,
                HighSeasConfig.SHADERPACK_SAILS, "shaderpack_sails", OptionImpact.LOW);
        follow(shaderpack, HighSeasConfig.SHADERPACK_SAILS, value -> HighSeasConfig.shaderpackSails = value);

        BooleanOptionBuilder seaglide = toggle(builder, HighSeasConfig.CLIENT_SPEC,
                HighSeasConfig.SEAGLIDE_SCREEN_EFFECTS, "seaglide_screen_effects", OptionImpact.LOW);
        follow(seaglide, HighSeasConfig.SEAGLIDE_SCREEN_EFFECTS, value -> HighSeasConfig.seaglideScreenEffects = value);

        return builder.createOptionPage()
                .setName(Component.translatable(KEY + "page.visuals"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(Component.translatable(KEY + "group.sea"))
                        .addOption(subdivisions)
                        .addOption(shaderpack)
                        .addOption(seaglide));
    }

    private static BooleanOptionBuilder toggle(ConfigBuilder builder, ModConfigSpec spec,
            ModConfigSpec.BooleanValue value, String name, OptionImpact impact) {
        return builder.createBooleanOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(Component.translatable(KEY + name + ".tooltip"))
                .setImpact(impact)
                .setDefaultValue(value.getDefault())
                .setStorageHandler(() -> save(spec))
                .setBinding(next -> write(spec, value, next), () -> read(spec, value));
    }

    private static IntegerOptionBuilder number(ConfigBuilder builder, ModConfigSpec spec,
            ModConfigSpec.IntValue value, String name, int min, int max, int step, OptionImpact impact, String unit) {
        return builder.createIntegerOption(id(name))
                .setName(Component.translatable(KEY + name))
                .setTooltip(Component.translatable(KEY + name + ".tooltip"))
                .setImpact(impact)
                .setRange(min, max, step)
                .setValueFormatter(amount -> Component.translatable(KEY + "unit." + unit, amount))
                .setDefaultValue(value.getDefault())
                .setStorageHandler(() -> save(spec))
                .setBinding(next -> write(spec, value, next), () -> read(spec, value));
    }

    private static <E extends Enum<E>> EnumOptionBuilder<E> choice(ConfigBuilder builder, ModConfigSpec spec,
            ModConfigSpec.EnumValue<E> value, Class<E> type, String name, OptionImpact impact) {
        return builder.createEnumOption(id(name), type)
                .setName(Component.translatable(KEY + name))
                .setTooltip(Component.translatable(KEY + name + ".tooltip"))
                .setImpact(impact)
                .setElementNameProvider(element -> Component.translatable(
                        KEY + name + "." + element.name().toLowerCase(Locale.ROOT)))
                .setDefaultValue(value.getDefault())
                .setStorageHandler(() -> save(spec))
                .setBinding(next -> write(spec, value, next), () -> read(spec, value));
    }

    private static void follow(IntegerOptionBuilder option, ModConfigSpec.IntValue value, IntConsumer cache) {
        option.setBinding(next -> {
            write(HighSeasConfig.CLIENT_SPEC, value, next);
            cache.accept(next);
        }, () -> read(HighSeasConfig.CLIENT_SPEC, value));
    }

    private static void follow(BooleanOptionBuilder option, ModConfigSpec.BooleanValue value, Consumer<Boolean> cache) {
        option.setBinding(next -> {
            write(HighSeasConfig.CLIENT_SPEC, value, next);
            cache.accept(next);
        }, () -> read(HighSeasConfig.CLIENT_SPEC, value));
    }

    private static <T> T read(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> value) {
        return spec.isLoaded() ? value.get() : value.getDefault();
    }

    private static <T> void write(ModConfigSpec spec, ModConfigSpec.ConfigValue<T> value, T next) {
        if (spec.isLoaded())
            value.set(next);
    }

    private static void save(ModConfigSpec spec) {
        if (spec.isLoaded())
            spec.save();
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "textures/gui/options/" + name + ".png");
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, name);
    }
}
