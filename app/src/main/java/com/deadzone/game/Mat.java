package com.deadzone.game;

final class Mat {
    Tex tex;
    float r = 1, g = 1, b = 1, a = 1;
    float er, eg, eb;
    boolean unlit, blend, additive, noFog, offset, puddle;
    boolean depthWrite = true;
    float repU = 1, repV = 1;

    static float[] rgb(int hex) {
        return new float[]{((hex >> 16) & 255) / 255f, ((hex >> 8) & 255) / 255f, (hex & 255) / 255f};
    }

    static Mat textured(Tex t, double rep) {
        Mat m = new Mat();
        m.tex = t;
        m.repU = m.repV = (float) rep;
        return m;
    }

    static Mat color(int hex) {
        Mat m = new Mat();
        float[] c = rgb(hex);
        m.r = c[0];
        m.g = c[1];
        m.b = c[2];
        return m;
    }

    static Mat basic(int hex) {
        Mat m = color(hex);
        m.unlit = true;
        return m;
    }

    Mat emissive(int hex, double k) {
        float[] c = rgb(hex);
        er = (float) (c[0] * k);
        eg = (float) (c[1] * k);
        eb = (float) (c[2] * k);
        return this;
    }

    Mat alpha(double v) {
        a = (float) v;
        blend = true;
        depthWrite = false;
        return this;
    }
}
