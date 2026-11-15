package com.deadzone.game;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class GameFiles {
    private GameFiles() {
    }

    static boolean hasAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static File root(Context c) {
        File r;
        if (hasAccess(c)) {
            r = new File(Environment.getExternalStorageDirectory(), "Dead Zone");
        } else {
            File d = c.getExternalFilesDir(null);
            r = new File(d != null ? d : c.getFilesDir(), "Dead Zone");
        }
        r.mkdirs();
        return r;
    }

    static File dir(Context c, String sub) {
        File f = new File(root(c), sub);
        f.mkdirs();
        return f;
    }

    static String clean(String s) {
        String t = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return t.isEmpty() ? "World" : t;
    }

    static void write(File f, String text) {
        try {
            File p = f.getParentFile();
            if (p != null) p.mkdirs();
            OutputStream o = new FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {
        }
    }

    static String read(File f) {
        try {
            InputStream i = new FileInputStream(f);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = i.read(buf)) > 0) b.write(buf, 0, n);
            i.close();
            return b.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static void copyAll(File from, File to) {
        if (from.isDirectory()) {
            to.mkdirs();
            File[] k = from.listFiles();
            if (k != null) for (File c : k) copyAll(c, new File(to, c.getName()));
        } else if (!to.exists()) {
            try {
                InputStream i = new FileInputStream(from);
                OutputStream o = new FileOutputStream(to);
                byte[] buf = new byte[8192];
                int n;
                while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
                i.close();
                o.close();
            } catch (Exception ignored) {
            }
        }
    }

    static void migrateFallback(Context c) {
        File d = c.getExternalFilesDir(null);
        if (d == null) return;
        File old = new File(d, "Dead Zone");
        if (!old.exists()) return;
        File real = new File(Environment.getExternalStorageDirectory(), "Dead Zone");
        real.mkdirs();
        copyAll(old, real);
        deleteAll(old);
    }

    static void deleteAll(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) deleteAll(c);
        f.delete();
    }

    static String playerName(Context c) {
        String s = read(new File(root(c), "player.json"));
        if (s != null) {
            try {
                return new org.json.JSONObject(s).optString("name", "Player");
            } catch (Exception ignored) {
            }
        }
        return "Player";
    }

    static void setPlayerName(Context c, String name) {
        try {
            write(new File(root(c), "player.json"), new org.json.JSONObject().put("name", name).toString());
        } catch (Exception ignored) {
        }
    }
}
