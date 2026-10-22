package com.maxenonyme.theabyss.client;

import com.maxenonyme.theabyss.CreateAbyss;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public record MagmaFlowSpriteSource(ResourceLocation strip, String prefix, int tiles, int frameTime) implements SpriteSource {
    public static final MapCodec<MagmaFlowSpriteSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.fieldOf("strip").forGetter(MagmaFlowSpriteSource::strip),
            Codec.STRING.fieldOf("prefix").forGetter(MagmaFlowSpriteSource::prefix),
            Codec.intRange(1, 64).fieldOf("tiles").forGetter(MagmaFlowSpriteSource::tiles),
            Codec.intRange(1, 1200).optionalFieldOf("frametime", 20).forGetter(MagmaFlowSpriteSource::frameTime)
    ).apply(i, MagmaFlowSpriteSource::new));

    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    public static void register(RegisterSpriteSourceTypesEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath(CreateAbyss.MOD_ID, "magma_flow"), TYPE);
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }

    @Override
    public void run(ResourceManager manager, Output output) {
        ResourceLocation file = TEXTURE_ID_CONVERTER.idToFile(strip);
        Optional<Resource> resource = manager.getResource(file);
        if (resource.isEmpty()) {
            CreateAbyss.LOGGER.warn("Magma flow strip {} is missing", file);
            return;
        }
        for (int tile = 0; tile < tiles; tile++) {
            int index = tile;
            ResourceLocation id = ResourceLocation.parse(prefix + index);
            output.add(id, loader -> cut(id, resource.get(), index));
        }
    }

    private SpriteContents cut(ResourceLocation id, Resource resource, int tile) {
        NativeImage source;
        try (InputStream in = resource.open()) {
            source = NativeImage.read(in);
        } catch (IOException e) {
            CreateAbyss.LOGGER.error("Could not read magma flow strip {}", strip, e);
            return null;
        }
        try (source) {
            int size = source.getHeight();
            int width = source.getWidth();
            int frames = width;
            NativeImage out = new NativeImage(size, size * frames, false);
            for (int frame = 0; frame < frames; frame++) {
                int start = tile * width / tiles - frame;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        out.setPixelRGBA(x, frame * size + y, source.getPixelRGBA(Math.floorMod(start + x, width), y));
                    }
                }
            }
            AnimationMetadataSection animation = new AnimationMetadataSection(List.of(), size, size, frameTime, false);
            return new SpriteContents(id, new FrameSize(size, size), out, new ResourceMetadata() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> serializer) {
                    return serializer == AnimationMetadataSection.SERIALIZER ? Optional.of((T) animation) : Optional.empty();
                }
            });
        }
    }
}
