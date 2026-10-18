package com.maxenonyme.vortexengine.light;

record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    static Box around(int x, int y, int z, int radius, int size) {
        return new Box(Math.max(0, x - radius), Math.max(0, y - radius), Math.max(0, z - radius),
                Math.min(size, x + radius + 1), Math.min(size, y + radius + 1), Math.min(size, z + radius + 1));
    }

    static Box union(Box a, Box b) {
        if (a == null)
            return b;
        if (b == null)
            return a;

        return new Box(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ),
                Math.max(a.maxX, b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
    }

    Box moved(int dx, int dy, int dz, int size) {
        Box box = new Box(Math.max(0, minX + dx), Math.max(0, minY + dy), Math.max(0, minZ + dz),
                Math.min(size, maxX + dx), Math.min(size, maxY + dy), Math.min(size, maxZ + dz));
        return box.minX < box.maxX && box.minY < box.maxY && box.minZ < box.maxZ ? box : null;
    }
}
