package com.deadzone.game;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

final class Bolt {
    private static final int MAX = 900;
    private static final float[] V = new float[MAX * 6 * 5];
    private static final Random R = new Random();

    private static final String VERT = """
            uniform mat4 uVP;
            attribute vec3 aPos;
            attribute vec2 aInfo;
            varying vec2 vI;
            void main() {
              vI = aInfo;
              gl_Position = uVP * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAG = """
            precision mediump float;
            varying vec2 vI;
            uniform float uI;
            void main() {
              float a = vI.x;
              float b = vI.y;
              float core = exp(-a * a * 14.0);
              float glow = exp(-a * a * 2.6);
              vec3 col;
              if (b < 0.0) {
                col = vec3(0.35, 0.5, 1.0) * glow * (-b);
              } else {
                col = vec3(0.45, 0.62, 1.0) * glow * 0.9 * b + vec3(1.0, 0.96, 1.0) * core * 1.25 * b;
              }
              gl_FragColor = vec4(col * uI, 1.0);
            }
            """;

    private static int prog, vbo, uVP, uI;
    private static int n;
    private static boolean active;
    private static double cx, cz, rx, rz;

    static double hx = 0, hz = -1;

    private Bolt() {
    }

    static void init() {
        prog = Sky.link(VERT, FRAG, new String[]{"aPos", "aInfo"});
        uVP = GLES20.glGetUniformLocation(prog, "uVP");
        uI = GLES20.glGetUniformLocation(prog, "uI");
        int[] b = new int[1];
        GLES20.glGenBuffers(1, b, 0);
        vbo = b[0];
        active = false;
        n = 0;
    }

    static void stop() {
        active = false;
    }

    static void strike(double camX, double camZ, double dirX, double dirZ) {
        double a = (R.nextDouble() - .5) * 1.6;
        double c = Math.cos(a), s = Math.sin(a);
        hx = dirX * c - dirZ * s;
        hz = dirX * s + dirZ * c;
        double dist = 52 + R.nextDouble() * 10;
        cx = camX + hx * dist;
        cz = camZ + hz * dist;
        rx = -hz;
        rz = hx;
        n = 0;
        double u0 = (R.nextDouble() - .5) * 14;
        path(u0, 56, u0 + (R.nextDouble() - .5) * 18, 0, 11, 5, 2.0, 1.0, 0);
        FloatBuffer fb = ByteBuffer.allocateDirect(n * 6 * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(V, 0, n * 6 * 5).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, n * 6 * 5 * 4, fb, GLES20.GL_DYNAMIC_DRAW);
        active = true;
    }

    private static void path(double u1, double v1, double u2, double v2, double disp, int depth, double w, double b,
                             int level) {
        if (n >= MAX - 2) return;
        if (depth == 0) {
            seg(u1, v1, u2, v2, w, b);
            if (level == 0) seg(u1, v1, u2, v2, 11, -.1);
            return;
        }
        double mu = (u1 + u2) / 2 + (R.nextDouble() - .5) * disp;
        double mv = (v1 + v2) / 2 + (R.nextDouble() - .5) * disp * .4;
        path(u1, v1, mu, mv, disp * .55, depth - 1, w, b, level);
        double chance = level == 0 ? .34 : level == 1 ? .18 : 0;
        if (depth <= 3 && R.nextDouble() < chance) {
            double side = R.nextBoolean() ? -1 : 1;
            double ang = .3 + R.nextDouble() * .8;
            double len = (level == 0 ? 9 + R.nextDouble() * 14 : 4 + R.nextDouble() * 6);
            double du = side * Math.sin(ang) * len;
            double dv = -Math.cos(ang) * len;
            path(mu, mv, mu + du, Math.max(0, mv + dv), len * .3, 3, w * .45, b * .75, level + 1);
        }
        path(mu, mv, u2, v2, disp * .55, depth - 1, w, b, level);
    }

    private static void put(int i, double u, double v, double side, double b) {
        int k = i * 5;
        V[k] = (float) (cx + rx * u);
        V[k + 1] = (float) v;
        V[k + 2] = (float) (cz + rz * u);
        V[k + 3] = (float) side;
        V[k + 4] = (float) b;
    }

    private static void seg(double u1, double v1, double u2, double v2, double w, double b) {
        double du = u2 - u1, dv = v2 - v1;
        double len = Math.hypot(du, dv);
        if (len < .01) return;
        double pu = -dv / len * w / 2, pv = du / len * w / 2;
        int i = n * 6;
        put(i, u1 - pu, v1 - pv, -1, b);
        put(i + 1, u1 + pu, v1 + pv, 1, b);
        put(i + 2, u2 + pu, v2 + pv, 1, b);
        put(i + 3, u1 - pu, v1 - pv, -1, b);
        put(i + 4, u2 + pu, v2 + pv, 1, b);
        put(i + 5, u2 - pu, v2 - pv, -1, b);
        n++;
    }

    static void draw(float[] vp, float intensity) {
        if (!active || intensity < .01f) return;
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES20.glUniform1f(uI, intensity);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 20, 0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 20, 12);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, n * 6);
        Gfx.use();
    }
}
