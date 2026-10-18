package com.maxenonyme.createsubmarine.submarine.block.propeller.submarine_propeller;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.List;

public class SubmarinePropellerClusterModel extends BakedModelWrapper<BakedModel> {
    public SubmarinePropellerClusterModel(BakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (Boolean.TRUE.equals(data.get(SubmarinePropellerBlockEntity.HIDDEN_BY_CLUSTER)))
            return List.of();
        return super.getQuads(state, side, rand, data, renderType);
    }
}
