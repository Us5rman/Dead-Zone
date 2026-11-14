package com.deadzone.game;

final class Prim {
    private Prim() {
    }

    private static void v(FloatList f, double x, double y, double z, double nx, double ny, double nz, double u, double w) {
        f.add((float) x);
        f.add((float) y);
        f.add((float) z);
        f.add((float) nx);
        f.add((float) ny);
        f.add((float) nz);
        f.add((float) u);
        f.add((float) w);
    }

    private static void quad(FloatList f, double[] a, double[] b, double[] c, double[] d, double nx, double ny, double nz) {
        v(f, a[0], a[1], a[2], nx, ny, nz, 0, 0);
        v(f, b[0], b[1], b[2], nx, ny, nz, 1, 0);
        v(f, c[0], c[1], c[2], nx, ny, nz, 1, 1);
        v(f, a[0], a[1], a[2], nx, ny, nz, 0, 0);
        v(f, c[0], c[1], c[2], nx, ny, nz, 1, 1);
        v(f, d[0], d[1], d[2], nx, ny, nz, 0, 1);
    }

    private static double[] p(double x, double y, double z) {
        return new double[]{x, y, z};
    }

    static float[] box(double w, double h, double d) {
        double x = w / 2, y = h / 2, z = d / 2;
        FloatList f = new FloatList();
        quad(f, p(-x, -y, z), p(x, -y, z), p(x, y, z), p(-x, y, z), 0, 0, 1);
        quad(f, p(x, -y, -z), p(-x, -y, -z), p(-x, y, -z), p(x, y, -z), 0, 0, -1);
        quad(f, p(x, -y, z), p(x, -y, -z), p(x, y, -z), p(x, y, z), 1, 0, 0);
        quad(f, p(-x, -y, -z), p(-x, -y, z), p(-x, y, z), p(-x, y, -z), -1, 0, 0);
        quad(f, p(-x, y, z), p(x, y, z), p(x, y, -z), p(-x, y, -z), 0, 1, 0);
        quad(f, p(-x, -y, -z), p(x, -y, -z), p(x, -y, z), p(-x, -y, z), 0, -1, 0);
        return f.toArray();
    }

    static float[] plane(double w, double h) {
        double x = w / 2, y = h / 2;
        FloatList f = new FloatList();
        quad(f, p(-x, -y, 0), p(x, -y, 0), p(x, y, 0), p(-x, y, 0), 0, 0, 1);
        return f.toArray();
    }

    static float[] circle(double r, int seg) {
        FloatList f = new FloatList();
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            v(f, 0, 0, 0, 0, 0, 1, .5, .5);
            v(f, Math.cos(a0) * r, Math.sin(a0) * r, 0, 0, 0, 1, 0, 0);
            v(f, Math.cos(a1) * r, Math.sin(a1) * r, 0, 0, 0, 1, 1, 0);
        }
        return f.toArray();
    }

    static float[] cyl(double rt, double rb, double h, int seg, boolean open) {
        FloatList f = new FloatList();
        double hh = h / 2;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg, am = (a0 + a1) / 2;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            quad(f, p(rb * c0, -hh, rb * s0), p(rb * c1, -hh, rb * s1), p(rt * c1, hh, rt * s1), p(rt * c0, hh, rt * s0),
                    Math.cos(am), 0, Math.sin(am));
            if (!open) {
                v(f, 0, hh, 0, 0, 1, 0, .5, .5);
                v(f, rt * c0, hh, rt * s0, 0, 1, 0, 0, 0);
                v(f, rt * c1, hh, rt * s1, 0, 1, 0, 1, 0);
                v(f, 0, -hh, 0, 0, -1, 0, .5, .5);
                v(f, rb * c0, -hh, rb * s0, 0, -1, 0, 0, 0);
                v(f, rb * c1, -hh, rb * s1, 0, -1, 0, 1, 0);
            }
        }
        return f.toArray();
    }

    private static double[] sp(double r, int x, int y, int ws, int hs) {
        double th = Math.PI * 2 * x / ws, ph = Math.PI * y / hs;
        return new double[]{r * Math.cos(th) * Math.sin(ph), r * Math.cos(ph), r * Math.sin(th) * Math.sin(ph)};
    }

    private static void tri(FloatList f, double[] a, double[] b, double[] c, double r) {
        v(f, a[0], a[1], a[2], a[0] / r, a[1] / r, a[2] / r, 0, 0);
        v(f, b[0], b[1], b[2], b[0] / r, b[1] / r, b[2] / r, 1, 0);
        v(f, c[0], c[1], c[2], c[0] / r, c[1] / r, c[2] / r, 1, 1);
    }

    static float[] sphere(double r, int ws, int hs) {
        FloatList f = new FloatList();
        for (int y = 0; y < hs; y++) {
            for (int x = 0; x < ws; x++) {
                double[] a = sp(r, x, y, ws, hs), b = sp(r, x + 1, y, ws, hs);
                double[] c = sp(r, x, y + 1, ws, hs), d = sp(r, x + 1, y + 1, ws, hs);
                tri(f, a, b, d, r);
                tri(f, a, d, c, r);
            }
        }
        return f.toArray();
    }
}
