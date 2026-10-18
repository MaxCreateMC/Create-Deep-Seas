package com.maxenonyme.createsubmarine.submarine.ponder;

import com.maxenonyme.createsubmarine.submarine.client.renderer.AllPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.RopeStrandRenderer;
import dev.simulated_team.simulated.ponder.elements.rope.PonderRopePose;
import dev.simulated_team.simulated.ponder.elements.rope.RopeStrandElement;
import dev.simulated_team.simulated.util.SimMathUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.List;

public class SteelCablePonderElement extends RopeStrandElement {

    public SteelCablePonderElement(Vec3 from, Vec3 to, double length, double sog, float floorHeight) {
        super(from, to, length, sog, floorHeight);
    }

    public SteelCablePonderElement(Vec3 from, Vec3 to, double length, double sog) {
        super(from, to, length, sog);
    }

    @Override
    protected void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float fade, float pt) {
        final SuperByteBuffer middle = CachedBuffers.partialFacing(AllPartialModels.STEEL_CABLE,
                Blocks.AIR.defaultBlockState(), Direction.NORTH);
        final SuperByteBuffer knot = CachedBuffers.partialFacing(AllPartialModels.STEEL_CABLE_KNOT,
                Blocks.AIR.defaultBlockState(), Direction.NORTH);
        final VertexConsumer vb = buffer.getBuffer(RenderType.solid());
        final PoseStack ps = graphics.pose();

        final PonderRopePose currentPose = new PonderRopePose();
        currentPose.set(this.pose);
        currentPose.lerp(this.pose, pt);

        final List<Vector3d> points = new ObjectArrayList<>();
        final Vector3d currentPos = new Vector3d();
        final int knots = (int) Math.ceil(currentPose.length) + 1;
        final double extra = currentPose.length - knots;

        for (int i = 0; i < knots; i++) {
            final double t = (1.0d - (double) i / (knots + extra));
            final Vector3d pos = currentPose.start.lerp(currentPose.end, Math.max(0, t), currentPos);
            final double y = Math.pow(t - .5, 2) * 4;
            pos.sub(0, Mth.clamp(1.0 - y, 0, 1) * currentPose.sog, 0);
            pos.set(pos.x, Math.max(pos.y, currentPose.floorHeight), pos.z);
            points.add(new Vector3d(pos));
        }

        final ObjectArrayList<RopeStrandRenderer.RopeRenderPoint> renderPoints = buildRenderPoints(pt, points);

        ps.pushPose();
        this.applyFade(ps, pt);
        ps.translate(currentPose.start.x, currentPose.start.y, currentPose.start.z);

        for (int i = 1; i < renderPoints.size(); i++) {
            final RopeStrandRenderer.RopeRenderPoint renderPoint0 = renderPoints.get(i - 1);
            final RopeStrandRenderer.RopeRenderPoint renderPoint1 = renderPoints.get(i);
            final Vector3d globalRenderPos = new Vector3d(renderPoint0.position());
            final Vector3d renderPos = renderPoint0.position();
            final Quaternionf orientation = renderPoint0.orientation();
            final double length = renderPoint1.position().distance(renderPoint0.position());

            ps.pushPose();
            ps.translate(renderPos.x - currentPose.start.x, renderPos.y - currentPose.start.y, renderPos.z - currentPose.start.z);
            ps.mulPose(orientation);
            ps.translate(-0.5, -0.5, -0.5);

            final int worldLight = LightTexture.FULL_BLOCK;
            knot.light(worldLight).renderInto(ps, vb);

            ps.pushPose();
            ps.translate(0.0, 0.5, 0.0);
            ps.scale(1.0f, (float) length, 1.0f);
            middle.light(worldLight).renderInto(ps, vb);
            ps.popPose();

            if (renderPoint1 == renderPoints.getLast()) {
                ps.translate(0, length, 0);
                knot.light(worldLight).renderInto(ps, vb);
            }
            ps.popPose();
        }
        ps.popPose();
    }

    private static @NotNull ObjectArrayList<RopeStrandRenderer.RopeRenderPoint> buildRenderPoints(float partialTick, List<Vector3d> inputPoints) {
        final ObjectArrayList<RopeStrandRenderer.RopeRenderPoint> ropeRenderPoints = new ObjectArrayList<>();
        final ObjectArrayList<Vector3d> points = new ObjectArrayList<>(inputPoints);

        while (points.size() >= 2 && points.getFirst().distanceSquared(points.get(1)) < 1e-6) {
            points.removeFirst();
        }
        if (points.size() <= 1)
            return new ObjectArrayList<>();

        final Vector3dc pointZeroPosition = points.get(0);
        final Vector3dc pointOnePosition = points.get(1);
        final Vector3d normal = pointOnePosition.sub(pointZeroPosition, new Vector3d()).normalize();

        final Quaternionf runningRotation;
        if (normal.dot(OrientedBoundingBox3d.UP) < 0) {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), normal);
            runningRotation.rotateZ((float) Math.PI);
        } else {
            runningRotation = SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), normal);
        }

        ropeRenderPoints.add(new RopeStrandRenderer.RopeRenderPoint(new Quaternionf(runningRotation), new Vector3d(pointZeroPosition)));

        final Vector3d runningNormal = new Vector3d();
        for (int i = 2; i < points.size(); i++) {
            final Vector3d pointA = points.get(i - 1);
            final Vector3d pointB = points.get(i);
            runningNormal.set(pointB).sub(pointA).normalize();

            if (runningNormal.dot(OrientedBoundingBox3d.UP) < -0.15) {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, -1, 0), runningNormal));
                runningRotation.rotateZ((float) Math.PI);
            } else {
                runningRotation.set(SimMathUtils.getQuaternionfFromVectorRotation(new Vector3d(0, 1, 0), runningNormal));
            }
            ropeRenderPoints.add(new RopeStrandRenderer.RopeRenderPoint(new Quaternionf(runningRotation), pointA));
            normal.set(runningNormal);
        }

        ropeRenderPoints.add(new RopeStrandRenderer.RopeRenderPoint(new Quaternionf(runningRotation), points.getLast()));
        return ropeRenderPoints;
    }
}
