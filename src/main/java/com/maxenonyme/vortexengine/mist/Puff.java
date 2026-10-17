package com.maxenonyme.vortexengine.mist;

class Puff {

    double x, y, z;
    double lastX, lastY, lastZ;
    double motionX, motionY, motionZ;
    double ground, hover;
    int columnX, columnZ;
    float size;
    int frame;
    int age, life;
    float red = 1, green = 1, blue = 1;

    void place(double px, double py, double pz) {
        x = lastX = px;
        y = lastY = py;
        z = lastZ = pz;
    }

    float fade() {
        float in = Math.min(1, age / 40f);
        float out = Math.min(1, (life - age) / 60f);
        return Math.max(0, Math.min(in, out));
    }
}
