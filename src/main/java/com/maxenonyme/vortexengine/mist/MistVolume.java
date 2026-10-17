package com.maxenonyme.vortexengine.mist;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public class MistVolume {

    public final double x, y, z;
    public final float radius, density;
    public final float red, green, blue;
    final List<Puff> puffs = new ArrayList<>();

    MistVolume(Level level, RandomSource random, double x, double y, double z, float radius, float density,
            float red, float green, float blue, int count) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.density = density;
        this.red = red;
        this.green = green;
        this.blue = blue;

        for (int i = 0; i < count; i++) {
            Puff puff = new Puff();
            respawn(puff, level, random);
            puff.place(puff.x, Math.max(puff.y, y + random.nextDouble() * radius / 3), puff.z);
            puff.age = random.nextInt(puff.life / 2);
            puffs.add(puff);
        }
    }

    void tick(Level level, RandomSource random, List<Entity> movers) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double time = level.getGameTime();

        for (Puff puff : puffs) {
            puff.lastX = puff.x;
            puff.lastY = puff.y;
            puff.lastZ = puff.z;

            if (Mth.floor(puff.x) != puff.columnX || Mth.floor(puff.z) != puff.columnZ || puff.age % 20 == 0)
                settle(puff, level);

            puff.motionY = puff.motionY * 0.9 + (puff.ground + puff.hover - puff.y) * 0.004;
            puff.motionX = puff.motionX * 0.92 + (random.nextDouble() - 0.5) * 0.003
                    + Math.sin(puff.z * 0.15 + time * 0.011) * 0.0015;
            puff.motionZ = puff.motionZ * 0.92 + (random.nextDouble() - 0.5) * 0.003
                    + Math.cos(puff.x * 0.15 + time * 0.013) * 0.0015;

            for (Entity mover : movers)
                push(puff, mover);

            double dx = puff.x - x, dz = puff.z - z;
            double spread = dx * dx + dz * dz;
            if (spread > radius * radius * 0.7) {
                double length = Math.sqrt(spread);
                puff.motionX -= dx / length * 0.004;
                puff.motionZ -= dz / length * 0.004;
            }

            double nextX = puff.x + puff.motionX, nextZ = puff.z + puff.motionZ;
            if (level.getBlockState(cursor.set(nextX, puff.y, nextZ)).canOcclude()) {
                puff.motionX = 0;
                puff.motionZ = 0;
            } else {
                puff.x = nextX;
                puff.z = nextZ;
            }

            puff.y += puff.motionY;
            puff.age++;

            if (puff.age >= puff.life)
                respawn(puff, level, random);
        }
    }

    void light(Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Puff puff : puffs) {
            cursor.set(puff.x, puff.y, puff.z);
            float ambient = Math.max(0.2f, LightTexture.getBrightness(level.dimensionType(),
                    level.getMaxLocalRawBrightness(cursor)));

            puff.red = red * ambient;
            puff.green = green * ambient;
            puff.blue = blue * ambient;
        }
    }

    float edge(double px, double pz) {
        double dx = px - x, dz = pz - z;
        return (float) Math.max(0, 1 - (dx * dx + dz * dz) / (radius * radius));
    }

    private static void push(Puff puff, Entity mover) {
        double dx = puff.x - mover.getX(), dz = puff.z - mover.getZ();
        double dy = puff.y - mover.getY();
        double reach = mover.getBbWidth() / 2 + 1.5;
        double spread = dx * dx + dz * dz;
        if (spread >= reach * reach || dy < -1 || dy > mover.getBbHeight() + 1)
            return;

        double distance = Math.max(Math.sqrt(spread), 0.1);
        double strength = 1 - distance / reach;
        double speedX = mover.getX() - mover.xo, speedZ = mover.getZ() - mover.zo;
        double speed = Math.sqrt(speedX * speedX + speedZ * speedZ);

        puff.motionX += dx / distance * strength * (0.01 + speed * 0.15) + speedX * strength * 0.2;
        puff.motionZ += dz / distance * strength * (0.01 + speed * 0.15) + speedZ * strength * 0.2;
        puff.motionY += strength * speed * 0.05;
    }

    private void respawn(Puff puff, Level level, RandomSource random) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = Math.sqrt(random.nextDouble()) * radius * 0.8;
        double px = x + Math.cos(angle) * distance;
        double pz = z + Math.sin(angle) * distance;

        puff.place(px, y, pz);
        puff.hover = 0.2 + random.nextDouble() * random.nextDouble() * 1.3;
        settle(puff, level);
        puff.place(px, puff.ground + puff.hover, pz);

        puff.motionX = (random.nextDouble() - 0.5) * 0.01;
        puff.motionY = 0;
        puff.motionZ = (random.nextDouble() - 0.5) * 0.01;
        puff.size = 3 + random.nextFloat() * 2.5f;
        puff.frame = random.nextInt(6);
        puff.life = 400 + random.nextInt(400);
        puff.age = 0;
    }

    private static void settle(Puff puff, Level level) {
        puff.columnX = Mth.floor(puff.x);
        puff.columnZ = Mth.floor(puff.z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(puff.columnX, Mth.floor(puff.y + 1),
                puff.columnZ);

        for (int i = 0; i < 32; i++) {
            BlockState state = level.getBlockState(cursor);
            VoxelShape shape = state.getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                puff.ground = cursor.getY() + shape.max(Direction.Axis.Y);
                return;
            }

            cursor.move(Direction.DOWN);
        }

        puff.ground = puff.y - 32;
    }
}
