package com.deadzone.game;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;

final class Geo {
    private static final HashMap<String, Geo> CACHE = new HashMap<>();

    private final float[] data;
    private final int count;
    private int vbo;
    private int gen = -1;

    Geo(float[] data) {
        this.data = data;
        this.count = data.length / 8;
    }

    static Geo box(double w, double h, double d) {
        String k = "b" + w + "," + h + "," + d;
        Geo g = CACHE.get(k);
        if (g == null) {
            g = new Geo(Prim.box(w, h, d));
            CACHE.put(k, g);
        }
        return g;
    }

    static Geo cyl(double rt, double rb, double h, int seg, boolean open) {
        String k = "c" + rt + "," + rb + "," + h + "," + seg + open;
        Geo g = CACHE.get(k);
        if (g == null) {
            g = new Geo(Prim.cyl(rt, rb, h, seg, open));
            CACHE.put(k, g);
        }
        return g;
    }

    static Geo plane(double w, double h) {
        String k = "p" + w + "," + h;
        Geo g = CACHE.get(k);
        if (g == null) {
            g = new Geo(Prim.plane(w, h));
            CACHE.put(k, g);
        }
        return g;
    }

    void draw(int mode) {
        if (count == 0) return;
        if (gen != Gfx.gen) {
            int[] b = new int[1];
            GLES20.glGenBuffers(1, b, 0);
            vbo = b[0];
            FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(data).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
            gen = Gfx.gen;
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glVertexAttribPointer(Gfx.aPos, 3, GLES20.GL_FLOAT, false, 32, 0);
        GLES20.glVertexAttribPointer(Gfx.aNor, 3, GLES20.GL_FLOAT, false, 32, 12);
        GLES20.glVertexAttribPointer(Gfx.aUv, 2, GLES20.GL_FLOAT, false, 32, 24);
        GLES20.glDrawArrays(mode, 0, count);
    }
}
