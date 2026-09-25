package com.maxenonyme.createsubmarine.submarine.system;

import com.maxenonyme.createsubmarine.CreateSubmarine;
import com.maxenonyme.createsubmarine.submarine.config.SubmarineConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

public class ConfigCondition implements ICondition {
    public static final ResourceLocation NAME = ResourceLocation.fromNamespaceAndPath(CreateSubmarine.MOD_ID, "config_enabled");

    public static final MapCodec<ConfigCondition> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    Codec.STRING.fieldOf("config_key").forGetter(ConfigCondition::getConfigKey)
            ).apply(builder, ConfigCondition::new)
    );

    private final String configKey;

    public ConfigCondition(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    @Override
    public boolean test(IContext context) {
        if (configKey.equalsIgnoreCase("enableAbyssDimension")) {
            return !FMLEnvironment.production;
        }
        if (!SubmarineConfig.COMMON_SPEC.isLoaded()) {
            return false;
        }
        if (configKey.equalsIgnoreCase("enableDeeperOceans")) {
            return SubmarineConfig.ENABLE_DEEPER_OCEANS.get();
        }
        return false;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
