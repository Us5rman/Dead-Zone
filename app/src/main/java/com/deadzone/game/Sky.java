package com.deadzone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

final class Sky {
    static final float[] MOON = norm(-30f, 32f, -50f);

    private static final int RAIN = 1400;
    private static final int NS = 128;
    private static final float[] INV = new float[16];

    private static int skyProg, rainProg, noiseTex, skyVbo, rainVbo;
    private static int sInv, sCam, sMoon, sHor, sTime, sFlash, sCover, sNoise;
    private static int rVP, rCam, rTime, rFlash, rFogCol, rFogDen, rMoonCol;

    private static final String SKY_VERT = """
            attribute vec2 aP;
            varying vec2 vN;
            void main() {
              vN = aP;
              gl_Position = vec4(aP, 1.0, 1.0);
            }
            """;

    private static final String SKY_FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vN;
            uniform mat4 uInvVP;
            uniform vec3 uCam;
            uniform vec3 uMoon;
            uniform vec3 uHor;
            uniform float uTime;
            uniform float uFlash;
            uniform float uCover;
            uniform sampler2D uNoise;

            const float MR = 0.062;
            const float MC = 0.998078;

            float vn(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return texture2D(uNoise, (i + f + 0.5) * 0.0078125).r;
            }

            float fbm4(vec2 p) {
              float v = 0.0;
              float a = 0.5;
              for (int i = 0; i < 4; i++) {
                v += a * vn(p);
                p = mat2(1.6, 1.2, -1.2, 1.6) * p + 7.3;
                a *= 0.5;
              }
              return v * 1.0667;
            }

            float fbm2(vec2 p) {
              float v = 0.5 * vn(p);
              p = mat2(1.6, 1.2, -1.2, 1.6) * p + 7.3;
              v += 0.25 * vn(p);
              return v * 1.3333;
            }

            void main() {
              vec4 f = uInvVP * vec4(vN, 1.0, 1.0);
              vec3 d = normalize(f.xyz / f.w - uCam);
              float h = clamp(d.y, 0.0, 1.0);
              vec3 zen = vec3(0.008, 0.016, 0.040);
              vec3 col = mix(uHor, zen, pow(h, 0.45));
              float md = dot(d, uMoon);
              float mp = max(md, 0.0);
              col += vec3(0.10, 0.14, 0.24) * (pow(mp, 6.0) * 0.35 + pow(mp, 48.0) * 0.6 + pow(mp, 600.0) * 1.2);
              col += vec3(0.05, 0.07, 0.12) * uFlash * (0.4 + h);

              vec2 g = d.xz / (max(d.y, 0.0) + 0.35) * 70.0;
              vec2 cell = floor(g);
              vec4 n = texture2D(uNoise, (cell + 0.5) * 0.0078125);
              float st = smoothstep(0.09, 0.0, length(fract(g) - 0.5 - (n.gb - 0.5) * 0.6)) * step(0.965, n.r);
              st *= 0.6 + 0.4 * sin(uTime * 2.5 + n.b * 60.0);
              st *= smoothstep(0.02, 0.25, d.y);
              col += vec3(0.7, 0.8, 1.0) * st * 0.9;

              if (md > MC - 0.001) {
                vec3 mt = normalize(cross(vec3(0.0, 1.0, 0.0), uMoon));
                vec3 mb = cross(uMoon, mt);
                vec2 mu = vec2(dot(d, mt), dot(d, mb)) / MR;
                float mz = sqrt(max(1.0 - dot(mu, mu), 0.0));
                vec3 mn = vec3(mu, mz);
                float tex = vn(mu * 5.0 + 11.0) * 0.55 + vn(mu * 13.0 + 3.0) * 0.3 + vn(mu * 30.0) * 0.15;
                float maria = smoothstep(0.35, 0.6, vn(mu * 2.2 + 5.0));
                float alb = mix(0.55, 1.0, tex) * mix(1.0, 0.62, maria);
                float lit = smoothstep(-0.15, 0.35, dot(mn, normalize(vec3(0.75, 0.2, 0.6))));
                vec3 mcol = vec3(0.86, 0.90, 1.0) * alb * lit * (0.55 + 0.45 * mz) * 1.25;
                float disc = smoothstep(MC - 0.00025, MC + 0.00025, md);
                col = mix(col, mcol, disc);
              }

              if (d.y > 0.015) {
                float t = (95.0 - uCam.y) / d.y;
                vec2 p = (uCam.xz + d.xz * t) * 0.011 + vec2(uTime * 0.006, uTime * 0.0035);
                float c = fbm4(p);
                float cd = smoothstep(uCover, uCover + 0.24, c);
                if (cd > 0.002) {
                  float cl = smoothstep(uCover, uCover + 0.24, fbm2(p + uMoon.xz * 0.05));
                  float shade = exp(-cl * 1.8);
                  float rim = pow(mp, 10.0);
                  vec3 cc = vec3(0.05, 0.065, 0.095) * (0.5 + 0.5 * shade);
                  cc += vec3(0.30, 0.38, 0.55) * shade * (0.18 + 0.9 * rim) * (0.4 + 0.6 * (1.0 - cd));
                  cc += vec3(0.5, 0.6, 0.95) * uFlash * (0.5 + 0.5 * shade);
                  cd *= smoothstep(0.015, 0.22, d.y);
                  col = mix(col, cc, cd);
                }
              }
              gl_FragColor = vec4(col, 1.0);
            }
            """;

    private static final String RAIN_VERT = """
            uniform mat4 uVP;
            uniform vec3 uCam;
            uniform float uTime;
            attribute vec3 aSeed;
            attribute vec2 aCorner;
            varying vec2 vC;
            varying float vDist;
            varying float vA;
            void main() {
              float r = fract(aSeed.x * 37.17 + aSeed.z * 91.31 + aSeed.y * 13.7);
              vec3 vel = vec3(-1.6, -(11.0 + r * 5.0), -0.8);
              vec3 axis = normalize(vel);
              vec3 B = vec3(34.0, 20.0, 34.0);
              vec3 q = mod(aSeed * B + vel * uTime - uCam, B);
              vec3 head = vec3(uCam.x + q.x - B.x * 0.5, uCam.y + q.y - 4.0, uCam.z + q.z - B.z * 0.5);
              vec3 toCam = uCam - head;
              float dist = length(toCam);
              vec3 side = cross(axis, toCam);
              float sl = length(side);
              side = sl > 0.0001 ? side / sl : vec3(1.0, 0.0, 0.0);
              float w = max(0.008, dist * 0.0055);
              float len = 0.55 + r * 0.5;
              vec3 pos = mix(head - axis * len, head, aCorner.y) + side * aCorner.x * w;
              vC = aCorner;
              vDist = dist;
              vA = 0.35 + 0.65 * clamp(0.015 / w, 0.0, 1.0);
              if (pos.y < 0.0) {
                gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
              } else {
                gl_Position = uVP * vec4(pos, 1.0);
              }
            }
            """;

    private static final String RAIN_FRAG = """
            precision mediump float;
            varying vec2 vC;
            varying float vDist;
            varying float vA;
            uniform vec3 uFogCol;
            uniform float uFogDen;
            uniform vec3 uMoonCol;
            uniform float uFlash;
            void main() {
              float across = 1.0 - abs(vC.x);
              float a = smoothstep(0.0, 0.7, across) * vC.y * vC.y;
              a *= smoothstep(0.5, 2.0, vDist) * vA * 0.7;
              vec3 col = uMoonCol + vec3(0.35, 0.42, 0.6) * uFlash;
              float f = 1.0 - exp(-uFogDen * uFogDen * vDist * vDist);
              col = mix(col, uFogCol, f * 0.6);
              a *= 1.0 - f * 0.85;
              gl_FragColor = vec4(col, a);
            }
            """;

    private Sky() {
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("sky shader: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static int link(String vs, String fs, String[] attribs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        for (int i = 0; i < attribs.length; i++) GLES20.glBindAttribLocation(p, i, attribs[i]);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("sky link: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static int buffer(float[] data) {
        int[] b = new int[1];
        GLES20.glGenBuffers(1, b, 0);
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, b[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
        return b[0];
    }

    static void init() {
        skyProg = link(SKY_VERT, SKY_FRAG, new String[]{"aP"});
        rainProg = link(RAIN_VERT, RAIN_FRAG, new String[]{"aSeed", "aCorner"});

        sInv = GLES20.glGetUniformLocation(skyProg, "uInvVP");
        sCam = GLES20.glGetUniformLocation(skyProg, "uCam");
        sMoon = GLES20.glGetUniformLocation(skyProg, "uMoon");
        sHor = GLES20.glGetUniformLocation(skyProg, "uHor");
        sTime = GLES20.glGetUniformLocation(skyProg, "uTime");
        sFlash = GLES20.glGetUniformLocation(skyProg, "uFlash");
        sCover = GLES20.glGetUniformLocation(skyProg, "uCover");
        sNoise = GLES20.glGetUniformLocation(skyProg, "uNoise");

        rVP = GLES20.glGetUniformLocation(rainProg, "uVP");
        rCam = GLES20.glGetUniformLocation(rainProg, "uCam");
        rTime = GLES20.glGetUniformLocation(rainProg, "uTime");
        rFlash = GLES20.glGetUniformLocation(rainProg, "uFlash");
        rFogCol = GLES20.glGetUniformLocation(rainProg, "uFogCol");
        rFogDen = GLES20.glGetUniformLocation(rainProg, "uFogDen");
        rMoonCol = GLES20.glGetUniformLocation(rainProg, "uMoonCol");

        byte[] px = new byte[NS * NS * 4];
        new Random(12345).nextBytes(px);
        ByteBuffer bb = ByteBuffer.allocateDirect(px.length).order(ByteOrder.nativeOrder());
        bb.put(px).position(0);
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        noiseTex = t[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, NS, NS, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, bb);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        skyVbo = buffer(new float[]{-1, -1, 3, -1, -1, 3});

        Random r = new Random(7);
        float[] corners = {-1, 0, 1, 0, 1, 1, -1, 0, 1, 1, -1, 1};
        float[] rain = new float[RAIN * 6 * 5];
        int k = 0;
        for (int i = 0; i < RAIN; i++) {
            float x = r.nextFloat(), y = r.nextFloat(), z = r.nextFloat();
            for (int v = 0; v < 6; v++) {
                rain[k++] = x;
                rain[k++] = y;
                rain[k++] = z;
                rain[k++] = corners[v * 2];
                rain[k++] = corners[v * 2 + 1];
            }
        }
        rainVbo = buffer(rain);
    }

    static void drawSky(float[] vp, float[] cam) {
        Matrix.invertM(INV, 0, vp, 0);
        GLES20.glUseProgram(skyProg);
        GLES20.glUniformMatrix4fv(sInv, 1, false, INV, 0);
        GLES20.glUniform3f(sCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(sMoon, MOON[0], MOON[1], MOON[2]);
        GLES20.glUniform3f(sHor, Gfx.FOG[0], Gfx.FOG[1], Gfx.FOG[2]);
        GLES20.glUniform1f(sTime, Gfx.time);
        GLES20.glUniform1f(sFlash, Gfx.flash);
        GLES20.glUniform1f(sCover, .42f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex);
        GLES20.glUniform1i(sNoise, 1);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(false);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        Gfx.use();
    }

    static void drawRain(float[] vp, float[] cam) {
        GLES20.glUseProgram(rainProg);
        GLES20.glUniformMatrix4fv(rVP, 1, false, vp, 0);
        GLES20.glUniform3f(rCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform1f(rTime, Gfx.time);
        GLES20.glUniform1f(rFlash, Gfx.flash);
        GLES20.glUniform3f(rFogCol, Gfx.FOG[0] + Gfx.flash * .1f, Gfx.FOG[1] + Gfx.flash * .13f, Gfx.FOG[2] + Gfx.flash * .2f);
        GLES20.glUniform1f(rFogDen, Gfx.FOG_DEN);
        GLES20.glUniform3f(rMoonCol, .45f, .54f, .72f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, rainVbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 20, 0);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 20, 12);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, RAIN * 6);
        Gfx.use();
    }
}
