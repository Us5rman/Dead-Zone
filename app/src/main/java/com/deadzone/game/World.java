package com.deadzone.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

final class World {
    static final double PI = Math.PI;

    static final class Light {
        double x, y, z, range, inten;
        float[] col;
        boolean flick;
    }

    static final class Batch {
        Mat mat;
        Geo geo;
    }

    final ArrayList<double[]> obs = new ArrayList<>();   // x, z, w, d, top
    final ArrayList<double[]> glass = new ArrayList<>();
    final ArrayList<double[]> walls = new ArrayList<>();
    final ArrayList<double[]> roofs = new ArrayList<>();   // x0, z0, x1, z1, top
    final ArrayList<Light> lights = new ArrayList<>();
    final ArrayList<Batch> solid = new ArrayList<>();
    final ArrayList<Batch> blend = new ArrayList<>();

    final Random rng;
    final long seed;
    private final LinkedHashMap<Mat, FloatList> acc = new LinkedHashMap<>();

    Mat mGround, mBrick, mWood, mSkin, mShirt, mPants, mGun, mConc, mCrate, mRust, mSand, mFloor, mRd, mCloth;
    Mat mTire, mDark, mBulb, mPool, mMud, mGlass, mMirror;
    private Geo sg;

    World(long seed) {
        this.seed = seed;
        rng = new Random(seed);
        Tex.R.setSeed(seed * 31 + 7);
        buildMats();
        build();
        for (java.util.Map.Entry<Mat, FloatList> e : acc.entrySet()) {
            Batch b = new Batch();
            b.mat = e.getKey();
            b.geo = new Geo(e.getValue().toArray());
            (b.mat.blend ? blend : solid).add(b);
        }
    }

    double rnd(double a, double b) {
        return a + rng.nextDouble() * (b - a);
    }

    private Mat T(Tex.Painter p, double rep) {
        return Mat.textured(Tex.tile(p), rep);
    }

    private void buildMats() {
        mGlass = Mat.color(0x0b1620).alpha(.2);
        mGlass.glass = 1;
        mMirror = Mat.color(0x090d11);
        mMirror.glass = 2;
        mMirror.offset = true;
        mGround = T((c, w, h) -> {
            Tex.noise(c, 62, 70, 44, 55);
            Tex.blot(c, "#2c2a1c", 10);
        }, 100);
        mBrick = T((c, w, h) -> {
            Tex.noise(c, 112, 62, 50, 30);
            for (int y = 0; y < 64; y += 16) {
                Tex.rect(c, "#2a2a2a", 0, y, 64, 2);
                for (int x = (y / 16 % 2) * 16; x < 64; x += 32) Tex.rect(c, "#2a2a2a", x, y, 2, 16);
            }
        }, 3);
        mWood = T((c, w, h) -> {
            Tex.noise(c, 70, 50, 35, 40);
            for (int x = 0; x < 64; x += 8) Tex.rect(c, "#2a1c10", x, 0, 1, 64);
        }, 1);
        mSkin = T((c, w, h) -> {
            Tex.noise(c, 105, 140, 95, 35);
            Tex.blot(c, "#4a1010", 6);
        }, 1);
        mShirt = T((c, w, h) -> {
            Tex.noise(c, 60, 70, 92, 30);
            Tex.blot(c, "#5a0a0a", 9);
        }, 1);
        mPants = T((c, w, h) -> Tex.noise(c, 52, 46, 40, 22), 1);
        mGun = Mat.color(0x222226);
        mConc = T((c, w, h) -> {
            Tex.noise(c, 98, 98, 94, 35);
            Tex.blot(c, "#3c3c3a", 8);
        }, 3);
        mCrate = T((c, w, h) -> {
            Tex.noise(c, 96, 70, 38, 35);
            Tex.rect(c, "#3a2810", 0, 0, 64, 3);
            Tex.rect(c, "#3a2810", 0, 61, 64, 3);
            Tex.rect(c, "#3a2810", 0, 30, 64, 3);
        }, 1);
        mRust = T((c, w, h) -> {
            Tex.noise(c, 92, 62, 44, 45);
            Tex.blot(c, "#3a1a10", 14);
        }, 1);
        mSand = T((c, w, h) -> Tex.noise(c, 150, 130, 90, 35), 1);
        mFloor = T((c, w, h) -> {
            Tex.noise(c, 72, 62, 52, 30);
            for (int x = 0; x < 64; x += 16) Tex.rect(c, "#2a2018", x, 0, 1, 64);
        }, 4);
        mRd = T((c, w, h) -> {
            Tex.noise(c, 42, 42, 44, 25);
            Tex.rect(c, "#b8a020", 0, 30, 32, 3);
        }, 1);
        mRd.repU = 23;
        mGround.puddle = true;
        mRd.puddle = true;
        mCloth = T((c, w, h) -> {
            Tex.noise(c, 150, 148, 138, 25);
            Tex.blot(c, "#5a1010", 6);
        }, 1);
        mTire = Mat.color(0x151515);
        mDark = Mat.basic(0x080c10);
        mBulb = Mat.basic(0xffd890);
        mBulb.noFog = true;
        mPool = Mat.color(0x3a0707).alpha(.85);
        mMud = Mat.color(0x241f14).alpha(.6);
        sg = Geo.box(1, 1, 1);
    }

    /* ---- mesh helpers ---- */

    private void bake(float[] prim, Mat m, float[] model) {
        FloatList l = acc.get(m);
        if (l == null) {
            l = new FloatList();
            acc.put(m, l);
        }
        Xf.bake(l, prim, model, m.repU, m.repV);
    }

    private void mesh(float[] prim, Mat m, double x, double y, double z, double rx, double ry, double rz) {
        bake(prim, m, Xf.make(x, y, z, rx, ry, rz, 1));
    }

    private void mesh(float[] prim, Mat m, double x, double y, double z) {
        mesh(prim, m, x, y, z, 0, 0, 0);
    }

    private void mb(double w, double h, double d, double x, double y, double z, Mat m) {
        mesh(Prim.box(w, h, d), m, x, y, z);
    }

    private void mbr(double w, double h, double d, double x, double y, double z, Mat m, double rx, double ry, double rz) {
        mesh(Prim.box(w, h, d), m, x, y, z, rx, ry, rz);
    }

    private void solidBox(double x, double y, double z, double w, double h, double d) {
        walls.add(new double[]{x - w / 2, y - h / 2, z - d / 2, x + w / 2, y + h / 2, z + d / 2});
    }

    /** collidable box standing on the ground */
    void box(double x, double z, double w, double d, double h, Mat m) {
        mb(w, h, d, x, h / 2, z, m);
        solidBox(x, h / 2, z, w, h, d);
        obs.add(new double[]{x, z, w, d, h});
    }

    /* ---- rain shelter height map: top of every block / wall / roof, so rain can land on them ---- */

    static final float SH_MIN = -50f, SH_SIZE = 100f, SH_MAXH = 16f;
    static final int SH_N = 200;
    private byte[] shelter;

    private void stamp(byte[] d, double x0, double z0, double x1, double z1, double top) {
        if (top < .25) return;
        int v = (int) Math.min(255, Math.round(top / SH_MAXH * 255));
        int i0 = (int) Math.floor((x0 - SH_MIN) / SH_SIZE * SH_N), i1 = (int) Math.floor((x1 - SH_MIN) / SH_SIZE * SH_N);
        int j0 = (int) Math.floor((z0 - SH_MIN) / SH_SIZE * SH_N), j1 = (int) Math.floor((z1 - SH_MIN) / SH_SIZE * SH_N);
        i0 = Math.max(0, i0);
        j0 = Math.max(0, j0);
        i1 = Math.min(SH_N - 1, i1);
        j1 = Math.min(SH_N - 1, j1);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if ((d[j * SH_N + i] & 255) < v) d[j * SH_N + i] = (byte) v;
            }
        }
    }

    /** SH_N x SH_N bytes, row = z, column = x, value = top height / SH_MAXH * 255 */
    synchronized byte[] shelterMap() {
        if (shelter != null) return shelter;
        byte[] d = new byte[SH_N * SH_N];
        for (double[] o : obs) stamp(d, o[0] - o[2] / 2, o[1] - o[3] / 2, o[0] + o[2] / 2, o[1] + o[3] / 2, o[4]);
        for (double[] b : walls) stamp(d, b[0], b[2], b[3], b[5], b[4]);
        for (double[] r : roofs) stamp(d, r[0], r[1], r[2], r[3], r[4]);
        shelter = d;
        return d;
    }

    private boolean openGround(byte[] d, double x, double z, double r) {
        int i0 = (int) Math.floor((x - r - SH_MIN) / SH_SIZE * SH_N), i1 = (int) Math.floor((x + r - SH_MIN) / SH_SIZE * SH_N);
        int j0 = (int) Math.floor((z - r - SH_MIN) / SH_SIZE * SH_N), j1 = (int) Math.floor((z + r - SH_MIN) / SH_SIZE * SH_N);
        if (i0 < 0 || j0 < 0 || i1 >= SH_N || j1 >= SH_N) return false;
        for (int j = j0; j <= j1; j++) for (int i = i0; i <= i1; i++) if (d[j * SH_N + i] != 0) return false;
        return true;
    }

    /** rain puddles on open ground: x, z, radius, seed (flat list of 4 floats each) */
    float[] puddleSpots() {
        byte[] d = shelterMap();
        Random pr = new Random(4242L + obs.size() * 31L + lights.size());
        float[] out = new float[20 * 4];
        int n = 0;
        for (int tries = 0; tries < 900 && n < 20; tries++) {
            double x = (pr.nextDouble() * 2 - 1) * 40, z = (pr.nextDouble() * 2 - 1) * 40;
            double r = .6 + pr.nextDouble() * .95;
            if (!openGround(d, x, z, r + .6)) continue;
            boolean close = false;
            for (int k = 0; k < n; k++) {
                if (Math.hypot(out[k * 4] - x, out[k * 4 + 1] - z) < out[k * 4 + 2] + r + 2) close = true;
            }
            if (close) continue;
            out[n * 4] = (float) x;
            out[n * 4 + 1] = (float) z;
            out[n * 4 + 2] = (float) r;
            out[n * 4 + 3] = pr.nextFloat();
            n++;
        }
        float[] res = new float[n * 4];
        System.arraycopy(out, 0, res, 0, n * 4);
        return res;
    }

    boolean freeAt(double x, double z) {
        for (double[] o : obs) {
            if (Math.abs(x - o[0]) < o[2] / 2 + 1 && Math.abs(z - o[1]) < o[3] / 2 + 1) return false;
        }
        return true;
    }

    private Light light(int hex, double inten, double range, double x, double y, double z, boolean flick) {
        Light l = new Light();
        l.col = Mat.rgb(hex);
        l.inten = inten;
        l.range = range;
        l.x = x;
        l.y = y;
        l.z = z;
        l.flick = flick;
        lights.add(l);
        return l;
    }

    /* ---- walls and houses ---- */

    private void wallG(double x0, double z0, double len, boolean hor, double[][] gs, double h) {
        double p = 0;
        for (double[] g : gs) {
            double gp = g[0], gw = g[1];
            boolean wn = g.length > 2 && g[2] != 0;
            double a = gp - gw / 2, b = gp + gw / 2;
            seg(x0, z0, hor, p, a, h, 0, true);
            if (wn) {
                seg(x0, z0, hor, a, b, 1, 0, true);
                seg(x0, z0, hor, a, b, h - 2.3, 2.3, false);
                for (int k = 0; k < 3; k++) {
                    double rot = (rng.nextDouble() - .5) * .2;
                    mbr(hor ? gw : .1, .18, hor ? .1 : gw, hor ? x0 + gp : x0, 1.25 + k * .35, hor ? z0 : z0 + gp, mWood,
                            hor ? 0 : rot, 0, hor ? rot : 0);
                }
                double gy0 = 1, gy1 = h - 2.3;
                if (gy1 - gy0 > .2) {
                    pane(mGlass, hor ? x0 + gp : x0, (gy0 + gy1) / 2, hor ? z0 : z0 + gp, gw, gy1 - gy0, hor ? 0 : PI / 2, 1);
                }
            } else {
                seg(x0, z0, hor, a, b, h - 2.5, 2.5, false);
            }
            p = b;
        }
        seg(x0, z0, hor, p, len, h, 0, true);
    }

    private void pane(Mat m, double x, double y, double z, double pw, double ph, double ry, int type) {
        mesh(Prim.plane(pw, ph), m, x, y, z, 0, ry, 0);
        glass.add(new double[]{x, y, z, pw, ph, Math.sin(ry), Math.cos(ry), type});
    }

    private void mirror(double x, double z, double ry) {
        double nx = Math.sin(ry), nz = Math.cos(ry);
        pane(mMirror, x, 2.6, z, 14, 3.4, ry, 2);
        double tx = nz, tz = -nx;
        mb(Math.abs(tx) * 14.4 + Math.abs(nx) * .2 + .0, .2, Math.abs(tz) * 14.4 + Math.abs(nz) * .2, x, 4.4, z, mDark);
        mb(Math.abs(tx) * 14.4 + Math.abs(nx) * .2, .2, Math.abs(tz) * 14.4 + Math.abs(nz) * .2, x, .9, z, mDark);
        for (int s = -1; s <= 1; s += 2) {
            mb(Math.abs(tx) * .2 + Math.abs(nx) * .2, 3.7, Math.abs(tz) * .2 + Math.abs(nz) * .2, x + tx * 7.1 * s, 2.65, z + tz * 7.1 * s, mDark);
        }
    }

    private void seg(double x0, double z0, boolean hor, double a, double b, double hh, double y0, boolean solid) {
        double l = b - a;
        if (l < .05) return;
        double sx = hor ? l : .4, sz = hor ? .4 : l;
        double px = hor ? x0 + a + l / 2 : x0, pz = hor ? z0 : z0 + a + l / 2;
        mb(sx, hh, sz, px, y0 + hh / 2, pz, mBrick);
        if (solid) {
            solidBox(px, y0 + hh / 2, pz, sx, hh, sz);
            obs.add(new double[]{px, pz, sx, sz, y0 + hh});
        }
    }

    private static final double[][] NONE = new double[0][];

    private void house(double cx, double cz, double w, double d, double h, double[][] n, double[][] s, double[][] we, double[][] e) {
        wallG(cx - w / 2, cz - d / 2, w, true, n, h);
        wallG(cx - w / 2, cz + d / 2, w, true, s, h);
        wallG(cx - w / 2, cz - d / 2, d, false, we, h);
        wallG(cx + w / 2, cz - d / 2, d, false, e, h);
        mb(w + .8, .4, d + .8, cx, h + .2, cz, mConc);
        roofs.add(new double[]{cx - (w + .8) / 2, cz - (d + .8) / 2, cx + (w + .8) / 2, cz + (d + .8) / 2, h + .4});
        mesh(Prim.plane(w, d), mFloor, cx, .05, cz, -PI / 2, 0, 0);
    }

    private static double[][] g(double[]... a) {
        return a;
    }

    private static double[] d(double... a) {
        return a;
    }

    /* ---- text decals ---- */

    private Tex.Painter txt(String s, String col, int px) {
        return (c, W, H) -> {
            Paint p = new Paint();
            p.setAntiAlias(false);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setTextSize(px);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(android.graphics.Color.parseColor(col));
            c.drawText(s, W / 2f, H / 2f - (p.ascent() + p.descent()) / 2f, p);
        };
    }

    private Tex.Painter lab(String s, String bg, String fg, int px) {
        Tex.Painter t = txt(s, fg, px);
        return (c, W, H) -> {
            Tex.rect(c, bg, 0, 0, W, H);
            t.paint(c, W, H);
        };
    }

    private Tex.Painter grf(String s, int px) {
        Tex.Painter t = txt(s, "#a01010", px);
        final Random gr = new Random(s.hashCode() * 31L + px);
        return (c, W, H) -> {
            t.paint(c, W, H);
            for (int i = 0; i < 12; i++) {
                Tex.rect(c, "#7a0a0a", (int) (W * .1 + gr.nextDouble() * W * .8), (int) (H * .5 + px * .35), 2,
                        (int) (3 + gr.nextDouble() * H * .3));
            }
        };
    }

    private Tex.Painter chalk(String s) {
        return (c, W, H) -> {
            Paint p = new Paint();
            p.setAntiAlias(false);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(android.graphics.Color.parseColor("#e8e0a0"));
            c.drawRect(10, H * .36f, W - 10, H * .36f + H * .14f, p);
            c.drawRect(W * .38f, H * .5f, W * .38f + 16, H * .5f + H * .3f, p);
            c.drawRect(W * .08f, H * .46f, W * .08f + 24, H * .46f + H * .26f, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            p.setTextSize(11);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(s, W / 2f, H * .2f, p);
        };
    }

    private void decal(double w, double h, double x, double y, double z, double ry, Tex.Painter draw, boolean lit) {
        int ph = Math.max(16, (int) Math.round(128 * h / w));
        Mat m = Mat.textured(new Tex(128, ph, false, draw), 1);
        m.blend = true;
        m.depthWrite = false;
        m.unlit = !lit;
        m.offset = true;
        mesh(Prim.plane(w, h), m, x, y, z, 0, ry, 0);
    }

    /* ---- props ---- */

    private void car(double x, double z, boolean v) {
        box(x, z, v ? 1.8 : 4.2, v ? 4.2 : 1.8, 1, mRust);
        mb(v ? 1.5 : 2.2, .7, v ? 2.2 : 1.5, x, 1.35, z, mRust);
    }

    private void barrel(double x, double z, boolean fire) {
        box(x, z, .9, .9, 1.1, mRust);
        if (fire) {
            mb(.5, .4, .5, x, 1.3, z, Mat.basic(0xff7a22));
            light(0xff7722, 1.2, 14, x, 1.8, z, true);
        }
    }

    private void lamp(double x, double z) {
        mesh(Prim.cyl(.08, .08, 4.5, 5, false), mGun, x, 2.25, z);
        mesh(Prim.sphere(.25, 5, 4), mBulb, x, 4.5, z);
        light(0xffe0a0, 1.1, 22, x, 4.3, z, false);
    }

    private void perk(double x, double z, int c) {
        box(x, z, 1.2, 1, 2.2, Mat.color(c).emissive(c, .7));
    }

    private void tires(double x, double z) {
        for (int i = 0; i < 3; i++) mesh(Prim.cyl(.5, .5, .3, 8, false), mTire, x + (i == 2 ? .1 : 0), .15 + i * .3, z);
        obs.add(new double[]{x, z, 1, 1, .9});
    }

    private void dtree(double x, double z, double h) {
        mesh(Prim.cyl(.12, .28, h, 5, false), mWood, x, h / 2, z);
        for (int k = 0; k < 3; k++) {
            float[] m = Xf.mul(Xf.make(x, h * .55 + k * .4, z, rnd(-1, 1), rnd(0, 6.28), rnd(.6, 1.1), 1),
                    Xf.make(0, h * .2, 0, 0, 0, 0, 1));
            bake(Prim.cyl(.03, .09, h * .45, 4, false), mWood, m);
        }
        obs.add(new double[]{x, z, .6, .6, 4});
    }

    private void corpse(double x, double z, double r) {
        float[] gm = Xf.make(x, 0, z, 0, r, 0, 1);
        double[][] parts = {
                {.5, .22, .7, 0, .12, 0, 0, 0}, {.28, .28, .28, 0, .16, -.52, 1, 0}, {.15, .15, .7, -.13, .09, .62, 2, 0},
                {.15, .15, .7, .13, .09, .55, 2, 0}, {.12, .12, .55, .36, .08, -.15, 1, .5}, {.12, .12, .55, -.36, .08, -.3, 1, -.4}};
        Mat[] ms = {mShirt, mSkin, mPants};
        for (double[] p : parts) {
            float[] cm = Xf.mul(gm, Xf.make(p[3], p[4], p[5], 0, p[7], 0, 1));
            bake(Prim.box(p[0], p[1], p[2]), ms[(int) p[6]], cm);
        }
        mesh(Prim.circle(.9, 8), mPool, x, .055, z, -PI / 2, 0, 0);
    }

    private double rx, rz;
    private boolean roadV;
    private final ArrayList<double[]> zones = new ArrayList<>();

    private boolean spaceFree(double x, double z, double w, double d, double m) {
        if (Math.abs(x) - w / 2 < 6.5 && Math.abs(z) - d / 2 < 6.5) return false;
        if (Math.abs(x) + w / 2 > 42 || Math.abs(z) + d / 2 > 42) return false;
        for (double[] o : obs) {
            if (Math.abs(x - o[0]) < (w + o[2]) / 2 + m && Math.abs(z - o[1]) < (d + o[3]) / 2 + m) return false;
        }
        for (double[] o : zones) {
            if (Math.abs(x - o[0]) < (w + o[2]) / 2 + m && Math.abs(z - o[1]) < (d + o[3]) / 2 + m) return false;
        }
        return true;
    }

    private double[] find(double w, double d, double m, int tries) {
        for (int i = 0; i < tries; i++) {
            double x = rnd(-40, 40), z = rnd(-40, 40);
            if (spaceFree(x, z, w, d, m)) return new double[]{x, z};
        }
        return null;
    }

    private double[] findRoad(double w, double d, double m) {
        boolean h = !roadV || rng.nextBoolean();
        double t = rnd(-38, 38);
        double x = h ? t : rx + rnd(-2.5, 2.5), z = h ? rz + rnd(-2.5, 2.5) : t;
        if (!spaceFree(x, z, w, d, m)) return null;
        return new double[]{x, z, h ? 0 : 1};
    }

    private double[][] gaps(double len, boolean needDoor) {
        ArrayList<double[]> l = new ArrayList<>();
        int n = rng.nextInt(3);
        if (needDoor && n == 0) n = 1;
        for (int i = 0; i < n; i++) {
            boolean door = (needDoor && i == 0) || rng.nextInt(3) == 0;
            double gw = door ? new double[]{2.6, 3, 3.2}[rng.nextInt(3)] : 1.6;
            for (int t = 0; t < 12; t++) {
                double gp = rnd(1.2 + gw / 2, len - 1.2 - gw / 2);
                boolean ok = true;
                for (double[] o : l) if (Math.abs(o[0] - gp) < (o[1] + gw) / 2 + 1.2) ok = false;
                if (ok) {
                    l.add(door ? d(gp, gw) : d(gp, gw, 1));
                    break;
                }
            }
        }
        l.sort((a, b) -> Double.compare(a[0], b[0]));
        return l.toArray(new double[0][]);
    }

    private boolean nearGap(double[][] gs, double along, double half) {
        for (double[] o : gs) if (Math.abs(o[0] - along) < o[1] / 2 + half + 1) return true;
        return false;
    }

    private boolean clearOfObs(double x, double z, double w, double d) {
        for (double[] o : obs) {
            if (Math.abs(x - o[0]) < (w + o[2]) / 2 + .3 && Math.abs(z - o[1]) < (d + o[3]) / 2 + .3) return false;
        }
        return true;
    }

    private static final String[] TAGS = {"NO ESCAPE", "RUN OR DIE", "THEY COME", "HELP US", "DON'T STOP", "TOO LATE",
            "BEHIND YOU", "NO HOPE", "STAY AWAY", "DEAD INSIDE", "SURVIVE", "NO MERCY", "RUN", "DEAD ZONE"};

    private void randHouse(double x, double z, double w, double d, double h) {
        int dw = rng.nextInt(4);
        int d2 = rng.nextBoolean() ? (dw + 2) % 4 : -1;
        double[][] n = gaps(w, dw == 0 || d2 == 0);
        double[][] s = gaps(w, dw == 1 || d2 == 1);
        double[][] we = gaps(d, dw == 2 || d2 == 2);
        double[][] e = gaps(d, dw == 3 || d2 == 3);
        house(x, z, w, d, h, n, s, we, e);

        int nf = 2 + rng.nextInt(3);
        for (int i = 0; i < nf; i++) {
            int side = rng.nextInt(4);
            double fw = rnd(1.2, 2.4), fd = rnd(.8, 1.4), fh = rnd(.5, 1);
            Mat fm = rng.nextBoolean() ? mWood : mCloth;
            if (side < 2) {
                double fx = x + rnd(-w / 2 + 2, w / 2 - 2), fz = side == 0 ? z - d / 2 + .9 : z + d / 2 - .9;
                if (nearGap(side == 0 ? n : s, fx - (x - w / 2), fw / 2) || !clearOfObs(fx, fz, fw, fd)) continue;
                box(fx, fz, fw, fd, fh, fm);
            } else {
                double fz = z + rnd(-d / 2 + 2, d / 2 - 2), fx = side == 2 ? x - w / 2 + .9 : x + w / 2 - .9;
                if (nearGap(side == 2 ? we : e, fz - (z - d / 2), fw / 2) || !clearOfObs(fx, fz, fd, fw)) continue;
                box(fx, fz, fd, fw, fh, fm);
            }
        }
        mesh(Prim.sphere(.18, 5, 4), mBulb, x, h - .7, z);
        mesh(Prim.cyl(.01, .01, .8, 3, false), mGun, x, h - .2, z);
        light(0xffc070, 1, 12, x, h - .9, z, rng.nextInt(3) == 0);

        int nd = 1 + rng.nextInt(2);
        for (int i = 0; i < nd; i++) {
            int wl = rng.nextInt(4);
            String tx = TAGS[rng.nextInt(TAGS.length)];
            double len = wl < 2 ? w : d;
            double[][] gs = wl == 0 ? n : wl == 1 ? s : wl == 2 ? we : e;
            for (int t = 0; t < 8; t++) {
                double along = rnd(1.8, len - 1.8);
                if (nearGap(gs, along, 1.6)) continue;
                double dwid = 3, y = 2.6;
                if (wl == 0) decal(dwid, 1.2, x - w / 2 + along, y, z - d / 2 - .25, PI, grf(tx, 17), true);
                else if (wl == 1) decal(dwid, 1.2, x - w / 2 + along, y, z + d / 2 + .25, 0, grf(tx, 17), true);
                else if (wl == 2) decal(dwid, 1.2, x - w / 2 - .25, y, z - d / 2 + along, -PI / 2, grf(tx, 17), true);
                else decal(dwid, 1.2, x + w / 2 + .25, y, z - d / 2 + along, PI / 2, grf(tx, 17), true);
                break;
            }
        }
    }

    private void build() {
        mesh(Prim.plane(400, 400), mGround, 0, 0, 0, -PI / 2, 0, 0);
        box(0, -46, 96, 2, 6, mBrick);
        box(0, 46, 96, 2, 6, mBrick);
        box(-46, 0, 2, 96, 6, mBrick);
        box(46, 0, 2, 96, 6, mBrick);
        mirror(0, -44.9, 0);
        mirror(0, 44.9, PI);
        mirror(-44.9, 0, PI / 2);
        mirror(44.9, 0, -PI / 2);

        rz = rnd(-14, 14);
        rx = rnd(-14, 14);
        roadV = rng.nextInt(4) != 0;
        mesh(Prim.plane(92, 8), mRd, 0, .03, rz, -PI / 2, 0, 0);
        if (roadV) mesh(Prim.plane(92, 8), mRd, rx, .03, 0, -PI / 2, 0, PI / 2);

        int nh = 4 + rng.nextInt(4);
        for (int a = 0, n = 0; a < 300 && n < nh; a++) {
            double w = Math.round(rnd(9, 17) * 2) / 2.0, d = Math.round(rnd(8, 13) * 2) / 2.0;
            double h = new double[]{3.6, 4, 5}[rng.nextInt(3)];
            double x = rnd(-38 + w / 2, 38 - w / 2), z = rnd(-38 + d / 2, 38 - d / 2);
            if (Math.abs(z - rz) < 5.5 + d / 2) continue;
            if (roadV && Math.abs(x - rx) < 5.5 + w / 2) continue;
            if (!spaceFree(x, z, w + 2, d + 2, 3)) continue;
            zones.add(new double[]{x, z, w, d});
            randHouse(x, z, w, d, h);
            n++;
        }

        int nc = 8 + rng.nextInt(5);
        for (int i = 0, n = 0; i < 120 && n < nc; i++) {
            boolean onRoad = rng.nextInt(10) < 7;
            double[] p = onRoad ? findRoad(4.4, 4.4, 1) : find(4.4, 4.4, 1, 1);
            if (p == null) continue;
            boolean v = onRoad ? p[2] == 1 : rng.nextBoolean();
            car(p[0], p[1], v);
            n++;
        }
        int nb = 6 + rng.nextInt(4), fires = 0;
        for (int i = 0, n = 0; i < 80 && n < nb; i++) {
            double[] p = find(.9, .9, 1, 1);
            if (p == null) continue;
            boolean fire = fires < 3 && rng.nextInt(3) == 0;
            if (fire) fires++;
            barrel(p[0], p[1], fire);
            n++;
        }
        int ncr = 10 + rng.nextInt(7);
        for (int i = 0, n = 0; i < 120 && n < ncr; i++) {
            double s = rnd(1, 1.8);
            double[] p = find(s, s, 1, 1);
            if (p == null) continue;
            box(p[0], p[1], s, s, s, mCrate);
            if (rng.nextBoolean() && spaceFree(p[0] + s + .1, p[1], s, s, .2)) box(p[0] + s + .1, p[1], s, s, s, mCrate);
            n++;
        }
        int nsb = 3 + rng.nextInt(3);
        for (int i = 0, n = 0; i < 80 && n < nsb; i++) {
            boolean hz = rng.nextBoolean();
            double[] p = find(hz ? 6 : .8, hz ? .8 : 6, 1, 1);
            if (p == null) continue;
            box(p[0], p[1], hz ? 6 : .8, hz ? .8 : 6, .9, mSand);
            n++;
        }
        int nbar = 5 + rng.nextInt(4);
        for (int i = 0, n = 0; i < 100 && n < nbar; i++) {
            double ang = rnd(0, 6.28), r = rnd(12, 32);
            double x = Math.cos(ang) * r, z = Math.sin(ang) * r;
            boolean hz = rng.nextBoolean();
            if (!spaceFree(x, z, hz ? 2.6 : .9, hz ? .9 : 2.6, 1)) continue;
            box(x, z, hz ? 2.6 : .9, hz ? .9 : 2.6, 1, mConc);
            n++;
        }
        int[] perkCol = {0xcc2222, 0xe0b020, 0x2288dd, 0xdd6610};
        String[] perkTxt = {"JUGGER-NOG", "SPEED COLA", "QUICK REVIVE", "DOUBLE TAP"};
        for (int k = 0; k < 4; k++) {
            double[] p = find(1.2, 1, 2.5, 60);
            if (p == null) continue;
            perk(p[0], p[1], perkCol[k]);
            String s = perkTxt[k];
            decal(1.1, .55, p[0], 1.7, p[1] + .52, 0, lab(s, "#111111", "#eeeeee", s.length() > 10 ? 11 : 13), true);
        }
        double[] pp = find(1.8, 1.2, 2.5, 60);
        if (pp != null) {
            box(pp[0], pp[1], 1.8, 1.2, 2.6, Mat.color(0x4a1a7a).emissive(0x2a0a55, .8));
            mesh(Prim.sphere(.3, 6, 5), Mat.basic(0xc070ff), pp[0], 2.9, pp[1]);
            decal(1.6, .5, pp[0], 1.6, pp[1] + .62, 0, lab("PACK-A-PUNCH", "#2a0a40", "#d9a0ff", 11), true);
            light(0xaa55ff, 1.3, 12, pp[0], 2.5, pp[1] + 1, false);
        }
        double[] mb2 = find(1.7, .9, 2.5, 60);
        if (mb2 != null) {
            box(mb2[0], mb2[1], 1.7, .9, .8, mCrate);
            Mat beam = Mat.basic(0x66ccff).alpha(.22);
            beam.additive = true;
            mesh(Prim.cyl(.7, .7, 8, 8, true), beam, mb2[0], 4.4, mb2[1]);
            mb(1.5, .1, .5, mb2[0], .86, mb2[1], Mat.basic(0xffd24a));
            light(0x55aaff, 1.1, 11, mb2[0], 2.2, mb2[1], false);
        }

        for (int i = 0, n = 0, want = 12 + rng.nextInt(7); i < 200 && n < want; i++) {
            double[] p = find(.6, .6, 1, 1);
            if (p == null) continue;
            box(p[0], p[1], .6, .6, 2.5 + rng.nextDouble() * 2, mWood);
            n++;
        }
        for (int i = 0, n = 0, want = 8 + rng.nextInt(5); i < 200 && n < want; i++) {
            double[] p = find(.6, .6, 1.5, 1);
            if (p == null) continue;
            dtree(p[0], p[1], rnd(4, 6.5));
            n++;
        }
        for (int i = 0, n = 0, want = 4 + rng.nextInt(4); i < 100 && n < want; i++) {
            double[] p = find(1, 1, 1, 1);
            if (p == null) continue;
            tires(p[0], p[1]);
            n++;
        }

        double[] gv = find(14, 9.5, 1, 40);
        if (gv != null) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 5; c++) box(gv[0] - 5.2 + c * 2.6, gv[1] - 2.8 + r * 2.8, .8, .3, 1 + rng.nextDouble() * .4, mConc);
            }
            box(gv[0] + 5.7, gv[1] + 4.5, .2, .2, 2.4, mWood);
            mb(1.1, .2, .2, gv[0] + 5.7, 2, gv[1] + 4.5, mWood);
        }
        double[] bk = find(6.5, 6, 2, 60);
        if (bk != null) {
            box(bk[0], bk[1] - 2, 5, .8, .9, mSand);
            box(bk[0] - 2.4, bk[1], .8, 4, .9, mSand);
            box(bk[0] + 2.4, bk[1], .8, 4, .9, mSand);
            box(bk[0], bk[1] - .5, 1.2, 1.2, 1.2, mCrate);
        }
        double[] tw = find(4.4, 4.4, 2, 60);
        if (tw != null) {
            double tx = tw[0], tz = tw[1];
            for (int a = -1; a <= 1; a += 2) for (int b2 = -1; b2 <= 1; b2 += 2) box(tx + a * 1.2, tz + b2 * 1.2, .3, .3, 5, mWood);
            mb(3.8, .25, 3.8, tx, 5.1, tz, mWood);
            mb(3.8, .9, .12, tx, 5.7, tz + 1.9, mWood);
            mb(3.8, .9, .12, tx, 5.7, tz - 1.9, mWood);
        }
        double[] tk = find(9.4, 3.2, 2, 60);
        if (tk != null) {
            box(tk[0], tk[1], 9, 2.6, 3, mRust);
            mb(7.6, .7, 2.7, tk[0], 2.1, tk[1], mDark);
            mb(.05, 1.6, 2.2, tk[0] + 4.55, 1.9, tk[1], mDark);
        }
        if (rng.nextBoolean()) {
            boolean hz = rng.nextBoolean();
            double edge = (rng.nextBoolean() ? -1 : 1) * 40, st = rnd(-28, 8);
            for (int i = 0; i < 10; i++) {
                double fx = hz ? st + i * 3 : edge, fz = hz ? edge : st + i * 3;
                if (spaceFree(fx, fz, .2, .2, .3)) box(fx, fz, hz ? 2.6 : .2, hz ? .2 : 2.6, 1.6, mWood);
            }
        }

        for (int t : new int[]{-30, -12, 12, 30}) {
            double side = (t > 0 ? 1 : -1) * 5.5;
            lamp(t, rz + side);
            if (roadV) lamp(rx - side, t);
        }
        for (int a : new int[]{-40, -24, -8, 8, 24, 40}) {
            for (int s = -1; s <= 1; s += 2) {
                box(a, s * 44.2, 1.6, 1.6, 7, mConc);
                box(s * 44.2, a, 1.6, 1.6, 7, mConc);
                mesh(Prim.sphere(.18, 5, 4), mBulb, a, 6.3, s * 43.2);
                mesh(Prim.sphere(.18, 5, 4), mBulb, s * 43.2, 6.3, a);
            }
        }
        for (int wl = 0; wl < 4; wl++) {
            for (int k = 0; k < 2; k++) {
                double t = (rng.nextBoolean() ? -1 : 1) * rnd(12, 32);
                String s = TAGS[rng.nextInt(TAGS.length)];
                Tex.Painter gp = grf(s, 18);
                if (wl == 0) decal(6, 1.6, t, 3.4, -44.85, 0, gp, true);
                else if (wl == 1) decal(6, 1.6, t, 3.4, 44.85, PI, gp, true);
                else if (wl == 2) decal(6, 1.6, -44.85, 3.4, t, PI / 2, gp, true);
                else decal(6, 1.6, 44.85, 3.4, t, -PI / 2, gp, true);
            }
        }

        for (int i = 0, n = 0, want = 5 + rng.nextInt(5); i < 100 && n < want; i++) {
            double x = rnd(-40, 40), z = rnd(-40, 40);
            if (Math.abs(x) < 3 && Math.abs(z) < 3) continue;
            corpse(x, z, rnd(0, 6.28));
            n++;
        }
        for (int i = 0, n = 0; i < 200 && n < 16; i++) {
            double x = rnd(-42, 42), z = rnd(-42, 42);
            if (freeAt(x, z)) {
                mesh(Prim.circle(rnd(.6, 1.8), 8), mPool, x, .045, z, -PI / 2, 0, 0);
                n++;
            }
        }
        for (int i = 0, n = 0; i < 200 && n < 16; i++) {
            double x = rnd(-42, 42), z = rnd(-42, 42);
            if (freeAt(x, z)) {
                mesh(Prim.circle(rnd(2, 5), 9), mMud, x, .02, z, -PI / 2, 0, 0);
                n++;
            }
        }
        for (int i = 0, n = 0; i < 200 && n < 22; i++) {
            double x = rnd(-42, 42), z = rnd(-42, 42);
            if (freeAt(x, z)) {
                mbr(rnd(1, 2.2), .06, .22, x, .08, z, mWood, rnd(-.15, .15), rnd(0, 6.28), rnd(-.15, .15));
                n++;
            }
        }
        for (int i = 0, n = 0; i < 200 && n < 30; i++) {
            double x = rnd(-42, 42), z = rnd(-42, 42);
            if (freeAt(x, z)) {
                mbr(rnd(.2, .5), rnd(.15, .4), rnd(.2, .5), x, .12, z, mConc, rnd(-.4, .4), rnd(0, 6.28), rnd(-.4, .4));
                n++;
            }
        }
    }

    /* ---- raycast against walls ---- */

    double rayWalls(double ox, double oy, double oz, double dx, double dy, double dz) {
        double best = -1;
        for (double[] w : walls) {
            double t = Hit.rayBox(ox, oy, oz, dx, dy, dz, w[0], w[1], w[2], w[3], w[4], w[5]);
            if (t >= 0 && (best < 0 || t < best)) best = t;
        }
        return best;
    }

    void draw(boolean blendPass) {
        ArrayList<Batch> list = blendPass ? blend : solid;
        for (Batch b : list) {
            Gfx.useMat(b.mat);
            Gfx.draw(b.geo, Xf.ID);
        }
    }
}
