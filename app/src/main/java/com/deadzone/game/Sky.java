package com.deadzone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

final class Sky {
    static final float[] BH = norm(-30f, 34f, -50f);

    private static final int RAIN = 2600;
    private static final int NS = 128;
    private static final float[] INV = new float[16];

    private static int skyProg, rainProg, noiseTex, skyVbo, rainVbo;
    private static final int[] FBO = new int[2], RTEX = new int[2], RDEP = new int[2];
    private static int rw, rh;
    static boolean reflOk;
    private static int sInv, sCam, sBH, sHor, sTime, sFlash, sCover, sNoise, sSize, sCut, sRefN;
    private static int rVP, rCam, rTime, rFlash, rFogCol, rFogDen, rMoonCol, rRain, rSh;
    private static int pudProg, pudVbo, pudVerts, shTex;
    private static int pVP, pCam, pTime, pGrow, pRain, pFlash, pFogCol, pFogDen, pHole, pMoonCol;
    private static boolean vtf;
    private static byte[] shData;
    private static boolean shDirty;
    private static float[] pudData = new float[0];
    private static boolean pudDirty;

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
            uniform vec3 uBH;
            uniform vec3 uHor;
            uniform float uTime;
            uniform float uFlash;
            uniform float uCover;
            uniform float uSize;
            uniform float uCut;
            uniform vec3 uRefN;
            uniform sampler2D uNoise;

            float vn(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return texture2D(uNoise, (i + f + 0.5) * 0.0078125).r;
            }

            float fbm4(vec2 p) {
              float v = 0.0;
              float a = 0.5;
              for (int i = 0; i < 3; i++) {
                v += a * vn(p);
                p = mat2(1.6, 1.2, -1.2, 1.6) * p + 7.3;
                a *= 0.5;
              }
              return v * 1.1429;
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
              d -= 2.0 * dot(d, uRefN) * uRefN;
              float h = clamp(d.y, 0.0, 1.0);
              vec3 zen = vec3(0.008, 0.016, 0.040);
              vec3 col = mix(uHor, zen, pow(h, 0.45));
              float md = dot(d, uBH);
              float mp = max(md, 0.0);
              col += vec3(0.30, 0.16, 0.07) * (pow(mp, 5.0) * 0.25 + pow(mp, 24.0) * 0.35) * uSize * 0.6;
              col += vec3(0.05, 0.07, 0.12) * uFlash * (0.4 + h);

              vec2 g = d.xz / (max(d.y, 0.0) + 0.35) * 70.0;
              vec2 cell = floor(g);
              vec4 n = texture2D(uNoise, (cell + 0.5) * 0.0078125);
              float st = smoothstep(0.09, 0.0, length(fract(g) - 0.5 - (n.gb - 0.5) * 0.6)) * step(0.965, n.r);
              st *= 0.6 + 0.4 * sin(uTime * 2.5 + n.b * 60.0);
              st *= smoothstep(0.02, 0.25, d.y);
              col += vec3(0.7, 0.8, 1.0) * st * 0.9;

              if (md > uCut) {
                float rs = 0.075 * uSize;
                vec3 bt = normalize(cross(vec3(0.0, 1.0, 0.0), uBH));
                vec3 bb = cross(uBH, bt);
                vec2 uv = vec2(dot(d, bt), dot(d, bb)) / rs;
                col *= smoothstep(0.97, 1.0, length(uv));
                col += gargantua(uv, uTime, 1.0);
              }

              if (d.y > 0.015) {
                float t = (95.0 - uCam.y) / d.y;
                vec2 p = (uCam.xz + d.xz * t) * 0.011 + vec2(uTime * 0.006, uTime * 0.0035);
                float c = fbm4(p);
                float cd = smoothstep(uCover, uCover + 0.24, c);
                if (cd > 0.002) {
                  float cl = smoothstep(uCover, uCover + 0.24, fbm2(p + uBH.xz * 0.05));
                  float shade = exp(-cl * 1.8);
                  float rim = pow(mp, 10.0) * (0.6 + 0.4 * uSize);
                  vec3 lc = vec3(0.50, 0.34, 0.22) * (0.5 + 0.35 * uSize);
                  vec3 cc = vec3(0.05, 0.065, 0.095) * (0.5 + 0.5 * shade);
                  cc += lc * shade * (0.12 + 0.9 * rim) * (0.4 + 0.6 * (1.0 - cd));
                  cc += vec3(0.5, 0.6, 0.95) * uFlash * (0.5 + 0.5 * shade);
                  cd *= smoothstep(0.015, 0.22, d.y);
                  col = mix(col, cc, cd);
                }
              }
              gl_FragColor = vec4(col, 1.0);
            }
            """;



    /** Gargantua style black hole: dark shadow, thin photon ring, flat disc band and lensed arcs over/under it */
    private static final String HOLE_FN = """
            float hfbm(vec2 p) {
              float v = 0.0;
              float a = 0.5;
              for (int i = 0; i < 4; i++) {
                v += a * vn(p);
                p = mat2(1.6, 1.2, -1.2, 1.6) * p + 7.3;
                a *= 0.5;
              }
              return v * 1.0667;
            }

            vec3 gargantua(vec2 uv, float t, float boost) {
              float ca = 0.9848;
              float sa = -0.1736;
              float x = uv.x * ca - uv.y * sa;
              float y = uv.x * sa + uv.y * ca;
              float r = length(vec2(x, y));
              float rn = max(r, 0.001);
              float ax = abs(x);
              float dop = 0.42 + 0.9 * smoothstep(-2.6, 2.6, x);
              float ang = atan(y, x + 0.00001);
              float orbR = 1.0 / (r * sqrt(r) + 0.6);

              float up = smoothstep(-0.2, 0.85, y / rn);
              float dn = smoothstep(0.4, 0.98, -y / rn);
              float sw = hfbm(vec2(ang * 2.4 - t * 0.35 * orbR, r * 7.0));
              float w1 = (r - 1.36) / 0.28;
              float w2 = (r - 1.95) / 0.62;
              float w3 = (r - 1.16) / 0.09;
              float arcs = exp(-w1 * w1) * up * 1.9 + exp(-w2 * w2) * up * 0.55 + exp(-w3 * w3) * dn;
              arcs *= (0.4 + 1.15 * sw) * dop;

              float th = 0.042 + 0.03 * ax;
              float across = exp(-(y / th) * (y / th));
              float prof = smoothstep(1.0, 1.4, ax) * smoothstep(6.2, 2.2, ax);
              float orb = t * 0.5 * orbR * (x < 0.0 ? -1.0 : 1.0);
              float sk = 0.4 + 1.1 * hfbm(vec2(x * 0.85 - orb, y * 13.0));
              float band = across * prof * sk * dop * 2.8;

              vec3 hot = vec3(1.0, 0.93, 0.8);
              vec3 warm = vec3(0.95, 0.55, 0.22);
              vec3 cool = vec3(0.55, 0.26, 0.10);
              vec3 dcb = mix(hot, mix(warm, cool, smoothstep(2.5, 6.0, ax)), smoothstep(1.1, 3.0, ax));
              vec3 dca = mix(hot, warm, smoothstep(1.15, 2.6, r));

              vec3 col = dcb * band + dca * arcs;
              float rg = (r - 1.045) / 0.02;
              col += vec3(1.0, 0.95, 0.85) * exp(-rg * rg) * (1.2 + 0.6 * dop);
              col += vec3(0.7, 0.4, 0.18) * exp(-max(r - 1.0, 0.0) * 0.6) * 0.10;
              return col * smoothstep(0.99, 1.05, r) * boost;
            }
            """;

    private static final String HOLE_FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform vec2 uRes;
            uniform vec2 uC;
            uniform float uR;
            uniform vec2 uBand;
            uniform float uTime;
            uniform vec3 uTouch;
            uniform sampler2D uNoise;

            float vn(vec2 p) {
              vec2 i = floor(p);
              vec2 f = fract(p);
              f = f * f * (3.0 - 2.0 * f);
              return texture2D(uNoise, (i + f + 0.5) * 0.0078125).r;
            }

            float fbm(vec2 p) {
              float v = 0.0;
              float a = 0.5;
              for (int i = 0; i < 4; i++) {
                v += a * vn(p);
                p = mat2(1.6, 1.2, -1.2, 1.6) * p + 7.3;
                a *= 0.5;
              }
              return v * 1.0667;
            }

            vec3 scene(vec2 fc, float boost) {
              vec2 uv = vec2(fc.x - uC.x, uC.y - fc.y) / uR;
              float r = length(uv);

              // stars, pulled slightly towards the hole (lensing)
              vec2 us = uv * (1.0 - 0.55 / (r * r + 1.2));
              vec2 fs = vec2(uC.x + us.x * uR, uC.y - us.y * uR);
              vec2 g = fs / 15.0;
              vec2 cell = floor(g);
              vec4 n = texture2D(uNoise, (cell + 0.5) * 0.0078125);
              float st = smoothstep(0.30, 0.0, length(fract(g) - 0.5 - (n.gb - 0.5) * 0.6)) * step(0.955, n.r);
              st *= 0.55 + 0.45 * sin(uTime * 2.2 + n.b * 60.0);
              vec3 col = vec3(0.7, 0.8, 1.0) * st * 0.9;
              col += vec3(0.30, 0.16, 0.07) * (0.20 * exp(-r * 0.32) + 0.35 * exp(-r * 1.1)) * boost;
              col *= smoothstep(0.97, 1.0, r);

              col += gargantua(uv, uTime, boost);
              return col;
            }

            void main() {
              vec2 fc = vec2(gl_FragCoord.x, uRes.y - gl_FragCoord.y);
              if (fc.y < uBand.x || fc.y > uBand.y) {
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                return;
              }
              float boost = 1.0 + uTouch.z * 1.1 * exp(-length(uTouch.xy - uC) / (uR * 4.0));
              vec2 dd = fc - uC;
              vec3 col;
              if (dot(dd, dd) < 60.0 * uR * uR) {
                col = scene(fc + vec2(-0.25, -0.25), boost) + scene(fc + vec2(0.25, -0.25), boost)
                    + scene(fc + vec2(-0.25, 0.25), boost) + scene(fc + vec2(0.25, 0.25), boost);
                col *= 0.25;
              } else {
                col = scene(fc, boost);
              }

              if (uTouch.z > 0.003) {
                vec2 tp = fc - uTouch.xy;
                float td = length(tp);
                float R = uRes.y * 0.07;
                float ang = atan(tp.y, tp.x);
                float fl = 0.6 + 0.8 * vn(vec2(ang * 2.2 + uTime * 1.3, uTime * 0.7));
                float k = uTouch.z;
                float rr = R * (0.35 + 0.65 * k);
                float u1 = (td - rr) / (R * 0.035);
                float u2 = (td - rr) / (R * 0.22);
                float u3 = (td - rr * 1.3) / (R * 0.07);
                float ring = exp(-u1 * u1);
                float halo = exp(-u2 * u2);
                float side = abs(tp.x) / max(td, 1.0);
                float arc = exp(-u3 * u3) * smoothstep(0.2, 0.9, side);
                vec3 yel = mix(vec3(1.0, 0.88, 0.55), vec3(0.95, 0.42, 0.12), smoothstep(0.0, 1.0, (td - rr) / (R * 0.5) + 0.3));
                col *= mix(1.0, smoothstep(rr * 0.7, rr * 0.98, td), 0.85 * k);
                col += (yel * (ring * 1.7 * fl + arc * 0.9 * fl) + vec3(0.9, 0.45, 0.12) * halo * 0.28) * k;
              }
              col = 1.0 - exp(-col * 1.5);
              gl_FragColor = vec4(col, 1.0);
            }
            """;

    private static int holeProg, hRes, hC, hR, hBand, hTime, hTouch, hNoise;
    /** menu touch point (px from top-left) and band, set from the UI thread */
    static volatile float menuX, menuY, menuTopPx = 20f, menuBotPx = 30f;
    static volatile boolean menuDown;
    /** the red line sits at this fraction of the screen height; everything below it is cut off */
    static final float MENU_LINE = .76f;
    private static float touchAmt;
    private static float holeT;

    static void drawMenuHole(int w, int h, double dt) {
        if (holeProg == 0 || skyVbo == 0) return;
        holeT = (holeT + (float) dt) % 4000f;
        float target = menuDown ? 1f : 0f;
        touchAmt += (target - touchAmt) * (float) Math.min(1, dt * (menuDown ? 9 : 3.5));
        float bandTop = 0f, bandBot = h * MENU_LINE;
        float rad = Math.min(h * .17f, w * .075f);
        float cx = w * .66f, cy = bandBot - rad * .3f;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(holeProg);
        GLES20.glUniform2f(hRes, w, h);
        GLES20.glUniform2f(hC, cx, cy);
        GLES20.glUniform1f(hR, rad);
        GLES20.glUniform2f(hBand, bandTop, bandBot);
        GLES20.glUniform1f(hTime, holeT);
        GLES20.glUniform3f(hTouch, menuX, menuY, touchAmt);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, noiseTex);
        GLES20.glUniform1i(hNoise, 1);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        Gfx.use();
    }

    private static void initHole() {
        try {
            holeProg = link(SKY_VERT, HOLE_FRAG.replace("vec3 scene(", HOLE_FN + "\nvec3 scene("), new String[]{"aP"});
            hRes = GLES20.glGetUniformLocation(holeProg, "uRes");
            hC = GLES20.glGetUniformLocation(holeProg, "uC");
            hR = GLES20.glGetUniformLocation(holeProg, "uR");
            hBand = GLES20.glGetUniformLocation(holeProg, "uBand");
            hTime = GLES20.glGetUniformLocation(holeProg, "uTime");
            hTouch = GLES20.glGetUniformLocation(holeProg, "uTouch");
            hNoise = GLES20.glGetUniformLocation(holeProg, "uNoise");
        } catch (RuntimeException e) {
            holeProg = 0;
        }
    }

    private static final String RAIN_VERT = """
            uniform mat4 uVP;
            uniform vec3 uCam;
            uniform float uTime;
            uniform float uRain;
            #ifdef SHELTER
            uniform sampler2D uSh;
            #endif
            attribute vec3 aSeed;
            attribute vec2 aCorner;
            varying vec2 vC;
            varying float vDist;
            varying float vA;
            varying float vHot;

            float hAt(vec2 p) {
            #ifdef SHELTER
              vec2 uv = (p + 150.0) / 300.0;
              if (uv.x <= 0.0 || uv.y <= 0.0 || uv.x >= 1.0 || uv.y >= 1.0) return 0.0;
              return texture2D(uSh, uv).r * 16.06;
            #else
              return 0.0;
            #endif
            }

            void main() {
              float r = fract(aSeed.x * 37.17 + aSeed.z * 91.31 + aSeed.y * 13.7);
              float r2 = fract(r * 7.13 + aSeed.y * 3.7);
              vec3 vel = vec3(-1.6, -(11.0 + r * 5.5), -0.8) * (0.85 + 0.3 * uRain);
              vec3 axis = normalize(vel);
              vec3 B = vec3(36.0, 22.0, 36.0);
              vec3 q = mod(aSeed * B + vel * uTime - uCam, B);
              vec3 head = vec3(uCam.x + q.x - B.x * 0.5, uCam.y + q.y - 4.0, uCam.z + q.z - B.z * 0.5);
              vec3 toCam = uCam - head;
              float dist = length(toCam);
              float keep = fract(aSeed.x * 53.7 + aSeed.y * 17.3 + aSeed.z * 7.1);
              float w = max(0.007, dist * 0.0048) * (0.7 + 0.6 * r2);
              float len = (0.5 + r * 0.55) * (0.8 + 0.5 * uRain);
              float hh = hAt(head.xz);
              vec3 pos;
              float alpha = 1.0;
              float hot = 0.0;
              bool dead = keep > uRain || head.y < 0.0;
              if (!dead && hh > 0.25 && head.y < hh) {
                float ta = (hh - head.y) / -vel.y;
                vec2 I = head.xz - vel.xz * ta;
                if (ta < 0.32 && abs(hAt(I) - hh) < 0.06) {
                  vec2 g = vec2(hAt(I + vec2(0.45, 0.0)) - hAt(I - vec2(0.45, 0.0)),
                                hAt(I + vec2(0.0, 0.45)) - hAt(I - vec2(0.0, 0.45)));
                  float ang = r * 6.2831;
                  vec2 dir = length(g) > 0.05 ? -normalize(g) : normalize(vec2(cos(ang), sin(ang)) + vec2(-0.5, -0.25));
                  float s = (0.25 + 0.8 * r2) * (1.0 - exp(-ta * 8.0));
                  vec3 P1 = vec3(I.x + dir.x * s, hh + 0.012, I.y + dir.y * s);
                  vec3 P0 = vec3(I.x + dir.x * max(0.0, s - 0.28), hh + 0.012, I.y + dir.y * max(0.0, s - 0.28));
                  vec3 ax = normalize(vec3(dir.x, 0.0, dir.y));
                  vec3 tc = uCam - P1;
                  vec3 sd = cross(ax, tc);
                  float sl = length(sd);
                  sd = sl > 0.0001 ? sd / sl : vec3(1.0, 0.0, 0.0);
                  float ws = max(0.012, length(tc) * 0.0042);
                  pos = mix(P0, P1, aCorner.y) + sd * aCorner.x * ws * mix(0.5, 1.0, aCorner.y);
                  alpha = (1.0 - ta / 0.32) * 0.8;
                  hot = 0.6;
                  dist = length(tc);
                } else {
                  dead = true;
                  pos = head;
                }
              } else {
                vec3 side = cross(axis, toCam);
                float sl = length(side);
                side = sl > 0.0001 ? side / sl : vec3(1.0, 0.0, 0.0);
                pos = mix(head - axis * len, head, aCorner.y) + side * aCorner.x * w * mix(0.35, 1.0, aCorner.y);
                hot = (1.0 - smoothstep(0.0, 1.5, head.y - hh)) * 0.5;
              }
              vC = aCorner;
              vDist = dist;
              vA = (0.35 + 0.65 * clamp(0.015 / w, 0.0, 1.0)) * alpha;
              vHot = hot;
              if (dead || pos.y < 0.0) {
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
            varying float vHot;
            uniform vec3 uFogCol;
            uniform float uFogDen;
            uniform vec3 uMoonCol;
            uniform float uFlash;
            void main() {
              float across = 1.0 - abs(vC.x);
              float core = smoothstep(0.0, 0.85, across);
              float along = vC.y * vC.y * (0.35 + 0.65 * vC.y);
              float a = core * along;
              a *= smoothstep(0.4, 1.8, vDist) * vA * 0.75;
              vec3 col = uMoonCol * (0.85 + 0.5 * vC.y * vC.y) + vec3(0.35, 0.42, 0.6) * uFlash;
              col += vec3(0.25, 0.28, 0.34) * vHot * core;
              float f = 1.0 - exp(-uFogDen * uFogDen * vDist * vDist);
              col = mix(col, uFogCol, f * 0.6);
              a *= 1.0 - f * 0.85;
              gl_FragColor = vec4(col, a);
            }
            """;

    private static final String PUD_VERT = """
            uniform mat4 uVP;
            attribute vec3 aP;
            attribute vec3 aUV;
            varying vec3 vUV;
            varying vec3 vW;
            void main() {
              vUV = aUV;
              vW = aP;
              gl_Position = uVP * vec4(aP, 1.0);
            }
            """;

    private static final String PUD_FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec3 vUV;
            varying vec3 vW;
            uniform vec3 uCam;
            uniform float uTime;
            uniform float uGrow;
            uniform float uRain;
            uniform float uFlash;
            uniform vec3 uFogCol;
            uniform float uFogDen;
            uniform vec3 uHole;
            uniform vec3 uMoonCol;
            float hash(vec2 p) {
              return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }
            void main() {
              vec2 uv = vUV.xy;
              float seed = vUV.z;
              float ang = atan(uv.y, uv.x);
              float r = length(uv);
              float edge = 0.74 + 0.10 * sin(ang * 3.0 + seed * 17.0) + 0.07 * sin(ang * 5.0 + seed * 31.0)
                         + 0.04 * sin(ang * 9.0 + seed * 7.0);
              edge *= uGrow;
              float mask = 1.0 - smoothstep(edge - 0.16, edge, r);
              if (mask < 0.02) discard;
              float rip = 0.0;
              vec2 grad = vec2(0.0);
              for (int i = 0; i < 4; i++) {
                float fi = float(i);
                float sp = 0.45 + 0.35 * hash(vec2(seed, fi));
                float ph = fract(uTime * sp + hash(vec2(fi, seed * 3.1)));
                vec2 c = (vec2(hash(vec2(seed + fi, 1.3)), hash(vec2(seed, fi + 7.7))) * 2.0 - 1.0) * 0.5 * edge;
                vec2 dv = uv - c;
                float d = length(dv);
                float rr = ph * 0.5 * edge + 0.02;
                float ring = (1.0 - smoothstep(0.0, 0.045, abs(d - rr))) * (1.0 - ph) * (1.0 - ph);
                rip += ring;
                grad += normalize(dv + 1e-4) * ring * sign(d - rr);
              }
              rip *= uRain;
              grad *= uRain;
              vec3 N = normalize(vec3(grad.x * 0.9, 1.0, grad.y * 0.9));
              vec3 V = normalize(uCam - vW);
              vec3 R = reflect(-V, N);
              float cosv = clamp(V.y, 0.0, 1.0);
              float fres = 0.05 + 0.95 * pow(1.0 - cosv, 4.0);
              vec3 horizon = uFogCol * 2.2 + vec3(0.02, 0.025, 0.04);
              vec3 zenith = vec3(0.015, 0.025, 0.06);
              vec3 sky = mix(horizon, zenith, clamp(R.y * 1.6, 0.0, 1.0));
              sky += vec3(0.35, 0.42, 0.6) * uFlash * 1.4;
              float sp = max(dot(R, normalize(uHole)), 0.0);
              vec3 glint = uMoonCol * (pow(sp, 90.0) * 1.8 + pow(sp, 14.0) * 0.18);
              vec3 water = vec3(0.012, 0.018, 0.026);
              vec3 col = mix(water, sky, clamp(fres * 0.85 + 0.12, 0.0, 1.0)) + glint;
              col += vec3(0.45, 0.52, 0.65) * rip * 0.35;
              float d = length(uCam - vW);
              float f = 1.0 - exp(-uFogDen * uFogDen * d * d);
              col = mix(col, uFogCol, f * 0.7);
              float a = mask * (0.62 + 0.3 * fres);
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

    static int noiseTexId() {
        return noiseTex;
    }

    static int link(String vs, String fs, String[] attribs) {
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
        FBO[0] = FBO[1] = 0;
        reflOk = false;
        skyProg = link(SKY_VERT, SKY_FRAG.replace("void main() {", HOLE_FN + "\nvoid main() {"), new String[]{"aP"});
        int[] vu = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, vu, 0);
        vtf = vu[0] > 0;
        try {
            rainProg = link((vtf ? "#define SHELTER 1\n" : "") + RAIN_VERT, RAIN_FRAG, new String[]{"aSeed", "aCorner"});
        } catch (RuntimeException e) {
            vtf = false;
            rainProg = link(RAIN_VERT, RAIN_FRAG, new String[]{"aSeed", "aCorner"});
        }
        try {
            pudProg = link(PUD_VERT, PUD_FRAG, new String[]{"aP", "aUV"});
        } catch (RuntimeException e) {
            pudProg = 0;
        }

        sInv = GLES20.glGetUniformLocation(skyProg, "uInvVP");
        sCam = GLES20.glGetUniformLocation(skyProg, "uCam");
        sBH = GLES20.glGetUniformLocation(skyProg, "uBH");
        sSize = GLES20.glGetUniformLocation(skyProg, "uSize");
        sCut = GLES20.glGetUniformLocation(skyProg, "uCut");
        sRefN = GLES20.glGetUniformLocation(skyProg, "uRefN");
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
        rRain = GLES20.glGetUniformLocation(rainProg, "uRain");
        rSh = GLES20.glGetUniformLocation(rainProg, "uSh");
        pVP = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uVP");
        pCam = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uCam");
        pTime = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uTime");
        pGrow = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uGrow");
        pRain = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uRain");
        pFlash = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uFlash");
        pFogCol = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uFogCol");
        pFogDen = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uFogDen");
        pHole = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uHole");
        pMoonCol = pudProg == 0 ? -1 : GLES20.glGetUniformLocation(pudProg, "uMoonCol");
        int[] st = new int[1];
        GLES20.glGenTextures(1, st, 0);
        shTex = st[0];
        shDirty = shData != null;
        pudVbo = 0;
        pudDirty = pudData.length > 0;

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
        initHole();

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

    static void resize(int w, int h) {
        float k = Math.min(1f, 720f / w);
        rw = Math.max(64, Math.round(w * k));
        rh = Math.max(32, Math.round(h * k));
        reflOk = true;
        for (int i = 0; i < 2; i++) {
            if (FBO[i] == 0) {
                int[] b = new int[1];
                GLES20.glGenFramebuffers(1, b, 0);
                FBO[i] = b[0];
                GLES20.glGenTextures(1, b, 0);
                RTEX[i] = b[0];
                GLES20.glGenRenderbuffers(1, b, 0);
                RDEP[i] = b[0];
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2 + i);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, RTEX[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, rw, rh, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, RDEP[i]);
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, rw, rh);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                    RTEX[i], 0);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                    GLES20.GL_RENDERBUFFER, RDEP[i]);
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                reflOk = false;
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    static void beginReflect(int i) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2 + i);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, FBO[i]);
        GLES20.glViewport(0, 0, rw, rh);
        GLES20.glDepthMask(true);
        GLES20.glClearColor(Gfx.FOG[0], Gfx.FOG[1], Gfx.FOG[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    }

    static void endReflect(int i, int w, int h) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2 + i);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, RTEX[i]);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    static void drawSky(float[] vp, float[] cam, float[] rn) {
        Matrix.invertM(INV, 0, vp, 0);
        GLES20.glUseProgram(skyProg);
        GLES20.glUniformMatrix4fv(sInv, 1, false, INV, 0);
        GLES20.glUniform3f(sCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(sBH, BH[0], BH[1], BH[2]);
        GLES20.glUniform1f(sSize, Gfx.bh);
        GLES20.glUniform1f(sCut, (float) Math.cos(Math.min(.075 * Gfx.bh * 6.5, 1.25)));
        GLES20.glUniform3f(sRefN, rn[0], rn[1], rn[2]);
        GLES20.glUniform3f(sHor, Gfx.FOG[0], Gfx.FOG[1], Gfx.FOG[2]);
        GLES20.glUniform1f(sTime, Gfx.time);
        GLES20.glUniform1f(sFlash, Gfx.flash);
        GLES20.glUniform1f(sCover, .5f);
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

    static void setShelter(byte[] data) {
        shData = data;
        shDirty = true;
    }

    static void setPuddles(float[] spots) {
        pudData = spots == null ? new float[0] : spots;
        pudDirty = true;
    }

    private static void uploadShelter() {
        shDirty = false;
        if (shData == null || !vtf) return;
        ByteBuffer bb = ByteBuffer.allocateDirect(shData.length).order(ByteOrder.nativeOrder());
        bb.put(shData).position(0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shTex);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, World.SH_N, World.SH_N, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, bb);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private static void uploadPuddles() {
        pudDirty = false;
        int n = pudData.length / 4;
        pudVerts = n * 6;
        if (n == 0) return;
        float[] v = new float[n * 6 * 6];
        float[][] cr = {{-1, -1}, {1, -1}, {1, 1}, {-1, -1}, {1, 1}, {-1, 1}};
        int k = 0;
        for (int i = 0; i < n; i++) {
            float x = pudData[i * 4], z = pudData[i * 4 + 1], r = pudData[i * 4 + 2], sd = pudData[i * 4 + 3];
            float q = r * 1.35f;
            for (int c = 0; c < 6; c++) {
                v[k++] = x + cr[c][0] * q;
                v[k++] = .022f;
                v[k++] = z + cr[c][1] * q;
                v[k++] = cr[c][0];
                v[k++] = cr[c][1];
                v[k++] = sd;
            }
        }
        if (pudVbo == 0) {
            int[] b = new int[1];
            GLES20.glGenBuffers(1, b, 0);
            pudVbo = b[0];
        }
        FloatBuffer fb = ByteBuffer.allocateDirect(v.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(v).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pudVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.length * 4, fb, GLES20.GL_STATIC_DRAW);
    }

    static void drawPuddles(float[] vp, float[] cam) {
        if (pudDirty) uploadPuddles();
        float grow = Math.max(0f, Math.min(1f, (Gfx.wet - .3f) / .55f));
        if (pudProg == 0 || grow <= .02f || pudVerts == 0 || pudVbo == 0) return;
        GLES20.glUseProgram(pudProg);
        GLES20.glUniformMatrix4fv(pVP, 1, false, vp, 0);
        GLES20.glUniform3f(pCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform1f(pTime, Gfx.time);
        GLES20.glUniform1f(pGrow, grow);
        GLES20.glUniform1f(pRain, Math.min(1f, Gfx.rain * 1.4f));
        GLES20.glUniform1f(pFlash, Gfx.flash);
        GLES20.glUniform3f(pFogCol, Gfx.FOG[0] + Gfx.flash * .1f, Gfx.FOG[1] + Gfx.flash * .13f, Gfx.FOG[2] + Gfx.flash * .2f);
        GLES20.glUniform1f(pFogDen, Gfx.FOG_DEN);
        GLES20.glUniform3f(pHole, BH[0], BH[1], BH[2]);
        GLES20.glUniform3f(pMoonCol, .52f + .06f * Gfx.bh, .44f + .03f * Gfx.bh, .42f);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL);
        GLES20.glPolygonOffset(-2f, -2f);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, pudVbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 24, 0);
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 24, 12);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, pudVerts);
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL);
        Gfx.use();
    }

    static void drawRain(float[] vp, float[] cam) {
        if (Gfx.rain < .03f) return;
        if (shDirty) uploadShelter();
        GLES20.glUseProgram(rainProg);
        if (vtf) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE7);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shTex);
            GLES20.glUniform1i(rSh, 7);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        }
        GLES20.glUniform1f(rRain, Gfx.rain);
        GLES20.glUniformMatrix4fv(rVP, 1, false, vp, 0);
        GLES20.glUniform3f(rCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform1f(rTime, Gfx.time);
        GLES20.glUniform1f(rFlash, Gfx.flash);
        GLES20.glUniform3f(rFogCol, Gfx.FOG[0] + Gfx.flash * .1f, Gfx.FOG[1] + Gfx.flash * .13f, Gfx.FOG[2] + Gfx.flash * .2f);
        GLES20.glUniform1f(rFogDen, Gfx.FOG_DEN);
        GLES20.glUniform3f(rMoonCol, .52f + .06f * Gfx.bh, .44f + .03f * Gfx.bh, .42f);
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
