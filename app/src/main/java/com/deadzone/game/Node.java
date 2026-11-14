package com.deadzone.game;

import java.util.ArrayList;

final class Node {
    static final class Part {
        final Geo geo;
        final Mat mat;
        double px, py, pz, rx, ry, rz;

        Part(Geo geo, Mat mat, double x, double y, double z) {
            this.geo = geo;
            this.mat = mat;
            px = x;
            py = y;
            pz = z;
        }
    }

    double px, py, pz, rx, ry, rz, s = 1;
    boolean visible = true;
    final ArrayList<Part> parts = new ArrayList<>();
    final ArrayList<Node> kids = new ArrayList<>();

    static Part box(double w, double h, double d, double x, double y, double z, Mat m) {
        return new Part(Geo.box(w, h, d), m, x, y, z);
    }

    static Part cyl(double r, double len, double x, double y, double z, Mat m, int seg) {
        Part p = new Part(Geo.cyl(r, r, len, seg, false), m, x, y, z);
        p.rx = Math.PI / 2;
        return p;
    }

    Node at(double x, double y, double z) {
        px = x;
        py = y;
        pz = z;
        return this;
    }

    void draw(float[] parent, boolean blendPass) {
        if (!visible) return;
        float[] m = Xf.mul(parent, Xf.make(px, py, pz, rx, ry, rz, s));
        for (Part p : parts) {
            if (p.mat.blend != blendPass) continue;
            Gfx.useMat(p.mat);
            Gfx.draw(p.geo, Xf.mul(m, Xf.make(p.px, p.py, p.pz, p.rx, p.ry, p.rz, 1)));
        }
        for (Node k : kids) k.draw(m, blendPass);
    }
}
