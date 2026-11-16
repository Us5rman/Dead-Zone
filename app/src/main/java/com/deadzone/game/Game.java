package com.deadzone.game;

import android.graphics.Color;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

final class Game implements GLSurfaceView.Renderer, Net.Callbacks {
    interface Ui {
        void showMenu(boolean canResume);

        void hideMenu();

        void showOver(String text, boolean canRestart);

        void hideOver();

        void status(String s);

        void redraw();

        void thunder(int idx, float vol);

        void rain(float level);

        void sound(String name, float vol, float rate);

        void bhSound(int cmd, int posMs);
    }

    static final int SOLO = 0, HOST = 1, CLIENT = 2;
    static final int MENU = 0, PLAY = 1, OVER = 2;
    static final double PI = Math.PI;
    private static final int[] AVC = {0xd04a3a, 0x2a6fd0, 0x30b060, 0xd0a020};
    private static final double[][] RLK = {
            {0, 0, 0, 0, 0, 0, 0}, {.14, -.04, -.10, .05, -.25, .2, -.35}, {.26, -.05, -.13, .06, -.35, .25, -.5},
            {.42, -.05, -.15, .06, -.4, .25, -.55}, {.62, -.05, -.14, .05, -.4, .25, -.5}, {.7, -.04, -.12, .04, -.3, .2, -.4},
            {.78, -.02, -.08, .02, -.2, .12, -.2}, {.9, 0, -.02, 0, -.05, 0, -.04}, {1, 0, 0, 0, 0, 0, 0}};
    private static final double[][] SPAWN_AT = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}};

    static final class Tgt {
        final String id;
        final double x, z;

        Tgt(String id, double x, double z) {
            this.id = id;
            this.x = x;
            this.z = z;
        }
    }

    static final class Remote {
        String id;
        double x, z, yaw, pitch, ph;
        boolean dead;
        double y, flashT;
        int f, lf, idx, mode;
        Node node, inner, lL, lR, flashN;
    }

    static final class Zombie {
        int id;
        double x, z, ry, tx, tz, hp, t, dt, atk, sp, scale, dmg = 10;
        double nudge, nx, nz, chk, lx, lz;
        final double[] mv = new double[2];
        int type;
        boolean dead;
        Node o, g, aL, aR, lL, lR;
    }

    static final class Blood {
        double x, y, z, vx, vy, vz, l;
    }

    static final class Debris {
        Node n;
        double vx, vy, vz, wx, wy, wz, h, b, l = 7;
        boolean mag, rest;
        int bounces;
    }

    static final class Trail {
        double ax, ay, az, dx, dy, dz, len, age, life;
    }

    final Ui ui;
    final Hud hud = new Hud();
    volatile World world;
    WorldStore store;
    long worldSeed;
    String worldId;
    int startWave;
    private long pendSeed;
    private String pendId;
    private int pendWave;
    final Gun gun = new Gun();
    final Sfx sfx = new Sfx();
    final Net net;
    final Settings set;
    final Random rng = new Random();
    GLSurfaceView view;

    // input from the ui thread
    volatile double mvx, mvy;
    volatile boolean fireIn, reloadReq;
    volatile boolean jumpReq, diveReq, sprintReq, slideReq, crawlReq, paused;
    private double lyaw, lpitch;

    // player
    double cx, cy = 1.7, cz, yaw, pitch;
    double hp = 100;
    int mag = 12, res = 60, kills, wave;
    double bhCur = .7;
    int state = MENU, role = SOLO, myIdx;
    String netId = "host";
    String roomCode = "";
    boolean pDead, firing;
    int fired;

    // jumping, diving, crawling, sliding
    static final double SLIDE_TIME = .85;
    double jy, vy, hvx, hvz, dvx, dvz, slideT, slideV0, crouchOff, dvPitch, camRoll, crawlPh, fovCur = 70;
    double wDive, wSlide, wCrawl, wRun;
    boolean grounded = true, diving, sprint, crawling, airFast;
    double vaultT, vaultTop;
    double[] vaultObs;
    private int frames;
    private long fpsT;

    // weapon and timers
    static final int VOID_WAVE = 3;
    static final double WAVE_TIME = 60, AWAKE_TIME = 7;
    double waveClock, awakeT = -1;
    int bhSnd, spawnTotal;
    double spT, waveT, cd, rl, flash, bob, hurtT, RT = 1.75, dryT, netT;
    int rlEv, spawnLeft;
    boolean rlEmpty;
    double sl = 9, spr, spMv, kz, kzv, kr, krv, kc, kcv, ky, kyv, kro, krov;

    // lighting
    double ambI = .9, flickT, lightWait = 60 + Math.random() * 540, lightClock = -1, thunderT, boltI;
    boolean thunderDone;
    private static final float[] NOREF = {0f, 0f, 0f};
    private static final float[] FAR = {60f, 100f, 150f, 220f};
    private int frameN;
    private final World.Light[] pickL = new World.Light[7];
    private final double[] pickD = new double[7];
    private final float[] prevVp = new float[16];
    private boolean prevVpSet;
    private double weatherT, rainSent = -1, rainClock;
    int bgHex = 0x070b10;
    final ArrayList<Trail> trails = new ArrayList<>();
    private World skyWorld;
    private float[] puddleSpots = new float[0];
    private int stepNo;
    private double ambT = 20;
    private static final double TRACER_SPEED = 380;
    private final Geo trailGeo = Geo.plane(1, 1);
    private final Mat mTracer = glowMat(0xffe2a8);
    private final Mat mGlint = glowMat(0xa8c4ff);

    // things in the world
    final ArrayList<Zombie> zs = new ArrayList<>();
    final java.util.HashMap<Integer, Zombie> zmap = new java.util.HashMap<>();
    final LinkedHashMap<String, Remote> pl = new LinkedHashMap<>();
    final ArrayList<Debris> deb = new ArrayList<>();
    final ArrayList<Blood> blood = new ArrayList<>();
    int zid;

    private final Node bloodNode = new Node();
    private final Mat mRunSkin = Mat.color(0xc87060).emissive(0x401008, 1);
    private final Mat mRunShirt = Mat.color(0x6a1818);
    private final Mat mBruteShirt = Mat.color(0x34344a);
    private final Mat mBruteSkin = Mat.color(0x7a9a6a);
    private final Mat mVoidSkin = Mat.color(0x2a1048).emissive(0x7a30ff, .9);
    private final Mat mVoidShirt = Mat.color(0x180a30).emissive(0x4a1a99, .8);
    private final Mat mTitanSkin = Mat.color(0x3a0a0a).emissive(0xff4a1a, .9);
    private final Mat mTitanShirt = Mat.color(0x1a0808).emissive(0xaa2a08, .7);
    private final Mat mBrass = Mat.color(0xc8a040).emissive(0x3a2c08, 1);
    private int vw = 1, vh = 1;
    private long last;

    Game(Ui ui, Settings set) {
        worldSeed = Math.abs(new java.util.Random().nextLong() % 1000000000L);
        world = new World(worldSeed);
        this.ui = ui;
        this.set = set;
        this.net = new Net(this);
        bloodNode.parts.add(Node.box(.08, .08, .08, 0, 0, 0, Mat.basic(0x8a0808)));
    }

    private void post(Runnable r) {
        if (view != null) view.queueEvent(r);
        else r.run();
    }

    void release() {
        sfx.release();
        net.close();
    }

    synchronized void addLook(double dyaw, double dpitch) {
        lyaw += dyaw;
        lpitch += dpitch;
    }

    /* ---- renderer ---- */

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Gfx.init();
        Sky.init();
        Bolt.init();
        Post.init();
        Upscaler.init();
        applyGl();
        last = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        vw = Math.max(1, w);
        vh = Math.max(1, h);
        GLES20.glViewport(0, 0, w, h);
        Gfx.setRes(vw, vh);
        Sky.resize(vw, vh);
        Post.resize(vw, vh);
        Upscaler.resize(vw, vh);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        double dt = last == 0 ? .016 : Math.min((now - last) / 1e9, .05);
        last = now;
        if (state == MENU || (paused && role == SOLO)) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (bhSnd == 1 && state == PLAY) {
                ui.bhSound(2, 0);
                bhSnd = 2;
            } else if (bhSnd != 0 && state != PLAY) {
                ui.bhSound(0, 0);
                bhSnd = 0;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        if (wave < VOID_WAVE) bhCur += (bhSize(wave) - bhCur) * Math.min(1, dt * .5);
        weather(dt);
        boolean running = state == PLAY && !(paused && role == SOLO);
        if (set.nativeRes) Upscaler.tick(dt, set.srDynamic);
        if (running) update(dt);
        else tickLights(dt);
        if (bhSnd != 0) {
            if (state != PLAY) {
                ui.bhSound(0, 0);
                bhSnd = 0;
            } else if (running && bhSnd == 2) {
                ui.bhSound(3, 0);
                bhSnd = 1;
            } else if (!running && bhSnd == 1) {
                ui.bhSound(2, 0);
                bhSnd = 2;
            }
        }
        render();
        ui.redraw();
        frames++;
        if (now - fpsT >= 1000000000L) {
            hud.fps = frames;
            frames = 0;
            fpsT = now;
        }
    }

    /** vsync on or off, called on the gl thread */
    void applyGl() {
        try {
            EGL14.eglSwapInterval(EGL14.eglGetCurrentDisplay(), set.vsync ? 1 : 0);
        } catch (Exception e) {
        }
    }

    private double awakePulse() {
        if (awakeT < 0) return 0;
        double s = Math.sin(Math.PI * Math.min(1, awakeT / AWAKE_TIME));
        return s * s;
    }

    private float[] camWorld() {
        double sh = awakePulse();
        return Xf.makeYXZR(cx, cy, cz, pitch + kc + dvPitch + Math.sin(awakeT * 41) * .008 * sh, yaw + ky,
                camRoll + Math.sin(awakeT * 29 + 1) * .012 * sh);
    }

    private void render() {
        float[] cw = camWorld();
        float[] viewM = new float[16];
        Matrix.invertM(viewM, 0, cw, 0);
        float[] proj = new float[16];
        Matrix.perspectiveM(proj, 0, (float) fovCur, (float) vw / vh, .1f, FAR[Math.max(0, Math.min(3, set.renderDist))] + 20f);
        float[] vpU = Xf.mul(proj, viewM);
        boolean sr = set.nativeRes && Upscaler.ok;
        int sw = vw, sh = vh;
        float[] vp = vpU;
        if (sr) {
            sw = Math.max(64, Math.round(vw * Upscaler.scale));
            sh = Math.max(36, Math.round(vh * Upscaler.scale));
            float[] jt = Upscaler.jitter(sw, sh);
            float[] jm = Xf.ident();
            jm[12] = jt[0];
            jm[13] = jt[1];
            vp = Xf.mul(jm, vpU);
        }
        Gfx.setRes(sw, sh);

        float[] plPos = new float[24], plCol = new float[24], plRange = new float[8];
        int n = 0;
        int cnt = 0;
        for (World.Light l : world.lights) {
            double dx = l.x - cx, dz = l.z - cz, dd = dx * dx + dz * dz;
            int k = cnt;
            if (cnt < 7) cnt++;
            else if (dd >= pickD[6]) continue;
            else k = 6;
            while (k > 0 && pickD[k - 1] > dd) {
                pickD[k] = pickD[k - 1];
                pickL[k] = pickL[k - 1];
                k--;
            }
            pickD[k] = dd;
            pickL[k] = l;
        }
        for (int i = 0; i < cnt; i++) {
            World.Light l = pickL[i];
            plPos[n * 3] = (float) l.x;
            plPos[n * 3 + 1] = (float) l.y;
            plPos[n * 3 + 2] = (float) l.z;
            plCol[n * 3] = (float) (l.col[0] * l.inten);
            plCol[n * 3 + 1] = (float) (l.col[1] * l.inten);
            plCol[n * 3 + 2] = (float) (l.col[2] * l.inten);
            plRange[n] = (float) l.range;
            n++;
        }
        float[] mp = Xf.point(cw, .2, -.16, -.9);
        float[] mc = Mat.rgb(0xffaa44);
        float mi = flash > 0 ? 2.5f : 0f;
        plPos[n * 3] = mp[0];
        plPos[n * 3 + 1] = mp[1];
        plPos[n * 3 + 2] = mp[2];
        plCol[n * 3] = mc[0] * mi;
        plCol[n * 3 + 1] = mc[1] * mi;
        plCol[n * 3 + 2] = mc[2] * mi;
        plRange[n] = 8f;
        n++;

        float[] ac = Mat.rgb(0x3a4a66);
        float[] amb = {(float) (ac[0] * ambI), (float) (ac[1] * ambI), (float) (ac[2] * ambI)};
        float[] fwd = Xf.dir(cw, 0, 0, -1);

        float[] camPos = {(float) cx, (float) cy, (float) cz};
        Gfx.flash = (float) Math.max(awakePulse() * .22, Math.max(0, Math.min(1, (ambI - .9) / 1.5)));
        Gfx.bh = (float) (bhCur * (1 + .6 * awakePulse()));
        Gfx.frame(vp, camPos, amb, plPos, plCol, plRange, n);

        frameN++;
        float farD = FAR[Math.max(0, Math.min(3, set.renderDist))];
        Gfx.drawDist = 70f;
        boolean refl = Sky.reflOk && pitch + kc + dvPitch < .8;
        Gfx.reflOn = refl;
        if (refl && (frameN & 1) == 0) {
            float[] flipM = Xf.ident();
            flipM[5] = -1f;
            float[] vpR = Xf.mul(vp, flipM);
            Sky.beginReflect(0);
            Gfx.setVP(vpR);
            Gfx.setRefl(true, 0, 1, 0, 0, .12f);
            world.draw(false);
            drawEntities(camPos, 28);
            Sky.drawSky(vp, camPos, new float[]{0f, 1f, 0f});
            Bolt.draw(vpR, (float) boltI);
            Gfx.setRefl(false, 0, 1, 0, 0, 0);
            Gfx.setVP(vp);
            Sky.endReflect(0, sw, sh);
        }

        float[] mir = Sky.reflOk ? pickMirror(camPos, fwd) : null;
        Gfx.setMirror(mir != null, mir);
        if (mir != null && (frameN & 1) == 1) {
            float[] mm = Xf.ident();
            mm[0] = 1 - 2 * mir[0] * mir[0];
            mm[2] = -2 * mir[0] * mir[2];
            mm[8] = mm[2];
            mm[10] = 1 - 2 * mir[2] * mir[2];
            mm[12] = 2 * mir[3] * mir[0];
            mm[14] = 2 * mir[3] * mir[2];
            float[] vpM = Xf.mul(vp, mm);
            Sky.beginReflect(1);
            Gfx.setVP(vpM);
            Gfx.setRefl(true, mir[0], 0, mir[2], mir[3], .25f);
            world.draw(false);
            drawEntities(camPos, 28);
            Sky.drawSky(vp, camPos, new float[]{mir[0], 0f, mir[2]});
            Bolt.draw(vpM, (float) boltI);
            Gfx.setRefl(false, 0, 1, 0, 0, 0);
            Gfx.setVP(vp);
            Sky.endReflect(1, sw, sh);
        }

        Gfx.drawDist = farD;
        boolean mb = !sr && set.motionBlur && Post.ok;
        if (sr) Upscaler.beginScene(sw, sh);
        else if (mb) Post.begin();
        float[] bg = Mat.rgb(bgHex);
        GLES20.glDepthMask(true);
        GLES20.glClearColor(bg[0], bg[1], bg[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        world.draw(false);
        drawEntities(camPos, farD + 5);
        if (!mb && !sr) gun.gun.draw(cw, false);

        if (skyWorld != world) {
            skyWorld = world;
            try {
                Sky.setShelter(world.shelterMap());
                puddleSpots = world.puddleSpots();
                Sky.setPuddles(puddleSpots);
            } catch (RuntimeException e) {
                Sky.setShelter(null);
                puddleSpots = new float[0];
                Sky.setPuddles(null);
            }
        }
        Sky.drawSky(vp, camPos, NOREF);
        Bolt.draw(vp, (float) boltI);
        world.draw(true);
        Sky.drawPuddles(vp, camPos);
        drawEntitiesBlend();
        drawTrails(camPos);
        Sky.drawRain(vp, camPos);
        if (sr) {
            if (!prevVpSet) {
                System.arraycopy(vpU, 0, prevVp, 0, 16);
                prevVpSet = true;
            }
            Upscaler.finish(vpU, prevVp, camPos, sw, sh, set.motionBlur);
            Gfx.setVP(vpU);
            gun.gun.draw(cw, false);
        }
        if (mb) {
            if (!prevVpSet) {
                System.arraycopy(vp, 0, prevVp, 0, 16);
                prevVpSet = true;
            }
            Post.end(vp, prevVp, camPos);
            gun.gun.draw(cw, false);
        }
        gun.gun.draw(cw, true);
        System.arraycopy(vpU, 0, prevVp, 0, 16);
        prevVpSet = true;
    }

    private float[] pickMirror(float[] cam, float[] fwd) {
        double best = 1e9;
        float[] r = null;
        for (double[] p : world.glass) {
            double side = (cam[0] - p[0]) * p[5] + (cam[2] - p[2]) * p[6];
            if (Math.abs(side) < .35) continue;
            double dx = p[0] - cam[0], dy = p[1] - cam[1], dz = p[2] - cam[2];
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 26 || dist < .01) continue;
            if ((dx * fwd[0] + dy * fwd[1] + dz * fwd[2]) / dist < .35) continue;
            double score = p[7] == 2 ? dist * .55 : dist;
            if (score >= best) continue;
            best = score;
            double sg = side > 0 ? 1 : -1;
            r = new float[]{(float) (sg * p[5]), 0f, (float) (sg * p[6]), (float) (sg * (p[5] * p[0] + p[6] * p[2]))};
        }
        return r;
    }

    /** the host (or a solo player) picks the strike and tells everybody, so it lands for all players at once */
    private void hostStrike() {
        float[] fw = Xf.dir(camWorld(), 0, 0, -1);
        double hl = Math.hypot(fw[0], fw[2]);
        double dx = r2(hl > .01 ? fw[0] / hl : -Math.sin(yaw)), dz = r2(hl > .01 ? fw[2] / hl : -Math.cos(yaw));
        double rx = role == SOLO ? r2(cx) : 0, rz = role == SOLO ? r2(cz) : 0;
        long seed = rng.nextLong();
        double tt = r2(rr(.5, 3.8));
        bc(Net.obj("t", "strike", "seed", seed, "rx", rx, "rz", rz, "dx", dx, "dz", dz, "tt", tt));
        startStrike(seed, rx, rz, dx, dz, tt);
    }

    private void startStrike(long seed, double rx, double rz, double dx, double dz, double tt) {
        lightClock = 0;
        Bolt.seed(seed);
        Bolt.strike(rx, rz, dx, dz);
        Gfx.boltDir[0] = (float) Bolt.hx;
        Gfx.boltDir[1] = 0f;
        Gfx.boltDir[2] = (float) Bolt.hz;
        thunderT = tt;
        thunderDone = false;
    }

    private static Mat glowMat(int hex) {
        Mat m = Mat.basic(hex);
        m.tex = new Tex(32, 4, false, new Tex.Painter() {
            @Override
            public void paint(android.graphics.Canvas c, int w, int h) {
                for (int i = 0; i < w; i++) {
                    double u = (i - (w - 1) / 2.0) / (w / 2.0);
                    Tex.P.setColor(Color.argb((int) (255 * Math.exp(-u * u * 5)), 255, 255, 255));
                    c.drawRect(i, 0, i + 1, h, Tex.P);
                }
            }
        });
        m.blend = true;
        m.additive = true;
        m.depthWrite = false;
        m.noFog = true;
        return m;
    }

    private void drawEntitiesBlend() {
        for (Remote q : pl.values()) {
            if (q.node != null) q.node.draw(Xf.ID, true);
        }
    }

    /** a flat ribbon between two points that always faces the camera */
    private void ribbon(Trail t, double s0, double s1, double width, Mat m, double alpha, float[] cam) {
        double mx = t.ax + t.dx * (s0 + s1) / 2, my = t.ay + t.dy * (s0 + s1) / 2, mz = t.az + t.dz * (s0 + s1) / 2;
        double tx = cam[0] - mx, ty = cam[1] - my, tz = cam[2] - mz;
        double rx = t.dy * tz - t.dz * ty, ry = t.dz * tx - t.dx * tz, rz = t.dx * ty - t.dy * tx;
        double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rl < 1e-6) return;
        rx /= rl;
        ry /= rl;
        rz /= rl;
        double nx = ry * t.dz - rz * t.dy, ny = rz * t.dx - rx * t.dz, nz = rx * t.dy - ry * t.dx;
        double len = s1 - s0;
        float[] mm = new float[16];
        mm[0] = (float) (rx * width);
        mm[1] = (float) (ry * width);
        mm[2] = (float) (rz * width);
        mm[4] = (float) (t.dx * len);
        mm[5] = (float) (t.dy * len);
        mm[6] = (float) (t.dz * len);
        mm[8] = (float) nx;
        mm[9] = (float) ny;
        mm[10] = (float) nz;
        mm[12] = (float) mx;
        mm[13] = (float) my;
        mm[14] = (float) mz;
        mm[15] = 1f;
        m.a = (float) alpha;
        Gfx.useMat(m);
        Gfx.draw(trailGeo, mm);
    }

    private void drawTrails(float[] cam) {
        for (Trail t : trails) {
            double head = Math.min(t.len, t.age * TRACER_SPEED), tail = Math.max(0, head - 6);
            if (head > tail + .05) ribbon(t, tail, head, .07, mTracer, .85, cam);
            double la = .2 * Math.max(0, 1 - t.age / t.life);
            if (la > .004) ribbon(t, 0, t.len, .03, mGlint, la, cam);
        }
    }

    private void addTrail(double sx, double sy, double sz, double ex, double ey, double ez) {
        double dx = ex - sx, dy = ey - sy, dz = ez - sz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < .5) return;
        Trail t = new Trail();
        t.ax = sx;
        t.ay = sy;
        t.az = sz;
        t.dx = dx / len;
        t.dy = dy / len;
        t.dz = dz / len;
        t.len = len;
        t.life = Math.max(.5, len / TRACER_SPEED + .15);
        if (trails.size() > 40) trails.remove(0);
        trails.add(t);
    }

    /** trail for somebody else's shot: starts near their gun, ends where the bullet stopped */
    private void addTrailFromEye(double ox, double oy, double oz, double dx, double dy, double dz,
                                 double ex, double ey, double ez) {
        double hl = Math.hypot(dx, dz);
        double rx = hl > .001 ? -dz / hl : 1, rz = hl > .001 ? dx / hl : 0;
        addTrail(ox + dx * .9 + rx * .2, oy + dy * .9 - .22, oz + dz * .9 + rz * .2, ex, ey, ez);
    }

    private double rayEnd(double ox, double oy, double oz, double dx, double dy, double dz) {
        double t = 70;
        double w = world.rayWalls(ox, oy, oz, dx, dy, dz);
        if (w >= 0 && w < t) t = w;
        if (dy < -1e-4) t = Math.min(t, -oy / dy);
        for (Zombie z : zs) {
            if (z.dead) continue;
            double[] r = hitZombie(z, ox, oy, oz, dx, dy, dz);
            if (r != null && r[0] < t) t = r[0];
        }
        return t;
    }

    private static JSONArray arr3(double a, double b, double c) {
        JSONArray r = new JSONArray();
        try {
            r.put(r2(a)).put(r2(b)).put(r2(c));
        } catch (JSONException e) {
            // nothing sensible to add
        }
        return r;
    }

    private void drawEntities(float[] cam, double maxD) {
        double m2 = maxD * maxD;
        for (Zombie z : zs) {
            double ex = z.x - cam[0], ez = z.z - cam[2];
            if (ex * ex + ez * ez > m2) continue;
            z.o.px = z.x;
            z.o.pz = z.z;
            z.o.ry = z.ry;
            z.o.draw(Xf.ID, false);
        }
        for (Remote q : pl.values()) {
            if (q.node != null) q.node.draw(Xf.ID, false);
        }
        for (Blood b : blood) {
            bloodNode.at(b.x, b.y, b.z);
            bloodNode.draw(Xf.ID, false);
        }
        for (Debris d : deb) d.n.draw(Xf.ID, false);
    }

    private double hash2(int i, int j) {
        long h = i * 374761393L + j * 668265263L + worldSeed * 1274126177L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h ^= h >> 16;
        return (h & 0xffff) / 65535.0;
    }

    private double vnoise2(double x, double y) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        double fx = x - i, fy = y - j;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash2(i, j), b = hash2(i + 1, j), c = hash2(i, j + 1), d = hash2(i + 1, j + 1);
        return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy;
    }

    private double rainAt(double x, double z, double t) {
        double n = vnoise2(x * .012 + t * .004, z * .012 - t * .003) * .7 + vnoise2(x * .03 - t * .01, z * .03 + t * .006) * .3;
        double u = Math.max(0, Math.min(1, (n - .38) / .34));
        return u * u * (3 - 2 * u);
    }

    private void weather(double dt) {
        weatherT += dt;
        double target = rainAt(cx, cz, weatherT);
        double k = Math.min(1, dt * .7);
        double r = Gfx.rain + (target - Gfx.rain) * k;
        Gfx.rain = (float) r;
        double w = Gfx.wet;
        w += r > w ? (r - w) * Math.min(1, dt * .15) : (r - w) * Math.min(1, dt * .02);
        Gfx.wet = (float) w;
        rainClock -= dt;
        if (rainClock <= 0 && Math.abs(r - rainSent) > .02) {
            rainClock = .25;
            rainSent = r;
            ui.rain((float) r);
        }
    }

    static double bhSize(int w) {
        return Math.min(4.2, .55 + .17 * w);
    }

    /* ---- starting and stopping ---- */

    private String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append("ABCDEFGHJKMNPQRSTUVWXYZ".charAt(rng.nextInt(23)));
        return sb.toString();
    }

    private void setWorld(long seed) {
        if (world != null && worldSeed == seed) return;
        worldSeed = seed;
        world = new World(seed);
        nav = null;
        navF.clear();
    }

    private void saveProgress() {
        if (role != CLIENT && store != null && worldId != null) store.update(worldId, wave, kills);
    }

    void startSolo(long seed, String id, int fromWave) {
        net.close();
        role = SOLO;
        myIdx = 0;
        netId = "host";
        hud.room = "";
        worldId = id;
        startWave = fromWave;
        setWorld(seed);
        startGame(false);
    }

    void startHost(long seed, String id, int fromWave) {
        pendSeed = seed;
        pendId = id;
        pendWave = fromWave;
        hostNow();
    }

    private void hostNow() {
        roomCode = newCode();
        net.host(roomCode);
    }

    volatile String myName = "Player";
    private String joinCode = "";

    void startJoin(String code) {
        joinCode = code;
        code = code == null ? "" : code.trim().toUpperCase();
        if (code.length() != 4) {
            ui.status(L.t("n_code"));
            return;
        }
        roomCode = code;
        net.join(code);
    }

    void again() {
        startWave = 0;
        ui.hideOver();
        bc(Net.obj("t", "restart"));
        reset(false);
    }

    private void startGame(boolean cli) {
        paused = false;
        ui.hideMenu();
        ui.hideOver();
        reset(cli);
    }

    private void reset(boolean cli) {
        zs.clear();
        zmap.clear();
        deb.clear();
        blood.clear();
        trails.clear();
        rl = 0;
        rlEv = 0;
        sl = 9;
        spr = 0;
        kz = kzv = kr = krv = kc = kcv = ky = kyv = kro = krov = 0;
        hp = 100;
        mag = 12;
        res = 60;
        kills = 0;
        wave = 0;
        cx = SPAWN_AT[myIdx % 4][0];
        cz = SPAWN_AT[myIdx % 4][1];
        cy = 1.7;
        yaw = pitch = 0;
        pDead = false;
        spawnLeft = 0;
        waveT = 0;
        waveClock = 0;
        awakeT = -1;
        ui.bhSound(0, 0);
        bhSnd = 0;
        hud.dead = false;
        resetPhysics();
        state = PLAY;
        hud.playing = true;
        if (!cli) {
            wave = Math.max(0, startWave - 1);
            nextWave();
        }
        updRoom();
    }

    private void showOver() {
        saveProgress();
        state = OVER;
        hud.playing = false;
        mvx = mvy = 0;
        fireIn = false;
        ui.showOver(L.t("h_wave") + " " + wave + "   ·   " + L.t("h_kills") + " " + kills, role != CLIENT);
    }

    private void clientLost() {
        if (role != CLIENT) return;
        state = OVER;
        hud.playing = false;
        mvx = mvy = 0;
        fireIn = false;
        ui.showOver(L.t("n_hostlost"), false);
    }

    private int nOther() {
        return pl.size();
    }

    private void updRoom() {
        hud.room = role == SOLO ? "" : L.t("h_room") + ": " + roomCode + " · " + (1 + nOther()) + "P";
    }

    private void ban(String t) {
        hud.banner = t;
        hud.banT = 2.4;
    }

    private void onWave(int w) {
        double a = bhSize(w - 1), b = bhSize(w);
        wave = w;
        saveProgress();
        ban(L.t("h_wave") + " " + wave);
        waveClock = 0;
        if (w >= VOID_WAVE) {
            if (Math.abs(bhCur - a) > .3) bhCur = a;
            if (w == VOID_WAVE) awakeT = 0;
            ui.bhSound(1, 0);
            bhSnd = 1;
        }
        if (a < 1.2 && b >= 1.2) ban("THE VOID GROWS  -  RUNNERS");
        else if (a < 1.9 && b >= 1.9) ban("THE VOID GROWS  -  BRUTES");
        else if (a < 2.6 && b >= 2.6) ban("THE VOID GROWS  -  VOIDLINGS");
        else if (a < 3.5 && b >= 3.5) ban("THE VOID GROWS  -  TITANS");
        if (w == VOID_WAVE) ban("THE VOID AWAKENS");
        if (wave > 1) {
            if (pDead) {
                pDead = false;
                hp = 50;
                hud.dead = false;
                cx = SPAWN_AT[myIdx % 4][0];
                cz = SPAWN_AT[myIdx % 4][1];
                cy = 1.7;
                resetPhysics();
            }
            hp = Math.min(100, hp + 20);
            res += 24;
        }
    }

    private void nextWave() {
        spawnLeft = (int) Math.round((4 + (wave + 1) * 3) * (1 + .7 * nOther()));
        spawnTotal = spawnLeft;
        onWave(wave + 1);
        bc(Net.obj("t", "wave", "w", wave));
    }

    /* ---- weapon ---- */

    private void reload() {
        if (rl > 0 || mag >= 12 || res <= 0) return;
        rlEmpty = mag <= 0;
        RT = rlEmpty ? 2.2 : 1.75;
        rl = RT;
        rlEv = 0;
    }

    private double[] rlPose(double p) {
        int i = 0;
        while (i < RLK.length - 2 && p > RLK[i + 1][0]) i++;
        double[] a = RLK[i], b = RLK[i + 1];
        double u = Math.max(0, Math.min(1, (p - a[0]) / (b[0] - a[0])));
        u = u * u * (3 - 2 * u);
        double[] r = new double[6];
        for (int j = 0; j < 6; j++) r[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * u;
        return r;
    }

    private double rr(double a, double b) {
        return a + rng.nextDouble() * (b - a);
    }

    private float[] gunMatrix() {
        Node g = gun.gun;
        return Xf.mul(camWorld(), Xf.make(g.px, g.py, g.pz, g.rx, g.ry, g.rz, g.s));
    }

    private void addDebris(Debris d) {
        if (deb.size() > 60) deb.remove(0);
        deb.add(d);
    }

    private void dropMag() {
        float[] mg = gunMatrix();
        float[] gr = Xf.mul(mg, Xf.make(0, -.025, .13, -.2, 0, 0, 1));
        float[] mm = Xf.mul(gr, Xf.make(0, gun.mag.py, -.037, 0, 0, 0, 1));
        float[] pos = Xf.point(mm, 0, 0, 0);
        float[] right = Xf.dir(camWorld(), 1, 0, 0);
        Debris d = new Debris();
        d.n = new Node();
        d.n.parts.add(Node.box(.042, .11, .07, 0, 0, 0, gun.mSt));
        d.n.at(pos[0], pos[1], pos[2]);
        d.n.ry = yaw + ky;
        d.n.rx = pitch + kc;
        d.vx = right[0] * .3;
        d.vy = right[1] * .3 - .4;
        d.vz = right[2] * .3;
        d.wx = rr(-3, 3);
        d.wy = rr(-2, 2);
        d.wz = rr(-3, 3);
        d.h = .055;
        d.b = .25;
        d.mag = true;
        addDebris(d);
    }

    private void ejectBrass() {
        float[] sm = Xf.mul(gunMatrix(), Xf.make(0, 0, gun.slide.pz, 0, 0, 0, 1));
        float[] pos = Xf.point(sm, .03, .046, .03);
        float[] v = Xf.dir(camWorld(), rr(1.2, 2), rr(1.2, 2.2), rr(.1, .7));
        Debris d = new Debris();
        d.n = new Node();
        d.n.parts.add(new Node.Part(Geo.cyl(.006, .006, .023, 6, false), mBrass, 0, 0, 0));
        d.n.at(pos[0], pos[1], pos[2]);
        d.vx = v[0];
        d.vy = v[1];
        d.vz = v[2];
        d.wx = rr(-25, 25);
        d.wy = rr(-25, 25);
        d.wz = rr(-25, 25);
        d.h = .006;
        d.b = .35;
        addDebris(d);
    }

    private void shoot() {
        mag--;
        flash = .06;
        cd = .3;
        sl = 0;
        sfx.tone(220, .18, Sfx.SAW, .25);
        sfx.noise(.12, .3, 3500);
        kzv += 2.4 + rng.nextDouble() * .4;
        krv += 4 + rng.nextDouble() * 1.2;
        kcv += .9 + rng.nextDouble() * .3;
        kyv += (rng.nextDouble() - .5) * .5;
        krov += (rng.nextDouble() - .5) * 2;
        pitch = Math.min(1.4, pitch + .0035);
        spr = Math.min(.02, spr + .006);
        ejectBrass();
        double sp = .004 + spr + spMv * .006;
        double nx = (rng.nextDouble() - .5) * 2 * sp, ny = (rng.nextDouble() - .5) * 2 * sp;
        double th = Math.tan(35 * PI / 180);
        float[] d = Xf.dir(camWorld(), nx * th * vw / vh, ny * th, -1);
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double dx = d[0] / l, dy = d[1] / l, dz = d[2] / l;
        fired++;
        double endT = rayEnd(cx, cy, cz, dx, dy, dz);
        double ex = cx + dx * endT, ey = cy + dy * endT, ez = cz + dz * endT;
        float[] mz = Xf.point(gunMatrix(), 0, .034, -.27);
        addTrail(mz[0], mz[1], mz[2], ex, ey, ez);
        if (role == HOST) bc(Net.obj("t", "trail", "id", "host", "o", arr3(cx, cy, cz), "e", arr3(ex, ey, ez)));
        if (role == CLIENT) {
            JSONArray o = new JSONArray(), dd = new JSONArray();
            try {
                o.put(r2(cx)).put(r2(cy)).put(r2(cz));
                dd.put(dx).put(dy).put(dz);
            } catch (JSONException e) {
                return;
            }
            net.sendG(Net.obj("t", "shot", "o", o, "d", dd), null);
        } else {
            resolveHit("host", cx, cy, cz, dx, dy, dz);
        }
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    /** returns {distance, head} or null */
    private double[] hitZombie(Zombie z, double ox, double oy, double oz, double dx, double dy, double dz) {
        double c = Math.cos(-z.ry), s = Math.sin(-z.ry);
        double px = ox - z.x, pz = oz - z.z;
        double lx = (px * c + pz * s) / z.scale, lz = (-px * s + pz * c) / z.scale, ly = oy / z.scale;
        double ldx = (dx * c + dz * s) / z.scale, ldz = (-dx * s + dz * c) / z.scale, ldy = dy / z.scale;
        double th = Hit.rayBox(lx, ly, lz, ldx, ldy, ldz, -.15, 1.5, -.15, .15, 1.8, .15);
        if (th >= 0) return new double[]{th, 1};
        double best = -1;
        double[][] boxes = {{-.25, .8, -.125, .25, 1.5, .125}, {-.22, 0, -.11, .22, .8, .11}, {-.42, 1.37, 0, .42, 1.53, .6}};
        for (double[] b : boxes) {
            double t = Hit.rayBox(lx, ly, lz, ldx, ldy, ldz, b[0], b[1], b[2], b[3], b[4], b[5]);
            if (t >= 0 && (best < 0 || t < best)) best = t;
        }
        return best < 0 ? null : new double[]{best, 0};
    }

    private void resolveHit(String by, double ox, double oy, double oz, double dx, double dy, double dz) {
        Zombie bz = null;
        double bt = -1;
        boolean head = false;
        for (Zombie z : zs) {
            if (z.dead) continue;
            double[] r = hitZombie(z, ox, oy, oz, dx, dy, dz);
            if (r != null && (bt < 0 || r[0] < bt)) {
                bt = r[0];
                bz = z;
                head = r[1] > 0;
            }
        }
        double w = world.rayWalls(ox, oy, oz, dx, dy, dz);
        if (bz == null || (w >= 0 && w < bt)) return;
        bz.hp -= head ? 3 : 1;
        double px = ox + dx * bt, py = oy + dy * bt, pz = oz + dz * bt;
        spawnBlood(px, py, pz);
        JSONArray p = new JSONArray();
        try {
            p.put(r2(px)).put(r2(py)).put(r2(pz));
        } catch (JSONException e) {
            return;
        }
        bc(Net.obj("t", "hit", "p", p));
        if (bz.hp <= 0) {
            bz.dead = true;
            bz.dt = 0;
            kills++;
            sfx.tone(90, .4, Sfx.SQUARE, .15);
            if (by.equals("host")) res += 3;
            else sendTo(by, Net.obj("t", "ammo", "n", 3));
        }
    }

    private void spawnBlood(double x, double y, double z) {
        for (int i = 0; i < 7; i++) {
            Blood b = new Blood();
            b.x = x;
            b.y = y;
            b.z = z;
            b.vx = (rng.nextDouble() - .5) * 3;
            b.vy = rng.nextDouble() * 2.5;
            b.vz = (rng.nextDouble() - .5) * 3;
            b.l = .6;
            blood.add(b);
        }
    }

    private void shotSound(Remote q) {
        if (q == null) return;
        double d = Math.hypot(q.x - cx, q.z - cz);
        if (d > 420) return;
        int v = 1 + rng.nextInt(3);
        float rate = .95f + rng.nextFloat() * .1f;
        if (d < 110) ui.sound("gunshot_close_player_" + v, (float) Math.max(.2, 1 - d / 110 * .75), rate);
        else ui.sound("gunshot_far_away_" + v, (float) Math.max(.1, .9 - (d - 110) / 310 * .8), rate);
    }

    private void remoteShot(String id, JSONObject m) {
        Remote q = pl.get(id);
        JSONArray o = m.optJSONArray("o"), d = m.optJSONArray("d");
        if (q == null || q.dead || o == null || d == null) return;
        double ox = o.optDouble(0), oy = o.optDouble(1), oz = o.optDouble(2);
        double dx = d.optDouble(0), dy = d.optDouble(1), dz = d.optDouble(2);
        double endT = rayEnd(ox, oy, oz, dx, dy, dz);
        resolveHit(id, ox, oy, oz, dx, dy, dz);
        q.flashT = .06;
        shotSound(q);
        double ex = ox + dx * endT, ey = oy + dy * endT, ez = oz + dz * endT;
        addTrailFromEye(ox, oy, oz, dx, dy, dz, ex, ey, ez);
        bc(Net.obj("t", "trail", "id", id, "o", arr3(ox, oy, oz), "e", arr3(ex, ey, ez)));
    }

    /* ---- zombies and players ---- */

    private int pickType() {
        double sz = bhSize(wave), r = rng.nextDouble();
        if (sz >= 3.5 && r < .07) return 4;
        if (sz >= 2.6 && r < .22) return 3;
        if (sz >= 1.9 && r < .4) return 2;
        if (sz >= 1.2 && r < .62) return 1;
        return 0;
    }

    private Zombie mkZombie(int type) {
        Zombie z = new Zombie();
        z.type = type;
        double[] mul = {1, .92, 1.45, 1.25, 2.1};
        z.scale = (.95 + rng.nextDouble() * .15) * mul[type];
        z.hp = 3 + Math.floor(wave / 3.0);
        z.t = rng.nextDouble() * 6;
        z.sp = 1.1 + wave * .12 + rng.nextDouble() * .5;
        Mat shirt = world.mShirt, skin = world.mSkin;
        if (type == 1) {
            z.hp = 2 + Math.floor(wave / 4.0);
            z.sp = Math.min(5.5, z.sp * 1.9);
            z.dmg = 8;
            shirt = mRunShirt;
            skin = mRunSkin;
        } else if (type == 2) {
            z.hp = 12 + wave;
            z.sp *= .75;
            z.dmg = 22;
            shirt = mBruteShirt;
            skin = mBruteSkin;
        } else if (type == 3) {
            z.hp = 8 + Math.floor(wave * .8);
            z.sp = Math.min(4.5, z.sp * 1.25);
            z.dmg = 16;
            shirt = mVoidShirt;
            skin = mVoidSkin;
        } else if (type == 4) {
            z.hp = 40 + wave * 2;
            z.sp *= .7;
            z.dmg = 35;
            shirt = mTitanShirt;
            skin = mTitanSkin;
        }
        z.o = new Node();
        z.o.s = z.scale;
        z.g = new Node();
        z.o.kids.add(z.g);
        z.g.parts.add(Node.box(.5, .7, .25, 0, 1.15, 0, shirt));
        z.g.parts.add(Node.box(.3, .3, .3, 0, 1.65, 0, skin));
        z.aL = limb(z.g, -.35, 1.45, Node.box(.14, .6, .14, 0, -.28, 0, skin));
        z.aR = limb(z.g, .35, 1.45, Node.box(.14, .6, .14, 0, -.28, 0, skin));
        z.aL.rx = z.aR.rx = -1.4;
        z.lL = limb(z.g, -.13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        z.lR = limb(z.g, .13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        return z;
    }

    private Node limb(Node parent, double x, double y, Node.Part part) {
        Node n = new Node().at(x, y, 0);
        n.parts.add(part);
        parent.kids.add(n);
        return n;
    }

    private void mkAvatar(Remote q) {
        Mat shirt = Mat.color(AVC[(q.idx) % 4]);
        Mat skin = Mat.color(0xc9a58a);
        Node root = new Node();
        Node g = new Node();
        root.kids.add(g);
        g.parts.add(Node.box(.5, .7, .25, 0, 1.15, 0, shirt));
        g.parts.add(Node.box(.28, .28, .28, 0, 1.65, 0, skin));
        g.parts.add(Node.box(.14, .6, .14, -.35, 1.15, .05, skin));
        g.parts.add(Node.box(.14, .14, .6, .3, 1.3, .3, skin));
        g.parts.add(Node.box(.06, .1, .3, .3, 1.34, .6, world.mGun));
        q.lL = limb(g, -.13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        q.lR = limb(g, .13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        Node mf = new Node().at(.3, 1.34, .9);
        mf.parts.add(new Node.Part(Geo.plane(.28, .28), gun.mFlash, 0, 0, 0));
        Node.Part f2 = new Node.Part(Geo.plane(.28, .28), gun.mFlash, 0, 0, 0);
        f2.rz = PI / 4;
        mf.parts.add(f2);
        Node.Part f3 = new Node.Part(Geo.plane(.28, .28), gun.mFlash, 0, 0, 0);
        f3.ry = PI / 2;
        mf.parts.add(f3);
        mf.visible = false;
        g.kids.add(mf);
        q.flashN = mf;
        root.at(q.x, 0, q.z);
        q.node = root;
        q.inner = g;
    }

    private List<Tgt> allPl() {
        ArrayList<Tgt> a = new ArrayList<>();
        if (!pDead) a.add(new Tgt("host", cx, cz));
        for (Remote q : pl.values()) if (!q.dead) a.add(new Tgt(q.id, q.x, q.z));
        return a;
    }

    private int alive() {
        int n = 0;
        for (Zombie z : zs) if (!z.dead) n++;
        return n;
    }

    private void spawn() {
        Zombie z = mkZombie(pickType());
        z.id = ++zid;
        zmap.put(z.id, z);
        List<Tgt> l = allPl();
        Tgt bp = l.isEmpty() ? new Tgt("", 0, 0) : l.get(rng.nextInt(l.size()));
        double a = rng.nextDouble() * 6.28, d = 26 + rng.nextDouble() * 10;
        z.x = Math.max(-World.HALF + 3, Math.min(World.HALF - 3, bp.x + Math.cos(a) * d));
        z.z = Math.max(-World.HALF + 3, Math.min(World.HALF - 3, bp.z + Math.sin(a) * d));
        for (int k = 0; k < 30 && (!world.freeAt(z.x, z.z) || tooClose(l, z)); k++) {
            z.x = (rng.nextDouble() - .5) * 86;
            z.z = (rng.nextDouble() - .5) * 86;
        }
        zs.add(z);
    }

    private boolean tooClose(List<Tgt> l, Zombie z) {
        for (Tgt t : l) if (Math.hypot(z.x - t.x, z.z - t.z) < 18) return true;
        return false;
    }

    private void collide(double[] p, double r, double feet) {
        for (double[] o : world.obs) {
            if (o[4] <= feet + .35 || o == vaultObs) continue;
            double cxx = Math.max(o[0] - o[2] / 2, Math.min(p[0], o[0] + o[2] / 2));
            double czz = Math.max(o[1] - o[3] / 2, Math.min(p[1], o[1] + o[3] / 2));
            double dx = p[0] - cxx, dz = p[1] - czz, d = Math.hypot(dx, dz);
            if (d < r) {
                if (d < 1e-4) {
                    dx = 1;
                    dz = 0;
                    d = 1;
                }
                p[0] += dx / d * (r - d);
                p[1] += dz / d * (r - d);
            }
        }
    }

    /* ---- per frame logic ---- */

    private void tickLights(double dt) {
        flickT -= dt;
        if (flickT <= 0) {
            flickT = .09;
            for (World.Light l : world.lights) if (l.flick) l.inten = 1 + rng.nextDouble() * .7;
        }
        if (state == PLAY && lightClock < 0 && role != CLIENT) {
            lightWait -= dt;
            if (lightWait <= 0) hostStrike();
        }
        if (lightClock >= 0) {
            double t = lightClock;
            lightClock += dt;
            double f;
            if (t < .07) f = 1;
            else if (t < .15) f = .08;
            else if (t < .30) f = .95;
            else if (t < .40) f = .15;
            else if (t < .52) f = .6;
            else f = .6 * Math.exp(-(t - .52) * 4.5);
            boltI = f;
            ambI = .9 + 1.5 * f;
            bgHex = f > .5 ? 0x2a3a55 : 0x070b10;
            if (!thunderDone && t >= thunderT) {
                thunderDone = true;
                if (thunderT < 1.3) ui.thunder(0, 1f);
                else if (thunderT < 2.6) ui.thunder(1, .85f);
                else ui.thunder(2, .7f);
            }
            if (t > Math.max(2.2, thunderT + .1)) {
                lightClock = -1;
                boltI = 0;
                ambI = .9;
                bgHex = 0x070b10;
                lightWait = rr(60, 600);
                Bolt.stop();
            }
        }
    }

    private void update(double dt) {
        double ly, lp;
        synchronized (this) {
            ly = lyaw;
            lp = lpitch;
            lyaw = lpitch = 0;
        }
        yaw += ly;
        pitch = Math.max(-1.4, Math.min(1.4, pitch + lp));
        firing = fireIn && !pDead;
        if (reloadReq) {
            reloadReq = false;
            reload();
        }
        tickLights(dt);

        if (sprintReq) {
            sprintReq = false;
            sprint = !sprint;
        }
        double stick = Math.hypot(mvx, mvy);
        if (jumpReq) {
            jumpReq = false;
            if (!pDead) {
                if (crawling) {
                    crawling = false;
                } else if (grounded && !diving && vaultT <= 0) {
                    vy = 5.6;
                    grounded = false;
                    slideT = 0;
                    airFast = stick > .1 && (sprint || stick > .95);
                    if (airFast) {
                        hvx *= 1.08;
                        hvz *= 1.08;
                    }
                }
            }
        }
        if (crawlReq) {
            crawlReq = false;
            toggleCrawl();
        }
        if (diveReq) {
            diveReq = false;
            startDive();
        }
        if (slideReq) {
            slideReq = false;
            startSlide();
        }

        double sn = Math.sin(yaw), cs = Math.cos(yaw);
        boolean free = !diving && slideT <= 0 && vaultT <= 0;
        double fx = mvx, fz = -mvy;
        if (pDead) fx = fz = 0;
        double L = Math.hypot(fx, fz);
        if (L > 1) {
            fx /= L;
            fz /= L;
        }
        boolean fast = sprint || (set.autoRun && stick > .95);
        double sp = crawling ? (fast ? 3.0 : 1.6) : (fast ? 6.5 : 4);
        if (!grounded && airFast) sp *= 1.08;
        boolean moving = Math.hypot(fx, fz) > .1 && free && grounded;
        if (free) {
            double tx = (fx * cs - fz * sn) * sp, tz = (-fx * sn - fz * cs) * sp;
            double k = Math.min(1, (grounded ? (crawling ? 9 : 60) : 2.5) * dt);
            hvx += (tx - hvx) * k;
            hvz += (tz - hvz) * k;
        } else if (diving) {
            double near = jy - groundAt(cx, cz, .3, jy);
            double f = Math.exp(-(vy < 0 && near < .9 ? 3.2 : .15) * dt);
            hvx *= f;
            hvz *= f;
        } else if (vaultT > 0) {
            hvx = dvx * 6.3;
            hvz = dvz * 6.3;
            vaultT -= dt;
            if (vaultT <= 0) endVault();
        } else {
            slideT -= dt;
            double p = Math.max(0, Math.min(1, 1 - slideT / SLIDE_TIME));
            double ss = slideV0 * (1 - .6 * p * p);
            hvx = dvx * ss;
            hvz = dvz * ss;
            if (slideT <= 0) slideT = 0;
        }
        cx += hvx * dt;
        cz += hvz * dt;
        cx = Math.max(-World.HALF + 1, Math.min(World.HALF - 1, cx));
        cz = Math.max(-World.HALF + 1, Math.min(World.HALF - 1, cz));
        double[] pp = {cx, cz};
        collide(pp, .5, jy);
        cx = pp[0];
        cz = pp[1];

        double gnd = groundAt(cx, cz, .3, jy);
        if (vaultT > 0) {
            jy += (vaultTop - jy) * Math.min(1, dt * 16);
            vy = 0;
            grounded = true;
        } else if (!grounded || jy > gnd + .001 || vy > 0) {
            vy -= 16 * dt;
            jy += vy * dt;
            if (jy <= gnd && vy <= 0) {
                double impact = -vy;
                jy = gnd;
                vy = 0;
                if (!grounded) land(impact);
                grounded = true;
            } else {
                grounded = false;
            }
        } else {
            jy = gnd;
            vy = 0;
            grounded = true;
        }

        double lowTarget = diving ? .55 : crawling ? .95 : vaultT > 0 ? .85 : slideT > 0 ? .8 : 0;
        crouchOff += (lowTarget - crouchOff) * Math.min(1, dt * (lowTarget > crouchOff ? 14 : 6));
        double pt = diving ? -.45 : vaultT > 0 ? -.14 : slideT > 0 ? -.1 : 0;
        dvPitch += (pt - dvPitch) * Math.min(1, dt * 10);
        if (crawling && Math.hypot(fx, fz) > .1) crawlPh += dt * (fast ? 9 : 5.5);
        double rt = vaultT > 0 ? -.3 : slideT > 0 ? -.16 : diving ? .1 : crawling ? Math.sin(crawlPh) * .035 : 0;
        camRoll += (rt - camRoll) * Math.min(1, dt * 9);
        wDive += ((diving ? 1 : 0) - wDive) * Math.min(1, dt * 12);
        wSlide += (((slideT > 0 || vaultT > 0) ? 1 : 0) - wSlide) * Math.min(1, dt * 12);
        wCrawl += ((crawling ? 1 : 0) - wCrawl) * Math.min(1, dt * 10);
        boolean runPose = moving && fast && !crawling && !diving && slideT <= 0 && vaultT <= 0 && !firing;
        wRun += ((runPose ? 1 : 0) - wRun) * Math.min(1, dt * 8);
        boolean runFov = (moving && fast && !crawling) || (!grounded && airFast && !diving);
        double fovT = 70 + (runFov ? 9 : 0) + (diving ? 5 : 0) + ((slideT > 0 || vaultT > 0) ? 7 : 0);
        fovCur += (fovT - fovCur) * Math.min(1, dt * 6);
        if (moving && !crawling) bob += dt * sp * 1.6;
        int stepIdx = (int) Math.floor((bob + Math.PI / 2) / (2 * Math.PI));
        if (stepIdx != stepNo) {
            if (moving && !crawling) footstep(fast);
            stepNo = stepIdx;
        }
        cy = 1.7 + jy - crouchOff + (moving && !crawling ? Math.sin(bob) * (fast ? .095 : .072) : 0)
                + (crawling && Math.hypot(fx, fz) > .1 ? Math.sin(crawlPh * 2) * .015 : 0);

        kzv += (-320 * kz - 24 * kzv) * dt;
        kz += kzv * dt;
        krv += (-260 * kr - 20 * krv) * dt;
        kr += krv * dt;
        kcv += (-150 * kc - 14 * kcv) * dt;
        kc += kcv * dt;
        kyv += (-150 * ky - 14 * kyv) * dt;
        ky += kyv * dt;
        krov += (-200 * kro - 16 * krov) * dt;
        kro += krov * dt;
        spr = Math.max(0, spr - dt * .03);
        spMv = moving ? 1 : 0;
        cd -= dt;
        flash -= dt;
        sl += dt;
        dryT -= dt;

        double gx = 0, gy = 0, gz = 0, grx = 0, gry = 0, grz = 0, dr = 0, slp, lhy = 0, lhz = 0;
        boolean magVis = true;
        if (rl > 0) {
            rl -= dt;
            double p = Math.max(0, Math.min(1, 1 - rl / RT));
            double hb = p < .1 ? p / .1 : p < .66 ? 1 : p < .78 ? (.78 - p) / .12 : 0;
            lhy = -.09 * hb;
            lhz = .03 * hb;
            double[] q = rlPose(p);
            gx = q[0];
            gy = q[1];
            gz = q[2];
            grx = q[3];
            gry = q[4];
            grz = q[5];
            if (p >= .24 && (rlEv & 1) == 0) {
                rlEv |= 1;
                dropMag();
                sfx.noise(.03, .25, 2500);
                sfx.tone(500, .04, Sfx.SQUARE, .1);
            }
            if (p >= .62 && (rlEv & 2) == 0) {
                rlEv |= 2;
                sfx.noise(.04, .3, 1800);
                sfx.tone(160, .08, Sfx.SQUARE, .2);
            }
            if (p >= .68 && (rlEv & 4) == 0) {
                rlEv |= 4;
                int n = Math.min(12 - mag, res);
                mag += n;
                res -= n;
                kzv += .5;
                krv += 1.2;
                sfx.noise(.05, .35, 1500);
                sfx.tone(120, .1, Sfx.TRI, .2);
            }
            if (rlEmpty && p >= .8 && (rlEv & 8) == 0) {
                rlEv |= 8;
                kzv += 1;
                sfx.noise(.05, .4, 4000);
                sfx.tone(700, .04, Sfx.SQUARE, .12);
            }
            magVis = p < .24 || p >= .46;
            if (p >= .46 && p < .62) dr = 1 - (p - .46) / .16;
            slp = rlEmpty ? (p < .8 ? 1 : Math.max(0, 1 - (p - .8) / .05)) : 0;
        } else {
            slp = mag <= 0 ? 1 : (sl < .035 ? sl / .035 : sl < .1 ? 1 - (sl - .035) / .065 : 0);
        }
        double rs = Math.sin(bob), rc = Math.cos(bob);
        gx += wRun * (.07 + Math.sin(bob * .5) * .05);
        gy += wRun * (-.02 + Math.abs(rc) * .05);
        gz += wRun * (.10 + Math.sin(bob * 2) * .02);
        grx += wRun * (.95 + Math.cos(bob * 2) * .09);
        gry += wRun * .35;
        grz += wRun * (-.12 + rs * .10);
        gun.lh.py = lhy;
        gun.lh.pz = lhz;
        double c1 = Math.sin(crawlPh), c2 = Math.cos(crawlPh);
        gx += wDive * .04 + wSlide * .10 + wCrawl * (.03 + c1 * .05);
        gy += wDive * -.08 + wSlide * -.10 + wCrawl * (-.06 + Math.abs(c2) * .02);
        gz += wDive * .12 + wSlide * .02 + wCrawl * .05;
        grx += wDive * -.45 + wSlide * -.18 + wCrawl * -.25;
        gry += wDive * .25 + wSlide * .55 + wCrawl * (.10 + c1 * .10);
        grz += wDive * -.15 + wSlide * -.45 + wCrawl * c1 * .12;
        double sb = .075 * slp;
        gun.slide.pz = sb;
        gun.ham.rx = .45 + sb * 4;
        gun.flash.visible = flash > 0;
        if (flash > 0) gun.flash.rz = rng.nextDouble() * 6.28;
        gun.mag.visible = magVis;
        gun.mag.py = -.14 - dr * dr * (3 - 2 * dr) * .3;
        Node g = gun.gun;
        g.px = .17 + (moving ? Math.sin(bob * .5) * .018 : 0) + gx;
        g.py = -.10 + (moving ? Math.abs(Math.cos(bob)) * .018 : 0) + gy;
        g.pz = -.45 + kz + gz;
        g.rx = kr + grx;
        g.ry = .07 + gry + kro * .3;
        g.rz = grz + kro;

        if (firing && !pDead && cd <= 0 && rl <= 0) {
            if (mag > 0) {
                shoot();
            } else {
                if (dryT <= 0) {
                    dryT = .35;
                    sfx.tone(900, .03, Sfx.SQUARE, .08);
                }
                reload();
            }
        }

        updateDebris(dt);

        ambT -= dt;
        if (wave >= 2 && ambT <= 0) {
            ambT = 28 + rng.nextDouble() * 32;
            ui.sound("black_hole_ambient_rumble", (float) Math.min(.85, .3 + .2 * bhCur), 1f);
        }
        if (awakeT >= 0) {
            awakeT += dt;
            if (awakeT > AWAKE_TIME) awakeT = -1;
        }
        if (wave >= VOID_WAVE) {
            waveClock += dt;
            double a = bhSize(wave - 1);
            bhCur = a + (bhSize(wave) - a) * Math.min(1, waveClock / WAVE_TIME);
        }
        boolean isHost = role != CLIENT;
        if (isHost) {
            if (spawnLeft > 0) {
                spT -= dt;
                if (spT <= 0 && alive() < 12 + 6 * nOther()) {
                    spawn();
                    spawnLeft--;
                    spT = (wave >= VOID_WAVE ? Math.max(1.0, Math.min(3.0, 46.0 / Math.max(1, spawnTotal))) : 1.4) / (1 + .4 * nOther());
                }
            } else if (wave < VOID_WAVE && alive() == 0) {
                waveT += dt;
                if (waveT > 3) {
                    waveT = 0;
                    nextWave();
                }
            }
            if (wave >= VOID_WAVE && waveClock >= WAVE_TIME) nextWave();
        }

        updateZombies(dt, isHost);
        updateRemotes(dt);

        for (int i = trails.size() - 1; i >= 0; i--) {
            Trail t = trails.get(i);
            t.age += dt;
            if (t.age > t.life) trails.remove(i);
        }
        for (int i = blood.size() - 1; i >= 0; i--) {
            Blood b = blood.get(i);
            b.l -= dt;
            b.vy -= 9 * dt;
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.z += b.vz * dt;
            if (b.l <= 0) blood.remove(i);
        }

        if (hurtT > 0) hurtT -= dt;
        hud.vig = hurtT > 0 ? 1 : Math.max(0, (40 - hp) / 60);
        hud.hp = hp;
        hud.wave = wave;
        hud.waveLeft = wave >= VOID_WAVE ? Math.max(0, WAVE_TIME - waveClock) : -1;
        hud.kills = kills;
        hud.mag = mag;
        hud.res = res;
        hud.reloading = rl > 0;
        hud.dead = pDead;
        if (hud.banT > 0) hud.banT -= dt;
        hud.stickX = mvx;
        hud.stickY = mvy;
        hud.px = cx;
        hud.pz = cz;
        hud.pyaw = yaw;
        hud.bh = bhCur;
        float[] zz = new float[zs.size() * 3];
        int zi = 0;
        for (Zombie zq : zs) {
            zz[zi++] = (float) zq.x;
            zz[zi++] = (float) zq.z;
            zz[zi++] = zq.type;
        }
        hud.mz = zz;
        float[] pp2 = new float[pl.size() * 2];
        int pi2 = 0;
        for (Remote rq : pl.values()) {
            pp2[pi2++] = (float) rq.x;
            pp2[pi2++] = (float) rq.z;
        }
        hud.mp = pp2;

        if (hp <= 0 && !pDead) {
            if (role == SOLO) {
                showOver();
                return;
            }
            pDead = true;
            hp = 0;
            fireIn = false;
            hud.dead = true;
        }
        if (role == HOST && pDead) {
            boolean all = true;
            for (Remote q : pl.values()) if (!q.dead) all = false;
            if (all) {
                bc(Net.obj("t", "over", "w", wave, "k", kills));
                showOver();
                return;
            }
        }
        netT -= dt;
        if (netT <= 0) {
            netT = role == HOST ? .066 : .05;
            netTick();
        }
    }

    private void updateDebris(double dt) {
        for (int i = deb.size() - 1; i >= 0; i--) {
            Debris d = deb.get(i);
            d.l -= dt;
            if (d.l <= 0) {
                deb.remove(i);
                continue;
            }
            if (d.rest) continue;
            d.vy -= 9.8 * dt;
            d.n.px += d.vx * dt;
            d.n.py += d.vy * dt;
            d.n.pz += d.vz * dt;
            d.n.rx += d.wx * dt;
            d.n.ry += d.wy * dt;
            d.n.rz += d.wz * dt;
            if (d.n.py < d.h) {
                d.n.py = d.h;
                d.bounces++;
                d.vy = -d.vy * d.b;
                d.vx *= .55;
                d.vz *= .55;
                d.wx *= .55;
                d.wy *= .55;
                d.wz *= .55;
                if (Math.abs(d.vy) < .5) {
                    d.rest = true;
                    d.n.rx = d.mag ? 0 : PI / 2;
                    d.n.rz = d.mag ? PI / 2 : 0;
                    d.n.py = d.mag ? .021 : .006;
                }
            }
        }
    }

    private Nav nav;
    private final java.util.HashMap<String, Nav.Field> navF = new java.util.HashMap<>();
    private double navT;

    private static double turn(double a, double b, double m) {
        double d = b - a;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        if (Math.abs(d) <= m) return b;
        return a + Math.signum(d) * m;
    }

    private void updateZombies(double dt, boolean isHost) {
        List<Tgt> targets = isHost ? allPl() : null;
        if (isHost) {
            if (nav == null) nav = new Nav(world.obs, .55);
            navT -= dt;
            if (navT <= 0) {
                navT = .45;
                for (Tgt t : targets) {
                    Nav.Field f = navF.get(t.id);
                    if (f == null) {
                        f = new Nav.Field();
                        navF.put(t.id, f);
                    }
                    nav.fill(f, t.x, t.z);
                }
            }
        }
        for (int i = zs.size() - 1; i >= 0; i--) {
            Zombie z = zs.get(i);
            z.t += dt;
            if (z.dead) {
                z.dt += dt;
                z.g.rx = -Math.min(z.dt * 3, 1.5);
                if (isHost && z.dt > 5) {
                    zs.remove(i);
                    zmap.remove(z.id);
                }
                continue;
            }
            if (isHost) {
                Tgt best = null;
                double bd = 1e9, bc = 1e18;
                for (Tgt t : targets) {
                    double d = Math.hypot(t.x - z.x, t.z - z.z);
                    Nav.Field nf = navF.get(t.id);
                    int c = nf != null ? nav.cost(nf, z.x, z.z) : Nav.INF;
                    double score = c < Nav.INF ? c : 1000 + d * 3;
                    if (score < bc) {
                        bc = score;
                        bd = d;
                        best = t;
                    }
                }
                if (best != null) {
                    double dx = best.x - z.x, dz = best.z - z.z;
                    boolean reach = bd <= 1.1 && nav.los(z.x, z.z, best.x, best.z);
                    if (!reach) {
                        if (z.nudge > 0) {
                            z.nudge -= dt;
                            z.mv[0] = z.nx;
                            z.mv[1] = z.nz;
                        } else {
                            nav.step(navF.get(best.id), z.x, z.z, best.x, best.z, z.mv);
                        }
                        z.x += z.mv[0] * z.sp * dt;
                        z.z += z.mv[1] * z.sp * dt;
                        z.ry = turn(z.ry, Math.atan2(z.mv[0], z.mv[1]), dt * 9);
                        z.chk -= dt;
                        if (z.chk <= 0) {
                            z.chk = .6;
                            if (Math.hypot(z.x - z.lx, z.z - z.lz) < z.sp * .18 && bd > 1.6) {
                                double side = rng.nextBoolean() ? 1 : -1;
                                z.nudge = .5;
                                z.nx = -z.mv[1] * side;
                                z.nz = z.mv[0] * side;
                            }
                            z.lx = z.x;
                            z.lz = z.z;
                        }
                    } else {
                        z.ry = turn(z.ry, Math.atan2(dx, dz), dt * 12);
                        z.atk -= dt;
                        if (z.atk <= 0) {
                            z.atk = 1;
                            if (best.id.equals("host")) {
                                hp -= z.dmg;
                                hurtT = .4;
                                sfx.tone(120, .2, Sfx.SQUARE, .3);
                            } else {
                                sendTo(best.id, Net.obj("t", "hurt", "d", z.dmg));
                            }
                        }
                    }
                }
                for (Zombie o : zs) {
                    if (o == z || o.dead) continue;
                    double ex = z.x - o.x, ez = z.z - o.z, e = Math.hypot(ex, ez);
                    if (e < .7 && e > 0) {
                        z.x += ex / e * dt * 1.5;
                        z.z += ez / e * dt * 1.5;
                    }
                }
                double[] pp = {z.x, z.z};
                collide(pp, .4, 0);
                z.x = pp[0];
                z.z = pp[1];
            } else {
                double a = Math.min(1, dt * 10);
                z.x += (z.tx - z.x) * a;
                z.z += (z.tz - z.z) * a;
            }
            double s = Math.sin(z.t * z.sp * 4);
            z.lL.rx = s * .6;
            z.lR.rx = -s * .6;
            z.aL.rx = -1.4 + s * .12;
            z.aR.rx = -1.4 - s * .12;
            z.g.rz = Math.sin(z.t * 2) * .07;
            z.g.rx = .12;
        }
    }

    private void updateRemotes(double dt) {
        for (Remote q : pl.values()) {
            if (q.node == null) mkAvatar(q);
            Node m = q.node;
            double ox = m.px, oz = m.pz, a = Math.min(1, dt * 12);
            m.px += (q.x - ox) * a;
            m.pz += (q.z - oz) * a;
            m.ry = q.yaw + PI;
            m.visible = !q.dead;
            m.py += (q.y - m.py) * a;
            q.inner.rx = q.mode == 1 ? 1.2 : q.mode == 2 ? 1.45 : q.mode == 3 ? -.8 : 0;
            q.flashT -= dt;
            q.flashN.visible = q.flashT > 0;
            if (q.flashT > 0) q.flashN.rz = rng.nextDouble() * 6.28;
            double spd = Math.hypot(m.px - ox, m.pz - oz) / Math.max(dt, .001);
            q.ph += dt * spd * 1.6;
            double w = spd > .5 ? Math.sin(q.ph) * .6 : 0;
            q.lL.rx = w;
            q.lR.rx = -w;
            if (q.f != q.lf) {
                q.lf = q.f;
            }
        }
    }

    /* ---- jumping, diving, menu ---- */

    private int mode() {
        return diving ? 1 : crawling ? 2 : (slideT > 0 || vaultT > 0) ? 3 : 0;
    }

    private void resetPhysics() {
        vaultT = 0;
        vaultObs = null;
        jy = vy = hvx = hvz = slideT = crouchOff = dvPitch = camRoll = crawlPh = 0;
        wDive = wSlide = wCrawl = wRun = 0;
        fovCur = 70;
        grounded = true;
        diving = crawling = airFast = false;
        sprint = false;
    }

    private double groundAt(double x, double z, double r, double feet) {
        double g = 0;
        for (double[] o : world.obs) {
            if (o[4] > feet + .35) continue;
            double px = Math.max(o[0] - o[2] / 2, Math.min(x, o[0] + o[2] / 2));
            double pz = Math.max(o[1] - o[3] / 2, Math.min(z, o[1] + o[3] / 2));
            if (Math.hypot(x - px, z - pz) < r && o[4] > g) g = o[4];
        }
        return g;
    }

    private boolean insideAny(java.util.List<double[]> boxes, double x, double z) {
        for (double[] f : boxes) if (x > f[0] && x < f[2] && z > f[1] && z < f[3]) return true;
        return false;
    }

    private String surfaceAt(double x, double z) {
        if (insideAny(world.floors, x, z)) return "wood";
        for (double[] r : world.roads) {
            if (Math.abs(x - r[0]) < r[2] / 2 && Math.abs(z - r[1]) < r[3] / 2) return "asphalt";
        }
        return "grass";
    }

    private double wetUnder(double x, double z) {
        double grow = Math.max(0, Math.min(1, (Gfx.wet - .3) / .55));
        if (grow <= .02 || insideAny(world.floors, x, z)) return 0;
        double a = Gfx.wet > .55 ? Math.min(.45, (Gfx.wet - .55) * 1.2) : 0;
        for (int i = 0; i + 3 < puddleSpots.length; i += 4) {
            if (Math.hypot(x - puddleSpots[i], z - puddleSpots[i + 1]) < puddleSpots[i + 2] * .8 * grow) {
                a = Math.max(a, .55 + .45 * grow);
            }
        }
        return a;
    }

    private void footstep(boolean run) {
        float rate = .95f + rng.nextFloat() * .1f;
        ui.sound("footstep_" + surfaceAt(cx, cz) + (run ? "_run" : "") + "_" + (1 + rng.nextInt(4)), run ? .85f : .6f, rate);
        double wet = wetUnder(cx, cz);
        if (wet > .05) ui.sound("footstep_water_splash" + (run ? "_run" : "") + "_" + (1 + rng.nextInt(4)), (float) (wet * (run ? .9 : .65)), rate);
    }

    private void toggleCrawl() {
        if (pDead || diving || slideT > 0 || vaultT > 0 || !grounded) return;
        crawling = !crawling;
        sprint = false;
        if (crawling) {
            crawlPh = 0;
            crouchOff += .1;
            sfx.noise(.12, .22, 500);
        }
    }

    private boolean tryVault() {
        double lx = -Math.sin(yaw), lz = -Math.cos(yaw);
        if (dvx * lx + dvz * lz < .75 || pitch > .75) return false;
        double bestT = 1.7, bestExit = 0;
        double[] hit = null;
        for (double[] o : world.obs) {
            double rise = o[4] - jy;
            if (rise < .4 || rise > 1.5) continue;
            double hw = o[2] / 2 + .3, hd = o[3] / 2 + .3;
            double tmin = -1e9, tmax = 1e9;
            boolean miss = false;
            double[] c = {o[0], o[1]}, h = {hw, hd}, p = {cx, cz}, v = {dvx, dvz};
            for (int a = 0; a < 2; a++) {
                if (Math.abs(v[a]) < 1e-6) {
                    if (Math.abs(p[a] - c[a]) > h[a]) miss = true;
                } else {
                    double t1 = (c[a] - h[a] - p[a]) / v[a], t2 = (c[a] + h[a] - p[a]) / v[a];
                    tmin = Math.max(tmin, Math.min(t1, t2));
                    tmax = Math.min(tmax, Math.max(t1, t2));
                }
            }
            if (miss || tmax < Math.max(tmin, 0)) continue;
            double entry = Math.max(tmin, 0);
            if (entry < bestT) {
                bestT = entry;
                bestExit = tmax;
                hit = o;
            }
        }
        if (hit == null) return false;
        vaultObs = hit;
        vaultTop = hit[4];
        vaultT = Math.max(.42, Math.min(1.2, (bestExit + .15) / 6.3));
        slideT = 0;
        airFast = false;
        hvx = dvx * 6.3;
        hvz = dvz * 6.3;
        sfx.noise(.3, .35, 900);
        sfx.tone(120, .14, Sfx.TRI, .18);
        return true;
    }

    private void endVault() {
        vaultObs = null;
        vaultT = 0;
        grounded = false;
        vy = .4;
        hvx *= .75;
        hvz *= .75;
        crouchOff += .12;
    }

    private void startDive() {
        if (pDead || diving || crawling || slideT > 0 || vaultT > 0) return;
        double sn = Math.sin(yaw), cs = Math.cos(yaw);
        double fx = mvx, fz = -mvy, dx, dz;
        if (Math.hypot(fx, fz) > .15) {
            dx = fx * cs - fz * sn;
            dz = -fx * sn - fz * cs;
        } else {
            dx = -sn;
            dz = -cs;
        }
        double l = Math.hypot(dx, dz);
        dvx = dx / l;
        dvz = dz / l;
        if (grounded && tryVault()) return;
        double speed = Math.min(9.5, Math.max(7.5, Math.hypot(hvx, hvz) * 1.35));
        hvx = dvx * speed;
        hvz = dvz * speed;
        diving = true;
        if (grounded) {
            vy = 3.6;
            grounded = false;
        } else {
            vy = Math.min(vy, 1.2) - 2.0;
        }
        slideT = 0;
        airFast = false;
    }

    private void startSlide() {
        if (pDead || diving || crawling || slideT > 0 || vaultT > 0 || !grounded) return;
        double sn = Math.sin(yaw), cs = Math.cos(yaw);
        double fx = mvx, fz = -mvy, dx, dz;
        if (Math.hypot(fx, fz) > .15) {
            dx = fx * cs - fz * sn;
            dz = -fx * sn - fz * cs;
        } else {
            dx = -sn;
            dz = -cs;
        }
        double l = Math.hypot(dx, dz);
        dvx = dx / l;
        dvz = dz / l;
        slideV0 = Math.min(9.5, Math.max(7.8, Math.hypot(hvx, hvz) * 1.3 + (sprint ? 1.0 : 0)));
        hvx = dvx * slideV0;
        hvz = dvz * slideV0;
        slideT = SLIDE_TIME;
    }

    private void land(double impact) {
        airFast = false;
        if (diving) {
            diving = false;
            crawling = true;
            crawlPh = 0;
            crouchOff += .1;
            sfx.noise(.25, .4, 600);
            sfx.tone(80, .12, Sfx.TRI, .2);
        } else if (impact > 4) {
            crouchOff += Math.min(.35, impact * .035);
        }
    }

    void pause() {
        saveProgress();
        paused = true;
        mvx = mvy = 0;
        fireIn = false;
        ui.showMenu(true);
    }

    void toMenu() {
        saveProgress();
        net.close();
        role = SOLO;
        state = MENU;
        paused = false;
        hud.playing = false;
        hud.room = "";
        mvx = mvy = 0;
        fireIn = false;
        pl.clear();
        zs.clear();
        zmap.clear();
        ui.hideOver();
        ui.status("");
        ui.showMenu(false);
    }

    /* ---- networking ---- */

    private void bc(JSONObject m) {
        if (role == HOST) net.sendG(m, null);
    }

    private void sendTo(String id, JSONObject m) {
        if (role == HOST) net.sendG(m, id);
    }

    private void netTick() {
        try {
            if (role == CLIENT) {
                net.sendG(Net.obj("t", "st", "x", r2(cx), "z", r2(cz), "yaw", r2(yaw), "pitch", r2(pitch),
                        "dead", pDead, "f", fired, "y", r2(jy), "m", mode()), null);
            } else if (role == HOST) {
                JSONArray p = new JSONArray();
                p.put(new JSONArray().put("host").put(r2(cx)).put(r2(cz)).put(r2(yaw)).put(r2(pitch)).put(pDead ? 1 : 0).put(fired).put(0).put(r2(jy)).put(mode()));
                for (Remote q : pl.values()) {
                    p.put(new JSONArray().put(q.id).put(r2(q.x)).put(r2(q.z)).put(r2(q.yaw)).put(r2(q.pitch))
                            .put(q.dead ? 1 : 0).put(q.f).put(q.idx).put(r2(q.y)).put(q.mode));
                }
                JSONArray z = new JSONArray();
                for (Zombie zb : zs) {
                    z.put(new JSONArray().put(zb.id).put(r2(zb.x)).put(r2(zb.z)).put(r2(zb.ry)).put(zb.dead ? 1 : 0).put(zb.type));
                }
                net.sendG(Net.obj("t", "snap", "w", wave, "k", kills, "p", p, "z", z), null);
            }
        } catch (Exception e) {
            // a dropped packet is fine
        }
    }

    private void applySnap(JSONObject m) {
        int w = m.optInt("w");
        if (w != wave) onWave(w);
        kills = m.optInt("k");
        JSONArray za = m.optJSONArray("z");
        if (za != null) {
            java.util.HashSet<Integer> seen = new java.util.HashSet<>();
            for (int i = 0; i < za.length(); i++) {
                JSONArray e = za.optJSONArray(i);
                if (e == null) continue;
                int id = e.optInt(0);
                seen.add(id);
                Zombie z = zmap.get(id);
                if (z == null) {
                    z = mkZombie(e.optInt(5));
                    z.id = id;
                    z.x = z.tx = e.optDouble(1);
                    z.z = z.tz = e.optDouble(2);
                    z.ry = e.optDouble(3);
                    zmap.put(id, z);
                    zs.add(z);
                }
                z.tx = e.optDouble(1);
                z.tz = e.optDouble(2);
                z.ry = e.optDouble(3);
                if (e.optInt(4) != 0 && !z.dead) {
                    z.dead = true;
                    z.dt = 0;
                }
            }
            for (int i = zs.size() - 1; i >= 0; i--) {
                Zombie z = zs.get(i);
                if (!seen.contains(z.id)) {
                    zs.remove(i);
                    zmap.remove(z.id);
                }
            }
        }
        JSONArray pa = m.optJSONArray("p");
        if (pa != null) {
            java.util.HashSet<String> ids = new java.util.HashSet<>();
            for (int i = 0; i < pa.length(); i++) {
                JSONArray e = pa.optJSONArray(i);
                if (e == null) continue;
                String id = e.optString(0);
                if (id.equals(netId)) continue;
                ids.add(id);
                Remote q = pl.get(id);
                if (q == null) {
                    q = new Remote();
                    q.id = id;
                    q.x = e.optDouble(1);
                    q.z = e.optDouble(2);
                    q.f = q.lf = e.optInt(6);
                    q.idx = e.optInt(7);
                    pl.put(id, q);
                }
                q.x = e.optDouble(1);
                q.z = e.optDouble(2);
                q.yaw = e.optDouble(3);
                q.pitch = e.optDouble(4);
                q.dead = e.optInt(5) != 0;
                q.f = e.optInt(6);
                q.y = e.optDouble(8);
                q.mode = e.optInt(9);
            }
            pl.keySet().retainAll(ids);
        }
        updRoom();
    }

    private void clientMsg(JSONObject m) {
        String t = m.optString("t");
        switch (t) {
            case "welcome":
                role = CLIENT;
                ui.sound("multiplayer_join_connected", 1f, 1f);
                myIdx = m.optInt("idx");
                hud.room = "";
                worldId = null;
                if (m.has("seed")) setWorld(m.optLong("seed"));
                if (store != null) store.cacheServer(joinCode, worldSeed);
                startGame(true);
                wave = m.optInt("wave");
                kills = m.optInt("kills");
                waveClock = m.optDouble("wt", 0);
                if (wave >= VOID_WAVE) {
                    bhCur = bhSize(wave - 1);
                    ui.bhSound(1, (int) (Math.min(waveClock, WAVE_TIME) * 1000));
                    bhSnd = 1;
                }
                break;
            case "snap":
                if (role == CLIENT && state == PLAY) applySnap(m);
                break;
            case "hit": {
                JSONArray p = m.optJSONArray("p");
                if (p != null) spawnBlood(p.optDouble(0), p.optDouble(1), p.optDouble(2));
                break;
            }
            case "hurt":
                hp -= m.optDouble("d", 10);
                hurtT = .4;
                sfx.tone(120, .2, Sfx.SQUARE, .3);
                break;
            case "ammo":
                res += m.optInt("n", 3);
                break;
            case "trail": {
                String sid = m.optString("id");
                JSONArray to = m.optJSONArray("o"), te = m.optJSONArray("e");
                if (sid.equals(netId) || to == null || te == null) break;
                double ox = to.optDouble(0), oy = to.optDouble(1), oz = to.optDouble(2);
                double ex = te.optDouble(0), ey = te.optDouble(1), ez = te.optDouble(2);
                double dx = ex - ox, dy = ey - oy, dz = ez - oz, dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dl > .01) {
                    addTrailFromEye(ox, oy, oz, dx / dl, dy / dl, dz / dl, ex, ey, ez);
                    Remote sq = pl.get(sid);
                    if (sq != null) sq.flashT = .06;
                    shotSound(sq);
                }
                break;
            }
            case "strike":
                startStrike(m.optLong("seed"), m.optDouble("rx"), m.optDouble("rz"), m.optDouble("dx"),
                        m.optDouble("dz"), m.optDouble("tt", 1));
                break;
            case "wave":
                onWave(m.optInt("w"));
                break;
            case "over":
                wave = m.optInt("w", wave);
                kills = m.optInt("k", kills);
                showOver();
                break;
            case "restart":
                ui.hideOver();
                reset(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void ping(long ms) {
        hud.ping = ms;
    }

    @Override
    public void status(String s) {
        ui.status(s);
    }

    @Override
    public void hosted() {
        post(new Runnable() {
            @Override
            public void run() {
                role = HOST;
                netId = "host";
                myIdx = 0;
                ui.sound("multiplayer_join_connected", 1f, 1f);
                ui.status("");
                pl.clear();
                worldId = pendId;
                startWave = pendWave;
                setWorld(pendSeed);
                startGame(false);
            }
        });
    }

    @Override
    public void taken() {
        post(new Runnable() {
            @Override
            public void run() {
                hostNow();
            }
        });
    }

    @Override
    public void peerJoin(final String id) {
        post(new Runnable() {
            @Override
            public void run() {
                if (role != HOST) return;
                boolean[] used = new boolean[4];
                for (Remote q : pl.values()) used[q.idx % 4] = true;
                int idx = 1;
                while (idx < 3 && used[idx]) idx++;
                Remote q = new Remote();
                q.id = id;
                q.idx = idx;
                pl.put(id, q);
                sendTo(id, Net.obj("t", "welcome", "idx", idx, "wave", wave, "kills", kills, "seed", worldSeed, "wt", waveClock));
                updRoom();
            }
        });
    }

    @Override
    public void peerLeave(final String id) {
        post(new Runnable() {
            @Override
            public void run() {
                pl.remove(id);
                updRoom();
            }
        });
    }

    @Override
    public void fromClient(final String id, final JSONObject m) {
        post(new Runnable() {
            @Override
            public void run() {
                Remote q = pl.get(id);
                if (q == null) return;
                String t = m.optString("t");
                if (t.equals("st")) {
                    q.x = m.optDouble("x");
                    q.z = m.optDouble("z");
                    q.yaw = m.optDouble("yaw");
                    q.pitch = m.optDouble("pitch");
                    q.dead = m.optBoolean("dead");
                    q.f = m.optInt("f");
                    q.y = m.optDouble("y");
                    q.mode = m.optInt("m");
                } else if (t.equals("shot")) {
                    remoteShot(id, m);
                }
            }
        });
    }

    @Override
    public void joined(final String id) {
        post(new Runnable() {
            @Override
            public void run() {
                netId = id;
            }
        });
    }

    @Override
    public void fromHost(final JSONObject m) {
        post(new Runnable() {
            @Override
            public void run() {
                clientMsg(m);
            }
        });
    }

    @Override
    public void hostGone() {
        post(new Runnable() {
            @Override
            public void run() {
                clientLost();
            }
        });
    }

    @Override
    public void closed() {
        post(new Runnable() {
            @Override
            public void run() {
                if (role == HOST) {
                    pl.clear();
                    role = SOLO;
                    hud.room = "OFFLINE";
                } else if (role == CLIENT) {
                    clientLost();
                } else if (state == MENU) {
                    ui.status(L.t("n_lost"));
                }
            }
        });
    }
}
