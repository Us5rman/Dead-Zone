package com.deadzone.game;

import android.opengl.GLES20;

final class Gfx {
    static int gen = 0;
    static float time, flash, bh = .7f, rain, wet = .3f;
    static boolean reflOn, reflPass;
    static final float[] FOG = {.038f, .055f, .082f};
    static final float FOG_DEN = .055f;
    static final float[] boltDir = {0f, 0f, -1f};
    private static final long T0 = System.nanoTime();
    static int aPos, aNor, aUv;

    private static int prog;
    private static int uVP, uModel, uTex, uHasTex, uColor, uEmis, uUnlit, uFogOn, uPS, uWet, uPud, uRefPass, uRes, uTimeU, uNoise, uRefl, uRefPl, uThr, uGlass, uMirPl, uMirOn, uMir, uFlashU, uBoltDir, uBhDir, uBhSize;
    private static int uAmb, uDirV, uDirCol, uCam, uFogCol, uFogDen;
    private static int uNumPl, uPlPos, uPlCol, uPlRange;

    private static final String VERT =
            "uniform mat4 uVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform float uPS;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNor;\n" +
            "attribute vec2 aUv;\n" +
            "varying vec3 vWorld;\n" +
            "varying vec3 vNor;\n" +
            "varying vec2 vUv;\n" +
            "void main() {\n" +
            "  vec4 w = uModel * vec4(aPos, 1.0);\n" +
            "  vWorld = w.xyz;\n" +
            "  vNor = (uModel * vec4(aNor, 0.0)).xyz;\n" +
            "  vUv = aUv;\n" +
            "  gl_Position = uVP * w;\n" +
            "  gl_PointSize = uPS;\n" +
            "}\n";

    private static final String FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec3 vWorld;
            varying vec3 vNor;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform float uHasTex;
            uniform vec4 uColor;
            uniform vec3 uEmis;
            uniform float uUnlit;
            uniform float uFogOn;
            uniform vec3 uAmb;
            uniform vec3 uDirV;
            uniform vec3 uDirCol;
            uniform vec3 uCam;
            uniform vec3 uFogCol;
            uniform float uFogDen;
            uniform float uWet;
            uniform float uPud;
            uniform vec4 uRefPl;
            uniform float uThr;
            uniform float uGlass;
            uniform vec4 uMirPl;
            uniform float uMirOn;
            uniform sampler2D uMir;
            uniform float uFlash;
            uniform vec3 uBoltDir;
            uniform vec3 uBhDir;
            uniform float uBhSize;
            uniform float uRefPass;
            uniform float uTime;
            uniform vec2 uRes;
            uniform sampler2D uNoise;
            uniform sampler2D uRefl;
            uniform float uNumPl;
            uniform vec3 uPlPos[8];
            uniform vec3 uPlCol[8];
            uniform float uPlRange[8];
            float vn(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return texture2D(uNoise, (i + f + 0.5) * 0.0078125).r;
            }
            void main() {
              if (uRefPass > 0.5 && dot(vWorld, uRefPl.xyz) - uRefPl.w < uThr) discard;
              vec4 base = uColor;
              if (uHasTex > 0.5) base *= texture2D(uTex, vUv);
              vec3 col;
              float alpha = base.a;
              vec3 scat = vec3(0.0);
              vec3 rcol = vec3(0.0);
              float pmix = 0.0;
              if (uUnlit > 0.5) {
                col = base.rgb;
              } else {
                vec3 n = normalize(vNor);
                vec3 v = normalize(uCam - vWorld);
                float up = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);
                float pud = 0.0;
                if (uPud > 0.5) {
                  vec2 pp = vWorld.xz * 0.085;
                  pud = smoothstep(0.50, 0.58, vn(pp) * 0.65 + vn(pp * 2.7 + 5.0) * 0.35) * clamp(uWet * 1.5 - 0.3, 0.0, 1.0);
                }
                float gloss = max(uWet * (0.35 + 0.65 * up), pud);
                vec3 albedo = base.rgb * mix(1.0, 0.8, uWet * up) * mix(1.0, 0.55, pud);
                vec3 light = uAmb * mix(0.75, 1.15, up) + uDirCol * max(dot(n, uDirV), 0.0);
                vec3 spec = uDirCol * (2.0 * gloss * pow(max(dot(n, normalize(uDirV + v)), 0.0), 36.0));
                for (int i = 0; i < 8; i++) {
                  if (float(i) >= uNumPl) break;
                  vec3 d = uPlPos[i] - vWorld;
                  float dist = max(length(d), 0.001);
                  float at = clamp(1.0 - dist / uPlRange[i], 0.0, 1.0);
                  light += uPlCol[i] * max(dot(n, d / dist), 0.0) * at;
                  scat += uPlCol[i] * at * at;
                }
                spec += uDirCol * (4.0 * pud * pow(max(dot(n, normalize(uDirV + v)), 0.0), 240.0));
                col = albedo * light + uEmis + spec;
                if (pud > 0.001) {
                  vec2 gg = vWorld.xz * 2.6;
                  vec4 rr = texture2D(uNoise, (floor(gg) + 0.5) * 0.0078125);
                  vec2 ff = fract(gg) - 0.5 - (rr.gb - 0.5) * 0.5;
                  float ph = fract(uTime * 0.7 + rr.r * 9.0);
                  float dd = length(ff);
                  float ring = exp(-pow((dd - ph * 0.5) * 16.0, 2.0)) * (1.0 - ph) * step(0.4, rr.a);
                  vec2 suv = gl_FragCoord.xy / uRes + ff / max(dd, 0.001) * ring * 0.012;
                  float fr = 0.3 + 0.7 * pow(1.0 - clamp(dot(n, v), 0.0, 1.0), 2.0);
                  rcol = texture2D(uRefl, suv).rgb + ring * 0.1;
                  pmix = pud * clamp(0.3 + 0.7 * fr, 0.0, 1.0) * 0.9;
                }
              }
              vec3 tm = col * 1.25;
              tm = (tm * (2.51 * tm + 0.03)) / (tm * (2.43 * tm + 0.59) + 0.14);
              col = mix(col, tm, 0.7);
              col = mix(col, rcol, pmix);
              if (uGlass > 0.5) {
                float onp = step(abs(dot(vWorld, uMirPl.xyz) - uMirPl.w), 0.12) * uMirOn * (1.0 - step(0.5, uRefPass));
                vec3 nn = normalize(vNor);
                vec3 vv = normalize(uCam - vWorld);
                float fr = pow(1.0 - clamp(abs(dot(nn, vv)), 0.0, 1.0), 2.0);
                vec3 rc = texture2D(uMir, gl_FragCoord.xy / uRes).rgb;
                float amt = (uGlass > 1.5 ? 0.92 : 0.25 + 0.55 * fr) * onp;
                col = mix(col, rc, amt);
                alpha = max(alpha, amt * 0.9);
              }
              if (uFogOn > 0.5) {
                vec3 vd = vWorld - uCam;
                float dd = length(vd);
                float hm = max((uCam.y + vWorld.y) * 0.5, 0.0);
                vec2 wp = vWorld.xz;
                float fpatch = vn(wp * 0.045 + vec2(uTime * 0.025, uTime * 0.015)) * 0.65 + vn(wp * 0.14 - vec2(uTime * 0.05, -uTime * 0.03)) * 0.35;
                float den = uFogDen * (0.55 + 1.1 * fpatch) * exp(-hm * 0.16);
                float f = 1.0 - exp(-den * den * dd * dd);
                vec3 vdn = vd / max(dd, 0.001);
                float facing = max(dot(vdn, uBoltDir), 0.0);
                float bhs = pow(max(dot(vdn, uBhDir), 0.0), 3.0);
                vec3 fc = uFogCol + vec3(0.30, 0.36, 0.60) * uFlash * (0.25 + 0.75 * facing * facing) + scat * 0.35 + vec3(0.30, 0.16, 0.07) * bhs * 0.10 * uBhSize;
                col = mix(col * exp(-dd * 0.012), fc, clamp(f, 0.0, 1.0));
              }
              gl_FragColor = vec4(col, alpha);
            }
            """;

    private Gfx() {
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("shader: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static int loc(String n) {
        return GLES20.glGetUniformLocation(prog, n);
    }

    static void init() {
        gen++;
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, shader(GLES20.GL_VERTEX_SHADER, VERT));
        GLES20.glAttachShader(prog, shader(GLES20.GL_FRAGMENT_SHADER, FRAG));
        GLES20.glLinkProgram(prog);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("link: " + GLES20.glGetProgramInfoLog(prog));
        GLES20.glUseProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog, "aPos");
        aNor = GLES20.glGetAttribLocation(prog, "aNor");
        aUv = GLES20.glGetAttribLocation(prog, "aUv");
        uVP = loc("uVP");
        uModel = loc("uModel");
        uTex = loc("uTex");
        uHasTex = loc("uHasTex");
        uColor = loc("uColor");
        uEmis = loc("uEmis");
        uUnlit = loc("uUnlit");
        uFogOn = loc("uFogOn");
        uPS = loc("uPS");
        uWet = loc("uWet");
        uPud = loc("uPud");
        uRefPass = loc("uRefPass");
        uRes = loc("uRes");
        uTimeU = loc("uTime");
        uNoise = loc("uNoise");
        uRefl = loc("uRefl");
        uRefPl = loc("uRefPl");
        uThr = loc("uThr");
        uGlass = loc("uGlass");
        uMirPl = loc("uMirPl");
        uMirOn = loc("uMirOn");
        uMir = loc("uMir");
        uFlashU = loc("uFlash");
        uBoltDir = loc("uBoltDir");
        uBhDir = loc("uBhDir");
        uBhSize = loc("uBhSize");
        uAmb = loc("uAmb");
        uDirV = loc("uDirV");
        uDirCol = loc("uDirCol");
        uCam = loc("uCam");
        uFogCol = loc("uFogCol");
        uFogDen = loc("uFogDen");
        uNumPl = loc("uNumPl");
        uPlPos = loc("uPlPos");
        uPlCol = loc("uPlCol");
        uPlRange = loc("uPlRange");
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNor);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform1i(uNoise, 1);
        GLES20.glUniform1i(uRefl, 2);
        GLES20.glUniform1i(uMir, 3);
        GLES20.glUniform1f(uMirOn, 0f);
        GLES20.glUniform1f(uPud, 0f);
        GLES20.glUniform1f(uRefPass, 0f);
        GLES20.glUniform1f(uPS, 1f);
    }

    /** per frame setup: lights, fog, camera */
    static void frame(float[] vp, float[] cam, float[] amb, float[] plPos, float[] plCol,
                      float[] plRange, int n) {
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES20.glUniform3f(uCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(uAmb, amb[0], amb[1], amb[2]);
        time = (float) ((System.nanoTime() - T0) / 1e9);
        FOG[0] = .038f + .014f * bh;
        FOG[1] = .055f + .005f * bh;
        FOG[2] = .082f;
        float k = (.6f + .3f * bh) * (1f + .04f * (float) Math.sin(time * 1.3));
        GLES20.glUniform3f(uDirV, Sky.BH[0], Sky.BH[1], Sky.BH[2]);
        GLES20.glUniform3f(uDirCol, .34f * k + flash * .5f, .22f * k + flash * .6f, .12f * k + flash * .9f);
        GLES20.glUniform1f(uTimeU, time);
        GLES20.glUniform3f(uFogCol, FOG[0], FOG[1], FOG[2]);
        GLES20.glUniform1f(uFlashU, flash);
        GLES20.glUniform3f(uBoltDir, boltDir[0], boltDir[1], boltDir[2]);
        GLES20.glUniform1f(uFogDen, FOG_DEN * (.8f + .4f * rain));
        GLES20.glUniform1f(uWet, .12f + .8f * wet);
        GLES20.glUniform3f(uBhDir, Sky.BH[0], Sky.BH[1], Sky.BH[2]);
        GLES20.glUniform1f(uBhSize, bh);
        GLES20.glUniform1f(uNumPl, n);
        GLES20.glUniform3fv(uPlPos, 8, plPos, 0);
        GLES20.glUniform3fv(uPlCol, 8, plCol, 0);
        GLES20.glUniform1fv(uPlRange, 8, plRange, 0);
        GLES20.glUniform1f(uPS, 1.6f);
    }

    static void use() {
        GLES20.glUseProgram(prog);
    }

    static void setRes(int w, int h) {
        GLES20.glUseProgram(prog);
        GLES20.glUniform2f(uRes, w, h);
    }

    static void setVP(float[] vp) {
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
    }

    static void setRefl(boolean on, float nx, float ny, float nz, float d, float thr) {
        reflPass = on;
        GLES20.glUseProgram(prog);
        GLES20.glUniform1f(uRefPass, on ? 1f : 0f);
        GLES20.glUniform4f(uRefPl, nx, ny, nz, d);
        GLES20.glUniform1f(uThr, thr);
    }

    static void setMirror(boolean on, float[] pl) {
        GLES20.glUseProgram(prog);
        GLES20.glUniform1f(uMirOn, on ? 1f : 0f);
        if (on) GLES20.glUniform4f(uMirPl, pl[0], pl[1], pl[2], pl[3]);
    }

    static void useMat(Mat m) {
        if (m.tex != null) {
            int id = m.tex.id();
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
            GLES20.glUniform1f(uHasTex, 1f);
        } else {
            GLES20.glUniform1f(uHasTex, 0f);
        }
        GLES20.glUniform4f(uColor, m.r, m.g, m.b, m.a);
        GLES20.glUniform3f(uEmis, m.er, m.eg, m.eb);
        GLES20.glUniform1f(uUnlit, m.unlit ? 1f : 0f);
        GLES20.glUniform1f(uGlass, m.glass);
        GLES20.glUniform1f(uPud, m.puddle && reflOn && !reflPass ? 1f : 0f);
        GLES20.glUniform1f(uFogOn, m.noFog ? 0f : 1f);
        if (m.blend) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, m.additive ? GLES20.GL_ONE : GLES20.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GLES20.glDisable(GLES20.GL_BLEND);
        }
        GLES20.glDepthMask(m.depthWrite);
        if (m.offset) {
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
            GLES20.glPolygonOffset(-2f, -2f);
        } else {
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);
        }
    }

    static void draw(Geo g, float[] model) {
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        g.draw(GLES20.GL_TRIANGLES);
    }

    static void drawPoints(Geo g, float[] model) {
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        g.draw(GLES20.GL_POINTS);
    }
}
