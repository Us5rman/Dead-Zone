package com.deadzone.game;

import android.opengl.Matrix;

final class Xf {
    static final float[] ID = ident();
    private static final double DEG = 180.0 / Math.PI;

    private Xf() {
    }

    static float[] ident() {
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        return m;
    }

    /** translate, then rotate x, y, z in that order, then scale (same as a three.js XYZ euler) */
    static float[] make(double px, double py, double pz, double rx, double ry, double rz, double s) {
        float[] m = ident();
        Matrix.translateM(m, 0, (float) px, (float) py, (float) pz);
        if (rx != 0) Matrix.rotateM(m, 0, (float) (rx * DEG), 1, 0, 0);
        if (ry != 0) Matrix.rotateM(m, 0, (float) (ry * DEG), 0, 1, 0);
        if (rz != 0) Matrix.rotateM(m, 0, (float) (rz * DEG), 0, 0, 1);
        if (s != 1) Matrix.scaleM(m, 0, (float) s, (float) s, (float) s);
        return m;
    }

    /** camera style: yaw first, then pitch */
    static float[] makeYXZ(double px, double py, double pz, double rx, double ry) {
        float[] m = ident();
        Matrix.translateM(m, 0, (float) px, (float) py, (float) pz);
        Matrix.rotateM(m, 0, (float) (ry * DEG), 0, 1, 0);
        Matrix.rotateM(m, 0, (float) (rx * DEG), 1, 0, 0);
        return m;
    }

    /** camera with a roll tilt: yaw, pitch, then roll */
    static float[] makeYXZR(double px, double py, double pz, double rx, double ry, double rz) {
        float[] m = makeYXZ(px, py, pz, rx, ry);
        if (rz != 0) Matrix.rotateM(m, 0, (float) (rz * DEG), 0, 0, 1);
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        Matrix.multiplyMM(r, 0, a, 0, b, 0);
        return r;
    }

    static float[] point(float[] m, double x, double y, double z) {
        float[] in = {(float) x, (float) y, (float) z, 1f};
        float[] out = new float[4];
        Matrix.multiplyMV(out, 0, m, 0, in, 0);
        return out;
    }

    static float[] dir(float[] m, double x, double y, double z) {
        float[] in = {(float) x, (float) y, (float) z, 0f};
        float[] out = new float[4];
        Matrix.multiplyMV(out, 0, m, 0, in, 0);
        return out;
    }

    static void bake(FloatList dst, float[] src, float[] m, float ru, float rv) {
        float[] in = new float[4], out = new float[4];
        for (int i = 0; i < src.length; i += 8) {
            in[0] = src[i];
            in[1] = src[i + 1];
            in[2] = src[i + 2];
            in[3] = 1;
            Matrix.multiplyMV(out, 0, m, 0, in, 0);
            dst.add(out[0]);
            dst.add(out[1]);
            dst.add(out[2]);
            in[0] = src[i + 3];
            in[1] = src[i + 4];
            in[2] = src[i + 5];
            in[3] = 0;
            Matrix.multiplyMV(out, 0, m, 0, in, 0);
            float l = (float) Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
            if (l < 1e-6f) l = 1;
            dst.add(out[0] / l);
            dst.add(out[1] / l);
            dst.add(out[2] / l);
            dst.add(src[i + 6] * ru);
            dst.add(src[i + 7] * rv);
        }
    }
}
