package com.maxenonyme.vortexengine;

import com.maxenonyme.vortexengine.mist.MistField;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

public class VortexCommand {

    public static void onRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vortex")
                .then(Commands.literal("profiler")
                        .then(Commands.literal("stop")
                                .executes(VortexCommand::stopProfiler))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(VortexCommand::profile)))
                .then(Commands.literal("style")
                        .executes(VortexCommand::styles)
                        .then(choices("light", VortexConfig.LIGHT_STYLE, VortexConfig.LightStyle.values()))
                        .then(choices("halo", VortexConfig.HALO_STYLE, VortexConfig.HaloStyle.values()))
                        .then(choices("water", VortexConfig.WATER_STYLE, VortexConfig.WaterStyle.values()))
                        .then(choices("mist", VortexConfig.MIST_STYLE, VortexConfig.MistStyle.values()))
                        .then(choices("motes", VortexConfig.MOTES_STYLE, VortexConfig.MotesStyle.values()))
                        .then(choices("wake", VortexConfig.WAKE_STYLE, VortexConfig.WakeStyle.values())))
                .then(Commands.literal("mist")
                        .then(Commands.literal("clear")
                                .executes(VortexCommand::clear))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 48))
                                .executes(context -> mist(context, 0.5f, "#C8D4E0"))
                                .then(Commands.argument("density", FloatArgumentType.floatArg(0.05f, 1))
                                        .executes(context -> mist(context,
                                                FloatArgumentType.getFloat(context, "density"), "#C8D4E0"))
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .executes(context -> mist(context,
                                                        FloatArgumentType.getFloat(context, "density"),
                                                        StringArgumentType.getString(context, "color"))))))));
    }

    private static int profile(CommandContext<CommandSourceStack> context) {
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        if (VortexProfiler.running()) {
            context.getSource().sendFailure(Component.literal(
                    "A Vortex profile is already running: wait for it or run /vortex profiler stop"));
            return 0;
        }

        VortexProfiler.start(seconds);
        context.getSource().sendSuccess(() -> Component.literal("Profiling Vortex for " + seconds
                + " s. Keep F3 closed for GPU timings, /vortex profiler stop ends it early."), false);
        return seconds;
    }

    private static int stopProfiler(CommandContext<CommandSourceStack> context) {
        if (!VortexProfiler.running()) {
            context.getSource().sendFailure(Component.literal("No Vortex profile is running"));
            return 0;
        }

        VortexProfiler.stop();
        return 1;
    }

    private static <T extends Enum<T>> LiteralArgumentBuilder<CommandSourceStack> choices(String name,
            ModConfigSpec.EnumValue<T> value, T[] options) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);

        for (T option : options)
            node.then(Commands.literal(lower(option)).executes(context -> pick(context, name, value, option)));

        return node;
    }

    private static <T extends Enum<T>> int pick(CommandContext<CommandSourceStack> context, String name,
            ModConfigSpec.EnumValue<T> value, T option) {
        value.set(option);
        value.save();
        context.getSource().sendSuccess(() -> Component.literal("Vortex " + name + " style: " + lower(option)), false);
        return 1;
    }

    private static int styles(CommandContext<CommandSourceStack> context) {
        String summary = "light " + lower(VortexConfig.LIGHT_STYLE.get())
                + ", halo " + lower(VortexConfig.HALO_STYLE.get())
                + ", water " + lower(VortexConfig.WATER_STYLE.get())
                + ", mist " + lower(VortexConfig.MIST_STYLE.get())
                + ", motes " + lower(VortexConfig.MOTES_STYLE.get())
                + ", wake " + lower(VortexConfig.WAKE_STYLE.get());
        context.getSource().sendSuccess(() -> Component.literal("Vortex styles: " + summary), false);
        return 1;
    }

    private static String lower(Enum<?> option) {
        return option.name().toLowerCase(Locale.ROOT);
    }

    private static int mist(CommandContext<CommandSourceStack> context, float density, String color) {
        CommandSourceStack source = context.getSource();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null)
            return 0;

        int rgb;

        try {
            rgb = Integer.parseInt(color.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Colours are written as #RRGGBB, for example #6FE8C8"));
            return 0;
        }

        if (!MistField.usable()) {
            source.sendFailure(Component.literal(
                    "Mist is turned off in create_submarine-vortex.toml, or a shader pack is in use"));
            return 0;
        }

        int radius = IntegerArgumentType.getInteger(context, "radius");
        int puffs = MistField.add(level, source.getPosition(), radius, density,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
        if (puffs == 0) {
            source.sendFailure(Component.literal("Too much mist already: run /vortex mist clear or raise maxPuffs"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Mist added: radius " + radius + ", " + puffs + " puffs"), false);
        return puffs;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int cleared = MistField.clear();
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + cleared + " mist volumes"), false);
        return cleared;
    }
}
