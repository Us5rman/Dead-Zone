package com.deadzone.game;

import android.opengl.GLES20;

final class Gfx {
    static int gen = 0;
    static int aPos, aNor, aUv;

    private static int prog;
    private static int uVP, uModel, uTex, uHasTex, uColor, uEmis, uUnlit, uFogOn, uPS;
    private static int uAmb, uDirV, uDirCol, uCam, uFogCol, uFogDen;
    private static int uNumPl, uPlPos, uPlCol, uPlRange;
    private static int uSpPos, uSpDir, uSpCol, uSpCos0, uSpCos1, uSpRange;

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

    private static final String FRAG =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "varying vec3 vWorld;\n" +
            "varying vec3 vNor;\n" +
            "varying vec2 vUv;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform float uHasTex;\n" +
            "uniform vec4 uColor;\n" +
            "uniform vec3 uEmis;\n" +
            "uniform float uUnlit;\n" +
            "uniform float uFogOn;\n" +
            "uniform vec3 uAmb;\n" +
            "uniform vec3 uDirV;\n" +
            "uniform vec3 uDirCol;\n" +
            "uniform vec3 uCam;\n" +
            "uniform vec3 uFogCol;\n" +
            "uniform float uFogDen;\n" +
            "uniform float uNumPl;\n" +
            "uniform vec3 uPlPos[8];\n" +
            "uniform vec3 uPlCol[8];\n" +
            "uniform float uPlRange[8];\n" +
            "uniform vec3 uSpPos;\n" +
            "uniform vec3 uSpDir;\n" +
            "uniform vec3 uSpCol;\n" +
            "uniform float uSpCos0;\n" +
            "uniform float uSpCos1;\n" +
            "uniform float uSpRange;\n" +
            "void main() {\n" +
            "  vec4 base = uColor;\n" +
            "  if (uHasTex > 0.5) base *= texture2D(uTex, vUv);\n" +
            "  vec3 col;\n" +
            "  if (uUnlit > 0.5) {\n" +
            "    col = base.rgb;\n" +
            "  } else {\n" +
            "    vec3 n = normalize(vNor);\n" +
            "    vec3 light = uAmb + uDirCol * max(dot(n, uDirV), 0.0);\n" +
            "    for (int i = 0; i < 8; i++) {\n" +
            "      if (float(i) >= uNumPl) break;\n" +
            "      vec3 d = uPlPos[i] - vWorld;\n" +
            "      float dist = max(length(d), 0.001);\n" +
            "      float at = clamp(1.0 - dist / uPlRange[i], 0.0, 1.0);\n" +
            "      light += uPlCol[i] * max(dot(n, d / dist), 0.0) * at;\n" +
            "    }\n" +
            "    vec3 sd = uSpPos - vWorld;\n" +
            "    float sl = max(length(sd), 0.001);\n" +
            "    vec3 L = sd / sl;\n" +
            "    float cs = dot(-L, uSpDir);\n" +
            "    float sa = smoothstep(uSpCos0, uSpCos1, cs) * clamp(1.0 - sl / uSpRange, 0.0, 1.0);\n" +
            "    light += uSpCol * max(dot(n, L), 0.0) * sa;\n" +
            "    col = base.rgb * light + uEmis;\n" +
            "  }\n" +
            "  if (uFogOn > 0.5) {\n" +
            "    float dd = length(vWorld - uCam);\n" +
            "    float f = 1.0 - exp(-uFogDen * uFogDen * dd * dd);\n" +
            "    col = mix(col, uFogCol, clamp(f, 0.0, 1.0));\n" +
            "  }\n" +
            "  gl_FragColor = vec4(col, base.a);\n" +
            "}\n";

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
        uSpPos = loc("uSpPos");
        uSpDir = loc("uSpDir");
        uSpCol = loc("uSpCol");
        uSpCos0 = loc("uSpCos0");
        uSpCos1 = loc("uSpCos1");
        uSpRange = loc("uSpRange");
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glEnableVertexAttribArray(aNor);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform1f(uPS, 1f);
    }

    /** per frame setup: lights, fog, camera */
    static void frame(float[] vp, float[] cam, float[] spotDir, float[] amb, float[] plPos, float[] plCol,
                      float[] plRange, int n) {
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0);
        GLES20.glUniform3f(uCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(uAmb, amb[0], amb[1], amb[2]);
        float dl = (float) Math.sqrt(20 * 20 + 40 * 40 + 10 * 10);
        GLES20.glUniform3f(uDirV, -20 / dl, 40 / dl, 10 / dl);
        float[] dc = Mat.rgb(0x6677aa);
        GLES20.glUniform3f(uDirCol, dc[0] * .35f, dc[1] * .35f, dc[2] * .35f);
        float[] fc = Mat.rgb(0x070b10);
        GLES20.glUniform3f(uFogCol, fc[0], fc[1], fc[2]);
        GLES20.glUniform1f(uFogDen, .04f);
        GLES20.glUniform1f(uNumPl, n);
        GLES20.glUniform3fv(uPlPos, 8, plPos, 0);
        GLES20.glUniform3fv(uPlCol, 8, plCol, 0);
        GLES20.glUniform1fv(uPlRange, 8, plRange, 0);
        GLES20.glUniform3f(uSpPos, cam[0], cam[1], cam[2]);
        GLES20.glUniform3f(uSpDir, spotDir[0], spotDir[1], spotDir[2]);
        float[] sc = Mat.rgb(0xfff0c8);
        GLES20.glUniform3f(uSpCol, sc[0] * 1.6f, sc[1] * 1.6f, sc[2] * 1.6f);
        GLES20.glUniform1f(uSpCos0, (float) Math.cos(.55));
        GLES20.glUniform1f(uSpCos1, (float) Math.cos(.55 * (1 - .45)));
        GLES20.glUniform1f(uSpRange, 32f);
        GLES20.glUniform1f(uPS, 1.6f);
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
