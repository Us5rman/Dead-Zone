package com.deadzone.game;

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
        void hideMenu();

        void showOver(String text, boolean canRestart);

        void hideOver();

        void status(String s);

        void redraw();
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
        int f, lf, idx;
        Node node, lL, lR;
    }

    static final class Zombie {
        int id;
        double x, z, ry, tx, tz, hp, t, dt, atk, sp, scale;
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

    final Ui ui;
    final Hud hud = new Hud();
    final World world = new World();
    final Gun gun = new Gun();
    final Sfx sfx = new Sfx();
    final Net net;
    final Random rng = new Random();
    GLSurfaceView view;

    // input from the ui thread
    volatile double mvx, mvy;
    volatile boolean fireIn, reloadReq;
    private double lyaw, lpitch;

    // player
    double cx, cy = 1.7, cz, yaw, pitch;
    double hp = 100;
    int mag = 12, res = 60, kills, wave;
    int state = MENU, role = SOLO, myIdx;
    String netId = "host";
    String roomCode = "";
    boolean pDead, firing;
    int fired;

    // weapon and timers
    double spT, waveT, cd, rl, flash, bob, hurtT, RT = 1.75, dryT, netT;
    int rlEv, spawnLeft;
    boolean rlEmpty;
    double sl = 9, spr, spMv, kz, kzv, kr, krv, kc, kcv, ky, kyv, kro, krov;

    // lighting
    double ambI = .9, flickT, lightWait = 20, lightClock = -1;
    int bgHex = 0x070b10;

    // things in the world
    final ArrayList<Zombie> zs = new ArrayList<>();
    final java.util.HashMap<Integer, Zombie> zmap = new java.util.HashMap<>();
    final LinkedHashMap<String, Remote> pl = new LinkedHashMap<>();
    final ArrayList<Debris> deb = new ArrayList<>();
    final ArrayList<Blood> blood = new ArrayList<>();
    int zid;

    private final Node bloodNode = new Node();
    private final Mat mBrass = Mat.color(0xc8a040).emissive(0x3a2c08, 1);
    private final Mat starMat = Mat.basic(0xffffff);
    private int vw = 1, vh = 1;
    private long last;

    Game(Ui ui) {
        this.ui = ui;
        this.net = new Net(this);
        bloodNode.parts.add(Node.box(.08, .08, .08, 0, 0, 0, Mat.basic(0x8a0808)));
        starMat.noFog = true;
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
        last = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        vw = Math.max(1, w);
        vh = Math.max(1, h);
        GLES20.glViewport(0, 0, w, h);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        double dt = last == 0 ? .016 : Math.min((now - last) / 1e9, .05);
        last = now;
        if (state == PLAY) update(dt);
        else tickLights(dt);
        render();
        ui.redraw();
    }

    private float[] camWorld() {
        return Xf.makeYXZ(cx, cy, cz, pitch + kc, yaw + ky);
    }

    private void render() {
        float[] cw = camWorld();
        float[] viewM = new float[16];
        Matrix.invertM(viewM, 0, cw, 0);
        float[] proj = new float[16];
        Matrix.perspectiveM(proj, 0, 70f, (float) vw / vh, .1f, 90f);
        float[] vp = Xf.mul(proj, viewM);

        float[] plPos = new float[24], plCol = new float[24], plRange = new float[8];
        int n = 0;
        for (World.Light l : world.lights) {
            if (n >= 7) break;
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

        float[] bg = Mat.rgb(bgHex);
        GLES20.glDepthMask(true);
        GLES20.glClearColor(bg[0], bg[1], bg[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Gfx.frame(vp, new float[]{(float) cx, (float) cy, (float) cz}, fwd, amb, plPos, plCol, plRange, n);

        world.draw(false);
        Gfx.useMat(starMat);
        Gfx.drawPoints(world.stars, Xf.make(cx, cy, cz, 0, 0, 0, 1));

        for (Zombie z : zs) {
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
        gun.gun.draw(cw, false);

        world.draw(true);
        gun.gun.draw(cw, true);
    }

    /* ---- starting and stopping ---- */

    private String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append("ABCDEFGHJKMNPQRSTUVWXYZ".charAt(rng.nextInt(23)));
        return sb.toString();
    }

    void startSolo() {
        net.close();
        role = SOLO;
        myIdx = 0;
        netId = "host";
        hud.room = "";
        startGame(false);
    }

    void startHost() {
        roomCode = newCode();
        net.host(roomCode);
    }

    void startJoin(String code) {
        code = code == null ? "" : code.trim().toUpperCase();
        if (code.length() != 4) {
            ui.status("کد ۴ حرفی را وارد کن");
            return;
        }
        roomCode = code;
        net.join(code);
    }

    void again() {
        ui.hideOver();
        bc(Net.obj("t", "restart"));
        reset(false);
    }

    private void startGame(boolean cli) {
        ui.hideMenu();
        ui.hideOver();
        reset(cli);
    }

    private void reset(boolean cli) {
        zs.clear();
        zmap.clear();
        deb.clear();
        blood.clear();
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
        hud.dead = false;
        state = PLAY;
        hud.playing = true;
        if (!cli) nextWave();
        updRoom();
    }

    private void showOver() {
        state = OVER;
        hud.playing = false;
        mvx = mvy = 0;
        fireIn = false;
        ui.showOver("WAVE " + wave + "   ·   KILLS " + kills, role != CLIENT);
    }

    private void clientLost() {
        if (role != CLIENT) return;
        state = OVER;
        hud.playing = false;
        mvx = mvy = 0;
        fireIn = false;
        ui.showOver("اتصال به میزبان قطع شد", false);
    }

    private int nOther() {
        return pl.size();
    }

    private void updRoom() {
        hud.room = role == SOLO ? "" : "ROOM: " + roomCode + " · " + (1 + nOther()) + "P";
    }

    private void ban(String t) {
        hud.banner = t;
        hud.banT = 2.4;
    }

    private void onWave(int w) {
        wave = w;
        ban("WAVE " + wave);
        if (wave > 1) {
            if (pDead) {
                pDead = false;
                hp = 50;
                hud.dead = false;
                cx = SPAWN_AT[myIdx % 4][0];
                cz = SPAWN_AT[myIdx % 4][1];
                cy = 1.7;
            }
            hp = Math.min(100, hp + 20);
            res += 24;
        }
    }

    private void nextWave() {
        spawnLeft = (int) Math.round((4 + (wave + 1) * 3) * (1 + .7 * nOther()));
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

    private void remoteShot(String id, JSONObject m) {
        Remote q = pl.get(id);
        JSONArray o = m.optJSONArray("o"), d = m.optJSONArray("d");
        if (q == null || q.dead || o == null || d == null) return;
        resolveHit(id, o.optDouble(0), o.optDouble(1), o.optDouble(2), d.optDouble(0), d.optDouble(1), d.optDouble(2));
    }

    /* ---- zombies and players ---- */

    private Zombie mkZombie() {
        Zombie z = new Zombie();
        z.scale = .95 + rng.nextDouble() * .15;
        z.hp = 3 + Math.floor(wave / 3.0);
        z.t = rng.nextDouble() * 6;
        z.sp = 1.1 + wave * .12 + rng.nextDouble() * .5;
        z.o = new Node();
        z.o.s = z.scale;
        z.g = new Node();
        z.o.kids.add(z.g);
        z.g.parts.add(Node.box(.5, .7, .25, 0, 1.15, 0, world.mShirt));
        z.g.parts.add(Node.box(.3, .3, .3, 0, 1.65, 0, world.mSkin));
        z.aL = limb(z.g, -.35, 1.45, Node.box(.14, .6, .14, 0, -.28, 0, world.mSkin));
        z.aR = limb(z.g, .35, 1.45, Node.box(.14, .6, .14, 0, -.28, 0, world.mSkin));
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
        Node g = new Node();
        g.parts.add(Node.box(.5, .7, .25, 0, 1.15, 0, shirt));
        g.parts.add(Node.box(.28, .28, .28, 0, 1.65, 0, skin));
        g.parts.add(Node.box(.14, .6, .14, -.35, 1.15, .05, skin));
        g.parts.add(Node.box(.14, .14, .6, .3, 1.3, .3, skin));
        g.parts.add(Node.box(.06, .1, .3, .3, 1.34, .6, world.mGun));
        q.lL = limb(g, -.13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        q.lR = limb(g, .13, .8, Node.box(.18, .8, .18, 0, -.4, 0, world.mPants));
        g.at(q.x, 0, q.z);
        q.node = g;
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
        Zombie z = mkZombie();
        z.id = ++zid;
        zmap.put(z.id, z);
        List<Tgt> l = allPl();
        Tgt bp = l.isEmpty() ? new Tgt("", 0, 0) : l.get(rng.nextInt(l.size()));
        double a = rng.nextDouble() * 6.28, d = 26 + rng.nextDouble() * 10;
        z.x = Math.max(-43, Math.min(43, bp.x + Math.cos(a) * d));
        z.z = Math.max(-43, Math.min(43, bp.z + Math.sin(a) * d));
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

    private void collide(double[] p, double r) {
        for (double[] o : world.obs) {
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
        if (state == PLAY && lightClock < 0) {
            lightWait -= dt;
            if (lightWait <= 0) lightClock = 0;
        }
        if (lightClock >= 0) {
            double t = lightClock;
            lightClock += dt;
            if (t < .08) {
                bgHex = 0x2a3a55;
                ambI = 2.4;
            } else if (t < .17) {
                bgHex = 0x070b10;
                ambI = .9;
            } else if (t < .24) {
                if (bgHex != 0x223048) sfx.tone(55, 1.6, Sfx.SAW, .22);
                bgHex = 0x223048;
                ambI = 2;
            } else {
                bgHex = 0x070b10;
                ambI = .9;
                lightClock = -1;
                lightWait = rr(14, 30);
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

        double fx = mvx, fz = -mvy;
        if (pDead) fx = fz = 0;
        double L = Math.hypot(fx, fz);
        if (L > 1) {
            fx /= L;
            fz /= L;
        }
        double sp = (L > 1.0 || Math.hypot(mvx, mvy) > .95) ? 6.5 : 4;
        double sn = Math.sin(yaw), cs = Math.cos(yaw);
        cx += (fx * cs - fz * sn) * sp * dt;
        cz += (-fx * sn - fz * cs) * sp * dt;
        cx = Math.max(-44, Math.min(44, cx));
        cz = Math.max(-44, Math.min(44, cz));
        double[] pp = {cx, cz};
        collide(pp, .5);
        cx = pp[0];
        cz = pp[1];
        boolean moving = Math.hypot(fx, fz) > .1;
        if (moving) bob += dt * sp * 1.6;
        cy = 1.7 + (moving ? Math.sin(bob) * .06 : 0);

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

        double gx = 0, gy = 0, gz = 0, grx = 0, gry = 0, grz = 0, dr = 0, slp;
        boolean magVis = true;
        if (rl > 0) {
            rl -= dt;
            double p = Math.max(0, Math.min(1, 1 - rl / RT));
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
        double sb = .075 * slp;
        gun.slide.pz = sb;
        gun.ham.rx = .45 + sb * 4;
        gun.flash.visible = flash > 0;
        if (flash > 0) gun.flash.rz = rng.nextDouble() * 6.28;
        gun.mag.visible = magVis;
        gun.mag.py = -.14 - dr * dr * (3 - 2 * dr) * .3;
        Node g = gun.gun;
        g.px = .22 + (moving ? Math.sin(bob * .5) * .015 : 0) + gx;
        g.py = -.2 + (moving ? Math.abs(Math.cos(bob)) * .015 : 0) + gy;
        g.pz = -.55 + kz + gz;
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

        boolean isHost = role != CLIENT;
        if (isHost) {
            if (spawnLeft > 0) {
                spT -= dt;
                if (spT <= 0 && alive() < 12 + 6 * nOther()) {
                    spawn();
                    spawnLeft--;
                    spT = 1.4 / (1 + .4 * nOther());
                }
            } else if (alive() == 0) {
                waveT += dt;
                if (waveT > 3) {
                    waveT = 0;
                    nextWave();
                }
            }
        }

        updateZombies(dt, isHost);
        updateRemotes(dt);

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
        hud.kills = kills;
        hud.mag = mag;
        hud.res = res;
        hud.reloading = rl > 0;
        hud.dead = pDead;
        if (hud.banT > 0) hud.banT -= dt;
        hud.stickX = mvx;
        hud.stickY = mvy;

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
                if (d.vy < -.8 && d.bounces < 2) {
                    if (d.mag) sfx.tone(180, .06, Sfx.TRI, .12);
                    else sfx.tone(3400 + rng.nextDouble() * 600, .06, Sfx.SINE, .035);
                }
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

    private void updateZombies(double dt, boolean isHost) {
        List<Tgt> targets = isHost ? allPl() : null;
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
                double bd = 1e9;
                for (Tgt t : targets) {
                    double d = Math.hypot(t.x - z.x, t.z - z.z);
                    if (d < bd) {
                        bd = d;
                        best = t;
                    }
                }
                if (best != null) {
                    double dx = best.x - z.x, dz = best.z - z.z;
                    z.ry = Math.atan2(dx, dz);
                    if (bd > 1.1) {
                        z.x += dx / bd * z.sp * dt;
                        z.z += dz / bd * z.sp * dt;
                    } else {
                        z.atk -= dt;
                        if (z.atk <= 0) {
                            z.atk = 1;
                            if (best.id.equals("host")) {
                                hp -= 10;
                                hurtT = .4;
                                sfx.tone(120, .2, Sfx.SQUARE, .3);
                            } else {
                                sendTo(best.id, Net.obj("t", "hurt", "d", 10));
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
                collide(pp, .4);
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
            double spd = Math.hypot(m.px - ox, m.pz - oz) / Math.max(dt, .001);
            q.ph += dt * spd * 1.6;
            double w = spd > .5 ? Math.sin(q.ph) * .6 : 0;
            q.lL.rx = w;
            q.lR.rx = -w;
            if (q.f != q.lf) {
                q.lf = q.f;
                double d = Math.hypot(q.x - cx, q.z - cz);
                sfx.tone(180, .12, Sfx.SAW, Math.min(.15, .6 / (1 + d)));
            }
        }
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
                        "dead", pDead, "f", fired), null);
            } else if (role == HOST) {
                JSONArray p = new JSONArray();
                p.put(new JSONArray().put("host").put(r2(cx)).put(r2(cz)).put(r2(yaw)).put(r2(pitch)).put(pDead ? 1 : 0).put(fired).put(0));
                for (Remote q : pl.values()) {
                    p.put(new JSONArray().put(q.id).put(r2(q.x)).put(r2(q.z)).put(r2(q.yaw)).put(r2(q.pitch))
                            .put(q.dead ? 1 : 0).put(q.f).put(q.idx));
                }
                JSONArray z = new JSONArray();
                for (Zombie zb : zs) {
                    z.put(new JSONArray().put(zb.id).put(r2(zb.x)).put(r2(zb.z)).put(r2(zb.ry)).put(zb.dead ? 1 : 0));
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
                    z = mkZombie();
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
                myIdx = m.optInt("idx");
                hud.room = "";
                startGame(true);
                wave = m.optInt("wave");
                kills = m.optInt("kills");
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
                ui.status("");
                pl.clear();
                startGame(false);
            }
        });
    }

    @Override
    public void taken() {
        post(new Runnable() {
            @Override
            public void run() {
                startHost();
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
                sendTo(id, Net.obj("t", "welcome", "idx", idx, "wave", wave, "kills", kills));
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
                    ui.status("اتصال قطع شد");
                }
            }
        });
    }
}
