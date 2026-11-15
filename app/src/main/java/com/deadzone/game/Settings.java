package com.deadzone.game;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

final class Settings {
    static final int JOY = 0, RUN = 1, FIRE = 2, RELOAD = 3, JUMP = 4, DIVE = 5, SLIDE = 6, COUNT = 7;
    static final float[] DX = {.14f, .30f, .88f, .74f, .76f, .92f, .60f};
    static final float[] DY = {.74f, .84f, .72f, .55f, .82f, .46f, .80f};
    static final float[] BASE_R = {60, 26, 42, 27, 30, 28, 28};

    boolean nativeRes, showFps, showPing, vsync = true;
    int renderDist = 1;
    String lang = "en";
    final float[] x = new float[COUNT], y = new float[COUNT], s = new float[COUNT];

    private final SharedPreferences sp;

    Settings(Context c) {
        sp = c.getSharedPreferences("deadzone", Context.MODE_PRIVATE);
        String sys = Locale.getDefault().getLanguage();
        String def = "en";
        if (sys.equals("fa") || sys.equals("ar") || sys.equals("ru")) def = sys;
        lang = sp.getString("lang", def);
        nativeRes = sp.getBoolean("nativeRes", false);
        renderDist = sp.getInt("renderDist", 1);
        showFps = sp.getBoolean("showFps", false);
        showPing = sp.getBoolean("showPing", false);
        vsync = sp.getBoolean("vsync", true);
        for (int i = 0; i < COUNT; i++) {
            x[i] = sp.getFloat("bx" + i, DX[i]);
            y[i] = sp.getFloat("by" + i, DY[i]);
            s[i] = sp.getFloat("bs" + i, 1f);
        }
        L.set(lang);
    }

    void save() {
        SharedPreferences.Editor e = sp.edit();
        e.putString("lang", lang);
        e.putBoolean("nativeRes", nativeRes);
        e.putInt("renderDist", renderDist);
        e.putBoolean("showFps", showFps);
        e.putBoolean("showPing", showPing);
        e.putBoolean("vsync", vsync);
        for (int i = 0; i < COUNT; i++) {
            e.putFloat("bx" + i, x[i]);
            e.putFloat("by" + i, y[i]);
            e.putFloat("bs" + i, s[i]);
        }
        e.apply();
    }

    void resetLayout() {
        for (int i = 0; i < COUNT; i++) {
            x[i] = DX[i];
            y[i] = DY[i];
            s[i] = 1f;
        }
    }
}
