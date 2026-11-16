package com.deadzone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class Upscaler {
    private static final String VERT = """
            attribute vec2 aP;
            varying vec2 vUv;
            void main() {
              vUv = aP * 0.5 + 0.5;
              gl_Position = vec4(aP, 0.0, 1.0);
            }
            """;

    private static final String RESOLVE = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUv;
            uniform sampler2D uCur;
            uniform sampler2D uHist;
            uniform vec2 uScale;
            uniform vec2 uTexel;
            uniform vec2 uJit;
            uniform mat4 uInvVP;
            uniform mat4 uPrevVP;
            uniform vec3 uCam;
            uniform float uAlpha;
            uniform float uFirst;
            void main() {
              vec2 ndc = vUv * 2.0 - 1.0;
              vec2 lo = uTexel * 0.5;
              vec2 hi = uScale - uTexel * 0.5;
              vec2 uvc = clamp(((ndc + uJit) * 0.5 + 0.5) * uScale, lo, hi);
              vec3 c = texture2D(uCur, uvc).rgb;
              vec3 n1 = texture2D(uCur, clamp(uvc + vec2(uTexel.x, 0.0), lo, hi)).rgb;
              vec3 n2 = texture2D(uCur, clamp(uvc - vec2(uTexel.x, 0.0), lo, hi)).rgb;
              vec3 n3 = texture2D(uCur, clamp(uvc + vec2(0.0, uTexel.y), lo, hi)).rgb;
              vec3 n4 = texture2D(uCur, clamp(uvc - vec2(0.0, uTexel.y), lo, hi)).rgb;
              vec3 mn = min(c, min(min(n1, n2), min(n3, n4)));
              vec3 mx = max(c, max(max(n1, n2), max(n3, n4)));
              vec4 f = uInvVP * vec4(ndc, 1.0, 1.0);
              vec3 d = normalize(f.xyz / f.w - uCam);
              vec3 p;
              if (d.y < -0.02) p = uCam + d * min(-uCam.y / d.y, 150.0);
              else p = uCam + d * 200.0;
              vec4 pc = uPrevVP * vec4(p, 1.0);
              vec2 pn = pc.xy / pc.w;
              vec2 puv = pn * 0.5 + 0.5;
              float valid = step(0.0, puv.x) * step(puv.x, 1.0) * step(0.0, puv.y) * step(puv.y, 1.0) * step(0.01, pc.w);
              vec3 h = clamp(texture2D(uHist, puv).rgb, mn, mx);
              float vel = length(ndc - pn) * 0.5;
              float a = clamp(uAlpha + vel * 40.0, uAlpha, 1.0);
              a = mix(1.0, a, valid * (1.0 - uFirst));
              gl_FragColor = vec4(mix(h, c, a), 1.0);
            }
            """;

    private static final String PRESENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform sampler2D uNoise;
            uniform vec2 uTexel;
            uniform float uSharp;
            uniform float uMb;
            uniform mat4 uInvVP;
            uniform mat4 uPrevVP;
            uniform vec3 uCam;
            void main() {
              vec3 c = texture2D(uTex, vUv).rgb;
              vec3 n = (texture2D(uTex, vUv + vec2(uTexel.x, 0.0)).rgb + texture2D(uTex, vUv - vec2(uTexel.x, 0.0)).rgb
                      + texture2D(uTex, vUv + vec2(0.0, uTexel.y)).rgb + texture2D(uTex, vUv - vec2(0.0, uTexel.y)).rgb) * 0.25;
              c = clamp(c + (c - n) * uSharp, 0.0, 1.0);
              if (uMb > 0.0) {
                vec2 ndc = vUv * 2.0 - 1.0;
                vec4 f = uInvVP * vec4(ndc, 1.0, 1.0);
                vec3 d = normalize(f.xyz / f.w - uCam);
                vec3 p;
                if (d.y < -0.02) p = uCam + d * min(-uCam.y / d.y, 150.0);
                else p = uCam + d * 200.0;
                vec4 pc = uPrevVP * vec4(p, 1.0);
                vec2 vel = vec2(0.0);
                if (pc.w > 0.01) vel = (ndc - pc.xy / pc.w) * 0.5;
                vel *= uMb * 0.8;
                float vl = length(vel);
                if (vl > 0.06) vel *= 0.06 / vl;
                if (vl >= 0.0006) {
                  float dither = texture2D(uNoise, gl_FragCoord.xy * 0.0078125).r;
                  vec3 sum = c;
                  for (int i = 0; i < 6; i++) {
                    float t = (float(i) + dither) / 6.0 - 0.5;
                    sum += texture2D(uTex, clamp(vUv + vel * t, 0.002, 0.998)).rgb;
                  }
                  c = sum / 7.0;
                }
              }
              gl_FragColor = vec4(c, 1.0);
            }
            """;

    static boolean ok;
    static float scale = .85f, targetFps = 60f, thermalCap = 1f;

    private static int progR, progP, vbo, w, h, cur, frame;
    private static final int[] FBO = new int[3], TEX = new int[3];
    private static int dep;
    private static boolean first = true;
    private static float jx, jy;
    private static double acc, stable, holdoff;
    private static int cnt;
    private static final float[] INV = new float[16];

    private static int rCur, rHist, rScale, rTexel, rJit, rInv, rPrev, rCam, rAlpha, rFirst;
    private static int pTex, pNoise, pTexel, pSharp, pMb, pInv, pPrev, pCam;

    private Upscaler() {
    }

    private static int loc(int p, String n) {
        return GLES20.glGetUniformLocation(p, n);
    }

    static void init() {
        progR = Sky.link(VERT, RESOLVE, new String[]{"aP"});
        progP = Sky.link(VERT, PRESENT, new String[]{"aP"});
        rCur = loc(progR, "uCur");
        rHist = loc(progR, "uHist");
        rScale = loc(progR, "uScale");
        rTexel = loc(progR, "uTexel");
        rJit = loc(progR, "uJit");
        rInv = loc(progR, "uInvVP");
        rPrev = loc(progR, "uPrevVP");
        rCam = loc(progR, "uCam");
        rAlpha = loc(progR, "uAlpha");
        rFirst = loc(progR, "uFirst");
        pTex = loc(progP, "uTex");
        pNoise = loc(progP, "uNoise");
        pTexel = loc(progP, "uTexel");
        pSharp = loc(progP, "uSharp");
        pMb = loc(progP, "uMb");
        pInv = loc(progP, "uInvVP");
        pPrev = loc(progP, "uPrevVP");
        pCam = loc(progP, "uCam");
        int[] b = new int[1];
        GLES20.glGenBuffers(1, b, 0);
        vbo = b[0];
        FloatBuffer fb = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(new float[]{-1, -1, 3, -1, -1, 3}).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 24, fb, GLES20.GL_STATIC_DRAW);
        FBO[0] = FBO[1] = FBO[2] = 0;
        ok = false;
    }

    static void resize(int width, int height) {
        w = Math.max(32, width);
        h = Math.max(32, height);
        ok = true;
        int[] b = new int[1];
        if (FBO[0] == 0) {
            for (int i = 0; i < 3; i++) {
                GLES20.glGenFramebuffers(1, b, 0);
                FBO[i] = b[0];
                GLES20.glGenTextures(1, b, 0);
                TEX[i] = b[0];
            }
            GLES20.glGenRenderbuffers(1, b, 0);
            dep = b[0];
        }
        for (int i = 0; i < 3; i++) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TEX[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                    TEX[i], 0);
            if (i == 0) {
                GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, dep);
                GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h);
                GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                        GLES20.GL_RENDERBUFFER, dep);
            }
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) ok = false;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        first = true;
    }

    private static float halton(int i, int base) {
        float f = 1, r = 0;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    static float[] jitter(int sw, int sh) {
        int i = (frame % 8) + 1;
        jx = halton(i, 2) - .5f;
        jy = halton(i, 3) - .5f;
        return new float[]{2f * jx / sw, 2f * jy / sh};
    }

    static void tick(double dt, boolean dynamic) {
        if (!dynamic) {
            scale = 1f;
            return;
        }
        float cap = Math.max(.5f, Math.min(1f, thermalCap));
        if (dt > .1) return;
        acc += dt;
        cnt++;
        holdoff -= dt;
        if (acc < .6) return;
        double ft = acc / cnt;
        acc = 0;
        cnt = 0;
        double target = 1.0 / Math.max(30f, targetFps);
        if (ft > target * 1.1) {
            scale -= .05f;
            holdoff = 4;
            stable = 0;
        } else {
            stable += .6;
            if (stable > 3 && holdoff <= 0 && scale < cap && ft < target * 1.04) {
                scale += .025f;
                stable = 0;
            }
        }
        if (scale > cap) scale = cap;
        scale = Math.round(Math.max(.5f, Math.min(1f, scale)) * 40f) / 40f;
    }

    static void beginScene(int sw, int sh) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[0]);
        GLES20.glViewport(0, 0, sw, sh);
    }

    private static void quad() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
    }

    static void finish(float[] vpU, float[] prevVp, float[] cam, int sw, int sh, boolean blur) {
        int wr = 1 + cur, rd = 2 - cur;
        Matrix.invertM(INV, 0, vpU, 0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(false);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[wr]);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(progR);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TEX[0]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TEX[rd]);
        GLES20.glUniform1i(rCur, 0);
        GLES20.glUniform1i(rHist, 1);
        GLES20.glUniform2f(rScale, (float) sw / w, (float) sh / h);
        GLES20.glUniform2f(rTexel, 1f / w, 1f / h);
        float[] jt = {2f * jx / sw, 2f * jy / sh};
        GLES20.glUniform2f(rJit, jt[0], jt[1]);
        GLES20.glUniformMatrix4fv(rInv, 1, false, INV, 0);
        GLES20.glUniformMatrix4fv(rPrev, 1, false, prevVp, 0);
        GLES20.glUniform3f(rCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform1f(rAlpha, scale >= .99f ? .3f : .12f);
        GLES20.glUniform1f(rFirst, first ? 1f : 0f);
        quad();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(progP);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TEX[wr]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, Sky.noiseTexId());
        GLES20.glUniform1i(pTex, 0);
        GLES20.glUniform1i(pNoise, 1);
        GLES20.glUniform2f(pTexel, 1f / w, 1f / h);
        GLES20.glUniform1f(pSharp, .15f + (1f - scale) * .9f);
        GLES20.glUniform1f(pMb, blur ? Post.STRENGTH : 0f);
        GLES20.glUniformMatrix4fv(pInv, 1, false, INV, 0);
        GLES20.glUniformMatrix4fv(pPrev, 1, false, prevVp, 0);
        GLES20.glUniform3f(pCam, cam[0], cam[1], cam[2]);
        quad();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        cur = 1 - cur;
        first = false;
        frame++;
        Gfx.use();
    }
}
