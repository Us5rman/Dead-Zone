package com.deadzone.game;

import java.util.ArrayList;
import java.util.Arrays;

final class Nav {
    static final int N = 96;
    static final int INF = 30000;
    private static final double ORG = -48, CELL = 1;
    private static final int[] DI = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DJ = {0, 0, 1, -1, 1, -1, 1, -1};

    static final class Field {
        final int[] d = new int[N * N];
    }

    final boolean[] blocked = new boolean[N * N];
    private final int[] queue = new int[N * N];

    Nav(ArrayList<double[]> obs, double inflate) {
        for (double[] o : obs) {
            if (o[4] <= .35) continue;
            double x0 = o[0] - o[2] / 2 - inflate, x1 = o[0] + o[2] / 2 + inflate;
            double z0 = o[1] - o[3] / 2 - inflate, z1 = o[1] + o[3] / 2 + inflate;
            int i0 = cell(x0), i1 = cell(x1), j0 = cell(z0), j1 = cell(z1);
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    double cx = ORG + (i + .5) * CELL, cz = ORG + (j + .5) * CELL;
                    if (cx >= x0 && cx <= x1 && cz >= z0 && cz <= z1) blocked[j * N + i] = true;
                }
            }
        }
    }

    private static int cell(double v) {
        int c = (int) Math.floor((v - ORG) / CELL);
        return Math.max(0, Math.min(N - 1, c));
    }

    private static double center(int i) {
        return ORG + (i + .5) * CELL;
    }

    private boolean free(int i, int j) {
        return i >= 0 && j >= 0 && i < N && j < N && !blocked[j * N + i];
    }

    private boolean canMove(int i, int j, int k) {
        int ni = i + DI[k], nj = j + DJ[k];
        if (!free(ni, nj)) return false;
        if (k >= 4) return free(i + DI[k], j) && free(i, j + DJ[k]);
        return true;
    }

    private int nearestFree(int ci, int cj, int maxR) {
        if (free(ci, cj)) return cj * N + ci;
        for (int r = 1; r <= maxR; r++) {
            double bd = 1e9;
            int bi = -1;
            for (int j = cj - r; j <= cj + r; j++) {
                for (int i = ci - r; i <= ci + r; i++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != r || !free(i, j)) continue;
                    double dd = (i - ci) * (i - ci) + (j - cj) * (j - cj);
                    if (dd < bd) {
                        bd = dd;
                        bi = j * N + i;
                    }
                }
            }
            if (bi >= 0) return bi;
        }
        return -1;
    }

    void fill(Field f, double tx, double tz) {
        Arrays.fill(f.d, INF);
        int s = nearestFree(cell(tx), cell(tz), 6);
        if (s < 0) return;
        int head = 0, tail = 0;
        f.d[s] = 0;
        queue[tail++] = s;
        while (head < tail) {
            int cur = queue[head++];
            int ci = cur % N, cj = cur / N;
            int nd = f.d[cur] + 1;
            for (int k = 0; k < 8; k++) {
                if (!canMove(ci, cj, k)) continue;
                int n = (cj + DJ[k]) * N + ci + DI[k];
                if (f.d[n] > nd) {
                    f.d[n] = nd;
                    queue[tail++] = n;
                }
            }
        }
    }

    int cost(Field f, double x, double z) {
        int ci = cell(x), cj = cell(z);
        int best = f.d[cj * N + ci];
        for (int k = 0; k < 8; k++) {
            int ni = ci + DI[k], nj = cj + DJ[k];
            if (ni < 0 || nj < 0 || ni >= N || nj >= N) continue;
            best = Math.min(best, f.d[nj * N + ni] + 1);
        }
        return best;
    }

    boolean los(double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        int steps = (int) (len / .5) + 1;
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            if (blocked[cell(z0 + dz * t) * N + cell(x0 + dx * t)]) return false;
        }
        return true;
    }

    private int bestNeighbor(Field f, int ci, int cj) {
        int cur = f.d[cj * N + ci];
        int bk = -1;
        int bd = cur;
        for (int k = 0; k < 8; k++) {
            if (!canMove(ci, cj, k)) continue;
            int d = f.d[(cj + DJ[k]) * N + ci + DI[k]];
            if (d < bd) {
                bd = d;
                bk = k;
            }
        }
        return bk;
    }

    void step(Field f, double x, double z, double tx, double tz, double[] out) {
        double dx = tx - x, dz = tz - z, len = Math.max(Math.hypot(dx, dz), 1e-6);
        out[0] = dx / len;
        out[1] = dz / len;
        if (f == null) return;
        int ci = cell(x), cj = cell(z);
        if (blocked[cj * N + ci]) {
            int e = nearestFree(ci, cj, 3);
            if (e >= 0) {
                double ex = center(e % N) - x, ez = center(e / N) - z, el = Math.max(Math.hypot(ex, ez), 1e-6);
                out[0] = ex / el;
                out[1] = ez / el;
            }
            return;
        }
        if (los(x, z, tx, tz)) return;
        if (f.d[cj * N + ci] >= INF) return;
        int wi = -1, wj = -1;
        int i = ci, j = cj;
        for (int s = 0; s < 6; s++) {
            int k = bestNeighbor(f, i, j);
            if (k < 0) break;
            i += DI[k];
            j += DJ[k];
            if (s > 0 && !los(x, z, center(i), center(j))) break;
            wi = i;
            wj = j;
        }
        if (wi < 0) return;
        double wx = center(wi) - x, wz = center(wj) - z, wl = Math.max(Math.hypot(wx, wz), 1e-6);
        out[0] = wx / wl;
        out[1] = wz / wl;
    }
}
