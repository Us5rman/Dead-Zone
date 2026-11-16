package com.deadzone.game;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class Post {
    private static final String VERT = """
            attribute vec2 aP;
            varying vec2 vUv;
            void main() {
              vUv = aP * 0.5 + 0.5;
              gl_Position = vec4(aP, 0.0, 1.0);
            }
            """;

    private static final String FRAG = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vUv;
            uniform sampler2D uScene;
            uniform sampler2D uNoise;
            uniform mat4 uInvVP;
            uniform mat4 uPrevVP;
            uniform vec3 uCam;
            uniform float uStr;
            void main() {
              vec3 base = texture2D(uScene, vUv).rgb;
              vec2 ndc = vUv * 2.0 - 1.0;
              vec4 f = uInvVP * vec4(ndc, 1.0, 1.0);
              vec3 d = normalize(f.xyz / f.w - uCam);
              vec3 p;
              if (d.y < -0.02) p = uCam + d * min(-uCam.y / d.y, 150.0);
              else p = uCam + d * 200.0;
              vec4 pc = uPrevVP * vec4(p, 1.0);
              vec2 vel = vec2(0.0);
              if (pc.w > 0.01) vel = (ndc - pc.xy / pc.w) * 0.5;
              vel *= uStr * 0.8;
              float vl = length(vel);
              if (vl > 0.06) vel *= 0.06 / vl;
              if (vl < 0.0006) {
                gl_FragColor = vec4(base, 1.0);
                return;
              }
              float dither = texture2D(uNoise, gl_FragCoord.xy * 0.0078125).r;
              vec3 sum = base;
              for (int i = 0; i < 6; i++) {
                float t = (float(i) + dither) / 6.0 - 0.5;
                sum += texture2D(uScene, clamp(vUv + vel * t, 0.002, 0.998)).rgb;
              }
              gl_FragColor = vec4(sum / 7.0, 1.0);
            }
            """;

    static boolean ok;
    static final float STRENGTH = 1.0f;

    private static int prog, vbo, fbo, tex, dep, w, h;
    private static int uScene, uNoise, uInv, uPrev, uCam, uStr;
    private static final float[] INV = new float[16];

    private Post() {
    }

    static void init() {
        prog = Sky.link(VERT, FRAG, new String[]{"aP"});
        uScene = GLES20.glGetUniformLocation(prog, "uScene");
        uNoise = GLES20.glGetUniformLocation(prog, "uNoise");
        uInv = GLES20.glGetUniformLocation(prog, "uInvVP");
        uPrev = GLES20.glGetUniformLocation(prog, "uPrevVP");
        uCam = GLES20.glGetUniformLocation(prog, "uCam");
        uStr = GLES20.glGetUniformLocation(prog, "uStr");
        int[] b = new int[1];
        GLES20.glGenBuffers(1, b, 0);
        vbo = b[0];
        FloatBuffer fb = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(new float[]{-1, -1, 3, -1, -1, 3}).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 24, fb, GLES20.GL_STATIC_DRAW);
        fbo = 0;
        ok = false;
    }

    static void resize(int width, int height) {
        w = Math.max(16, width);
        h = Math.max(16, height);
        int[] b = new int[1];
        if (fbo == 0) {
            GLES20.glGenFramebuffers(1, b, 0);
            fbo = b[0];
            GLES20.glGenTextures(1, b, 0);
            tex = b[0];
            GLES20.glGenRenderbuffers(1, b, 0);
            dep = b[0];
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, dep);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, dep);
        ok = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    static void begin() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, w, h);
    }

    static void end(float[] vp, float[] prevVp, float[] cam) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, w, h);
        Matrix.invertM(INV, 0, vp, 0);
        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uInv, 1, false, INV, 0);
        GLES20.glUniformMatrix4fv(uPrev, 1, false, prevVp, 0);
        GLES20.glUniform3f(uCam, cam[0], cam[1], cam[2]);
        GLES20.glUniform1f(uStr, STRENGTH);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform1i(uScene, 0);
        GLES20.glUniform1i(uNoise, 1);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(false);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
        Gfx.use();
    }
}
