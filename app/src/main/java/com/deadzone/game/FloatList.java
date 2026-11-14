package com.deadzone.game;

import java.util.Arrays;

final class FloatList {
    private float[] a = new float[1024];
    private int n;

    void add(float v) {
        if (n == a.length) a = Arrays.copyOf(a, n * 2);
        a[n++] = v;
    }

    int size() {
        return n;
    }

    float[] toArray() {
        return Arrays.copyOf(a, n);
    }
}
