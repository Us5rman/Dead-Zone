package com.deadzone.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.Random;

final class Tex {
    interface Painter {
        void paint(Canvas c, int w, int h);
    }

    static final Paint P = new Paint();
    static final Random R = new Random();

    static {
        P.setAntiAlias(false);
        P.setStyle(Paint.Style.FILL);
    }

    private final Bitmap bmp;
    private final boolean repeat;
    private int id;
    private int gen = -1;

    Tex(int w, int h, boolean repeat, Painter p) {
        Bitmap src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        p.paint(new Canvas(src), w, h);
        Matrix flip = new Matrix();
        flip.preScale(1, -1);
        bmp = Bitmap.createBitmap(src, 0, 0, w, h, flip, false);
        this.repeat = repeat;
    }

    int id() {
        if (gen != Gfx.gen) {
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            id = t[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            gen = Gfx.gen;
        }
        return id;
    }

    static int rgb(int r, int g, int b) {
        return Color.rgb(Math.max(0, Math.min(255, r)), Math.max(0, Math.min(255, g)), Math.max(0, Math.min(255, b)));
    }

    static void rect(Canvas c, String hex, double x, double y, double w, double h) {
        P.setColor(Color.parseColor(hex));
        c.drawRect((float) x, (float) y, (float) (x + w), (float) (y + h), P);
    }

    static void noise(Canvas c, int r, int g, int b, int amp) {
        P.setColor(rgb(r, g, b));
        c.drawRect(0, 0, 64, 64, P);
        for (int i = 0; i < 900; i++) {
            int v = (int) ((R.nextDouble() - .5) * amp);
            P.setColor(rgb(r + v, g + v, b + v));
            int x = R.nextInt(64), y = R.nextInt(64);
            c.drawRect(x, y, x + 2, y + 2, P);
        }
    }

    static void blot(Canvas c, String hex, int n) {
        P.setColor(Color.parseColor(hex));
        for (int i = 0; i < n; i++) {
            int x = (int) (R.nextDouble() * 60), y = (int) (R.nextDouble() * 60);
            int w = 2 + (int) (R.nextDouble() * 5), h = 2 + (int) (R.nextDouble() * 5);
            c.drawRect(x, y, x + w, y + h, P);
        }
    }

    static Tex tile(Painter p) {
        return new Tex(64, 64, true, p);
    }
}
