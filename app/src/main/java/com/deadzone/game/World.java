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

    final ArrayList<double[]> obs = new ArrayList<>();
    final ArrayList<double[]> walls = new ArrayList<>();
    final ArrayList<Light> lights = new ArrayList<>();
    final ArrayList<Batch> solid = new ArrayList<>();
    final ArrayList<Batch> blend = new ArrayList<>();

    final Random rng = new Random();
    private final LinkedHashMap<Mat, FloatList> acc = new LinkedHashMap<>();

    Mat mGround, mBrick, mWood, mSkin, mShirt, mPants, mGun, mConc, mCrate, mRust, mSand, mFloor, mRd, mCloth;
    Mat mTire, mDark, mBulb, mPool, mMud;
    private Geo sg;

    World() {
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
        mGround = T((c, w, h) -> {
            Tex.noise(c, 62, 70, 44, 55);
            Tex.blot(c, "#2c2a1c", 10);
        }, 50);
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
        obs.add(new double[]{x, z, w, d});
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
            } else {
                seg(x0, z0, hor, a, b, h - 2.5, 2.5, false);
            }
            p = b;
        }
        seg(x0, z0, hor, p, len, h, 0, true);
    }

    private void seg(double x0, double z0, boolean hor, double a, double b, double hh, double y0, boolean solid) {
        double l = b - a;
        if (l < .05) return;
        double sx = hor ? l : .4, sz = hor ? .4 : l;
        double px = hor ? x0 + a + l / 2 : x0, pz = hor ? z0 : z0 + a + l / 2;
        mb(sx, hh, sz, px, y0 + hh / 2, pz, mBrick);
        if (solid) {
            solidBox(px, y0 + hh / 2, pz, sx, hh, sz);
            obs.add(new double[]{px, pz, sx, sz});
        }
    }

    private static final double[][] NONE = new double[0][];

    private void house(double cx, double cz, double w, double d, double h, double[][] n, double[][] s, double[][] we, double[][] e) {
        wallG(cx - w / 2, cz - d / 2, w, true, n, h);
        wallG(cx - w / 2, cz + d / 2, w, true, s, h);
        wallG(cx - w / 2, cz - d / 2, d, false, we, h);
        wallG(cx + w / 2, cz - d / 2, d, false, e, h);
        mb(w + .8, .4, d + .8, cx, h + .2, cz, mConc);
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
        return (c, W, H) -> {
            t.paint(c, W, H);
            for (int i = 0; i < 12; i++) {
                Tex.rect(c, "#7a0a0a", (int) (W * .1 + rng.nextDouble() * W * .8), (int) (H * .5 + px * .35), 2,
                        (int) (3 + rng.nextDouble() * H * .3));
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
        obs.add(new double[]{x, z, 1, 1});
    }

    private void dtree(double x, double z, double h) {
        mesh(Prim.cyl(.12, .28, h, 5, false), mWood, x, h / 2, z);
        for (int k = 0; k < 3; k++) {
            float[] m = Xf.mul(Xf.make(x, h * .55 + k * .4, z, rnd(-1, 1), rnd(0, 6.28), rnd(.6, 1.1), 1),
                    Xf.make(0, h * .2, 0, 0, 0, 0, 1));
            bake(Prim.cyl(.03, .09, h * .45, 4, false), mWood, m);
        }
        obs.add(new double[]{x, z, .6, .6});
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

    private void build() {
        mesh(Prim.plane(200, 200), mGround, 0, 0, 0, -PI / 2, 0, 0);
        box(0, -46, 96, 2, 6, mBrick);
        box(0, 46, 96, 2, 6, mBrick);
        box(-46, 0, 2, 96, 6, mBrick);
        box(46, 0, 2, 96, 6, mBrick);
        for (int r = 0; r < 2; r++) mesh(Prim.plane(92, 8), mRd, 0, .03, 0, -PI / 2, 0, r == 1 ? PI / 2 : 0);

        house(-20, -22, 14, 10, 4, g(d(3, 1.6, 1), d(10, 1.6, 1)), g(d(4, 2.6), d(10, 1.6, 1)), g(d(5, 1.6, 1)), g(d(5, 2.6)));
        house(22, -22, 12, 10, 4, g(d(6, 2.6)), g(d(3, 3.2), d(8, 1.6, 1)), g(d(5, 2.6)), g(d(4, 1.6, 1)));
        house(-24, 22, 16, 12, 5, g(d(8, 3.2)), g(d(4, 3.2), d(12, 3.2)), g(d(6, 1.6, 1)), g(d(6, 2.6)));
        house(24, 24, 10, 10, 3.6, g(d(5, 3)), g(d(5, 3)), NONE, g(d(5, 2.6)));

        box(-22, -24, 2.4, 1.2, .9, mWood);
        box(-16, -25, 1, 1, 1, mCrate);
        box(22, -25, 7, .8, 1.1, mWood);
        box(18, -19, 1, 1, 1, mCrate);
        box(26, -19, 1, 1, 1, mCrate);
        box(-30, 26, 1.4, 1.4, 1.4, mCrate);
        box(-30, 24.4, 1.4, 1.4, 1.4, mCrate);
        box(-18, 27, 3, 1, 2, mCrate);
        box(-20, 19, 1.2, 1.2, 1.2, mCrate);
        box(27, 28, 1.5, 1.5, 1.5, mCrate);

        car(-10, 1.5, false);
        car(14, -2, false);
        car(2, 12, true);
        car(-3, -14, true);
        car(34, -4, true);
        barrel(-12, 7, true);
        barrel(12, -9, true);
        barrel(6, 15, false);
        barrel(-6, -12, false);
        barrel(-13, 8, false);
        lamp(5.5, 5.5);
        lamp(-5.5, -5.5);
        box(0, -9, 6, .8, .9, mSand);
        box(-9, 9, .8, 6, .9, mSand);
        box(9, 9, 6, .8, .9, mSand);
        box(31, 5, 1.2, 1.2, 1.2, mCrate);
        box(32.2, 5, 1.2, 1.2, 1.2, mCrate);
        box(31.6, 5, 1.2, 1.2, 1.2, mCrate);
        box(-34, -4, 1.3, 1.3, 1.3, mCrate);
        box(10, 12, 3, 3, .8, mConc);
        box(10, 12, 1.2, 1.2, 3.5, mConc);
        for (int z = -40; z <= -10; z += 3) box(40, z, .2, 2.6, 1.6, mWood);
        perk(-12, -25, 0xcc2222);
        perk(15, -17, 0xe0b020);
        perk(-30, 15, 0x2288dd);
        perk(29, 18, 0xdd6610);
        for (int i = 0, n = 0; i < 300 && n < 16; i++) {
            double x = (rng.nextDouble() - .5) * 84, z = (rng.nextDouble() - .5) * 84;
            if (Math.abs(x) > 6 && Math.abs(z) > 6 && freeAt(x, z)) {
                box(x, z, .6, .6, 2.5 + rng.nextDouble() * 2, mWood);
                n++;
            }
        }

        // graffiti and labels
        decal(3.2, 1.2, -20.5, 2.9, -27.25, PI, grf("NO ESCAPE", 18), true);
        decal(3.2, 1.2, -19.8, 2.9, -16.75, 0, grf("RUN OR DIE", 18), true);
        decal(2.6, 1.2, 18.2, 2.6, -27.25, PI, grf("THEY COME", 16), true);
        decal(3, 1.2, -15.75, 2.8, 25.5, PI / 2, grf("HELP US", 18), true);
        decal(3.4, 1.2, 18.75, 2.2, 24, -PI / 2, grf("DON'T STOP", 17), true);
        decal(8, 2, -18, 3.4, -44.85, 0, grf("SURVIVE", 22), true);
        decal(8, 2, 16, 3.4, -44.85, 0, grf("DEAD ZONE", 20), true);
        decal(6, 1.6, -16, 3.4, 44.85, PI, grf("NO MERCY", 20), true);
        decal(6, 1.6, 16, 3.4, 44.85, PI, grf("RUN", 24), true);
        decal(6, 1.6, -44.85, 3.4, -16, PI / 2, grf("THEY COME", 16), true);
        decal(6, 1.6, 44.85, 3.4, 16, -PI / 2, grf("HELP US", 20), true);
        decal(2, .9, -19.75, 1.4, -16.75, 0, chalk("M14  500"), false);
        decal(2.8, 1.1, -24, 1.5, 28.25, 0, chalk("MP5  1000"), false);

        for (int a : new int[]{-40, -24, -8, 8, 24, 40}) {
            for (int s = -1; s <= 1; s += 2) {
                box(a, s * 44.2, 1.6, 1.6, 7, mConc);
                box(s * 44.2, a, 1.6, 1.6, 7, mConc);
                mesh(Prim.sphere(.18, 5, 4), mBulb, a, 6.3, s * 43.2);
                mesh(Prim.sphere(.18, 5, 4), mBulb, s * 43.2, 6.3, a);
            }
        }

        Object[][] perkLabels = {{-12.0, -25.0, "JUGGER-NOG"}, {15.0, -17.0, "SPEED COLA"}, {-30.0, 15.0, "QUICK REVIVE"}, {29.0, 18.0, "DOUBLE TAP"}};
        for (Object[] pl : perkLabels) {
            String s = (String) pl[2];
            decal(1.1, .55, (Double) pl[0], 1.7, (Double) pl[1] + .52, 0, lab(s, "#111111", "#eeeeee", s.length() > 10 ? 11 : 13), true);
        }

        // pack-a-punch
        box(36, -14, 1.8, 1.2, 2.6, Mat.color(0x4a1a7a).emissive(0x2a0a55, .8));
        mesh(Prim.sphere(.3, 6, 5), Mat.basic(0xc070ff), 36, 2.9, -14);
        decal(1.6, .5, 36, 1.6, -13.38, 0, lab("PACK-A-PUNCH", "#2a0a40", "#d9a0ff", 11), true);
        light(0xaa55ff, 1.3, 12, 36, 2.5, -13, false);

        // mystery box with a beam
        box(8, -15, 1.7, .9, .8, mCrate);
        Mat beam = Mat.basic(0x66ccff).alpha(.22);
        beam.additive = true;
        mesh(Prim.cyl(.7, .7, 8, 8, true), beam, 8, 4.4, -15);
        mb(1.5, .1, .5, 8, .86, -15, Mat.basic(0xffd24a));
        light(0x55aaff, 1.1, 11, 8, 2.2, -15, false);

        // furniture and hanging bulbs
        box(-26.4, -21.5, .6, 3, 2, mWood);
        box(-15.5, -19.5, 1.2, 2.2, .5, mCloth);
        box(27.4, -20, .6, 3, 2, mWood);
        box(17, -25.5, .9, .9, 1.1, mRust);
        box(-28.5, 19, 2.2, 1.2, .5, mCloth);
        box(-31.4, 19.5, .6, 3, 2, mWood);
        box(-24, 23, 2, 1.2, .9, mWood);
        box(-25.6, 23, .6, .6, .5, mWood);
        box(-22.4, 23, .6, .6, .5, mWood);
        box(21.5, 22, 1.8, 1.2, .9, mWood);
        box(27, 20.5, 3, 1, .6, mCloth);
        double[][] bulbs = {{-20, 3.3, -22}, {22, 3.3, -22}, {-24, 4.3, 22}, {24, 2.9, 24}};
        for (double[] b : bulbs) {
            mesh(Prim.sphere(.18, 5, 4), mBulb, b[0], b[1], b[2]);
            mesh(Prim.cyl(.01, .01, .8, 3, false), mGun, b[0], b[1] + .5, b[2]);
        }
        light(0xffc070, 1, 12, -20, 3.1, -22, true);

        // barricades and wrecks
        double[][] bar = {{-30, -2.4, .9, 2.6}, {-30, 2.4, .9, 2.6}, {30, -2.4, .9, 2.6}, {30, 2.4, .9, 2.6}, {-2.4, -30, 2.6, .9}, {2.4, -30, 2.6, .9}};
        for (double[] b : bar) box(b[0], b[1], b[2], b[3], 1, mConc);
        car(-24, -2.5, false);
        car(-37, 3, false);
        car(26, 3, false);
        car(1, -27, true);
        car(-1, 24, true);
        box(0, 34, 9, 2.6, 3, mRust);
        mb(7.6, .7, 2.7, 0, 2.1, 34, mDark);
        mb(.05, 1.6, 2.2, 4.55, 1.9, 34, mDark);

        // bunker, tower, graveyard
        box(-32, -30, 5, .8, .9, mSand);
        box(-34.4, -28, .8, 4, .9, mSand);
        box(-29.6, -28, .8, 4, .9, mSand);
        box(-32, -28.5, 1.2, 1.2, 1.2, mCrate);
        double[][] legs = {{34.8, -37.2}, {37.2, -37.2}, {34.8, -34.8}, {37.2, -34.8}};
        for (double[] l : legs) box(l[0], l[1], .3, .3, 5, mWood);
        mb(3.8, .25, 3.8, 36, 5.1, -36, mWood);
        mb(3.8, .9, .12, 36, 5.7, -34.1, mWood);
        mb(3.8, .9, .12, 36, 5.7, -37.9, mWood);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 5; c++) box(-41 + c * 2.6, -42 + r * 2.8, .8, .3, 1 + rng.nextDouble() * .4, mConc);
        box(-35.5, -33.3, .2, .2, 2.4, mWood);
        mb(1.1, .2, .2, -35.5, 2, -33.3, mWood);
        tires(-8, 20);
        tires(-7, 21.2);
        tires(8, -30);
        tires(36, 32);
        tires(-36, -12);
        for (int i = 0, n = 0; i < 300 && n < 10; i++) {
            double x = rnd(-42, 42), z = rnd(-42, 42);
            if (Math.abs(x) > 6 && Math.abs(z) > 6 && freeAt(x, z)) {
                dtree(x, z, rnd(4, 6.5));
                n++;
            }
        }
        double[][] bodies = {{-18, -24.5}, {24, -20.5}, {-26, 24}, {2, 5}, {-8, -2}, {20, 8}};
        for (double[] b : bodies) corpse(b[0], b[1], rnd(0, 6.28));

        // ground clutter
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
        double[][] sl = {{-14, 4.5}, {14, -4.5}, {-4.5, -26}, {4.5, 14}, {-26, 4.5}, {26, -4.5}, {4.5, 26}, {-4.5, -14}};
        for (double[] p : sl) {
            mesh(Prim.cyl(.07, .07, 4.2, 5, false), mGun, p[0], 2.1, p[1]);
            mesh(Prim.sphere(.18, 5, 4), mBulb, p[0], 4.3, p[1]);
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
