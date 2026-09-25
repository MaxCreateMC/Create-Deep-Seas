package com.maxenonyme.highseas.client;

import net.minecraft.resources.ResourceLocation;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;
import com.maxenonyme.highseas.block.RudderBlock;
import java.util.Collections;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

public class RudderCopycatModel extends CopycatModel {

    private static final ResourceLocation COPYCAT_BASE = ResourceLocation.fromNamespaceAndPath("create", "copycat_base");
    private static final AABB RUDDER_BB = new AABB(0.0, 6.0 / 16.0, 0.0, 1.0, 10.0 / 16.0, 1.0);

    public RudderCopycatModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        List<BakedQuad> superQuads = super.getQuads(state, side, rand, data, renderType);
        
        BlockState material = getMaterial(data);
        boolean bare = material != null && COPYCAT_BASE.equals(BuiltInRegistries.BLOCK.getKey(material.getBlock()));
        if (material != null && !bare) {
            
            boolean materialSupportsRenderType = true;
            if (renderType != null) {
                materialSupportsRenderType = Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getRenderTypes(material, rand, ModelData.EMPTY).contains(renderType);
            }
            
            if (!materialSupportsRenderType) {
                return Collections.emptyList();
            }
        } else if (bare) {
            if (renderType != null && renderType != RenderType.cutoutMipped()) {
                return Collections.emptyList();
            }
        }
        
        return superQuads;
    }
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource rand, BlockState material,
                                              ModelData wrappedData, RenderType renderType) {

        Direction.Axis axis = state.getOptionalValue(RudderBlock.AXIS).orElse(Direction.Axis.Y);
        Direction facing = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());

        BakedModel model = getModelOf(material);
        List<BakedQuad> templateQuads = model.getQuads(material, side, rand, wrappedData, renderType);
        
        List<BakedQuad> quads = new ArrayList<>();

        for (boolean front : Iterate.trueAndFalse) {
            double minX = 0, minY = 0, minZ = 0;
            double maxX = 1, maxY = 1, maxZ = 1;

            if (axis == Direction.Axis.X) {
                if (front) maxX = 2.0 / 16.0;
                else minX = 14.0 / 16.0;
            } else if (axis == Direction.Axis.Y) {
                if (front) maxY = 2.0 / 16.0;
                else minY = 14.0 / 16.0;
            } else {
                if (front) maxZ = 2.0 / 16.0;
                else minZ = 14.0 / 16.0;
            }

            AABB bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            double offsetValue = front ? (6.0 / 16.0) : (-6.0 / 16.0);
            Vec3 offset = normal.scale(offsetValue);

            for (BakedQuad quad : templateQuads) {
                Direction direction = quad.getDirection();
                
                if (front && direction == facing) continue;
                if (!front && direction == facing.getOpposite()) continue;

                quads.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                        BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), bb, offset)));
            }
        }

        return quads;
    }
}
