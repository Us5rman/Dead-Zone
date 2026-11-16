package com.deadzone.game;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

final class WorldStore {
    static final class Info {
        String id, name, dir;
        long seed, last;
        int wave, kills;
    }

    private final Context ctx;

    WorldStore(Context c) {
        ctx = c;
        migrate();
    }

    static long parseSeed(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return Math.abs(new Random().nextLong() % 1000000000L);
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private File worlds() {
        return GameFiles.dir(ctx, "Worlds");
    }

    private void save(Info i) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", i.id);
            o.put("name", i.name);
            o.put("seed", i.seed);
            o.put("wave", i.wave);
            o.put("kills", i.kills);
            o.put("last", i.last);
            GameFiles.write(new File(new File(worlds(), i.dir), "world.json"), o.toString());
        } catch (Exception ignored) {
        }
    }

    private String unique(String name, String except) {
        String base = GameFiles.clean(name);
        String d = base;
        int n = 2;
        while (new File(worlds(), d).exists() && !d.equals(except)) d = base + " " + n++;
        return d;
    }

    synchronized ArrayList<Info> list() {
        ArrayList<Info> out = new ArrayList<>();
        File[] ds = worlds().listFiles();
        if (ds != null) {
            for (File d : ds) {
                String s = GameFiles.read(new File(d, "world.json"));
                if (s == null) continue;
                try {
                    JSONObject o = new JSONObject(s);
                    Info i = new Info();
                    i.dir = d.getName();
                    i.id = o.getString("id");
                    i.name = o.optString("name", d.getName());
                    i.seed = o.getLong("seed");
                    i.wave = o.optInt("wave");
                    i.kills = o.optInt("kills");
                    i.last = o.optLong("last");
                    out.add(i);
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(out, new Comparator<Info>() {
            @Override
            public int compare(Info a, Info b) {
                return Long.compare(b.last, a.last);
            }
        });
        return out;
    }

    synchronized Info add(long seed, String name) {
        int n = list().size() + 1;
        if (name == null || name.trim().isEmpty()) name = "World " + n;
        Info i = new Info();
        i.name = name.trim();
        i.dir = unique(i.name, null);
        i.id = Long.toString(System.nanoTime(), 36) + n;
        i.seed = seed;
        i.last = System.currentTimeMillis();
        save(i);
        return i;
    }

    synchronized void update(String id, int wave, int kills) {
        for (Info i : list()) {
            if (!i.id.equals(id)) continue;
            if (wave >= i.wave) {
                i.wave = wave;
                i.kills = kills;
            }
            i.last = System.currentTimeMillis();
            save(i);
        }
    }

    synchronized void rename(String id, String name) {
        if (name == null || name.trim().isEmpty()) return;
        for (Info i : list()) {
            if (!i.id.equals(id)) continue;
            String nd = unique(name, i.dir);
            if (!nd.equals(i.dir)) {
                new File(worlds(), i.dir).renameTo(new File(worlds(), nd));
                i.dir = nd;
            }
            i.name = name.trim();
            save(i);
        }
    }

    synchronized void delete(String id) {
        for (Info i : list()) if (i.id.equals(id)) GameFiles.deleteAll(new File(worlds(), i.dir));
    }

    synchronized void hostFolder(Info i) {
        try {
            File d = new File(GameFiles.dir(ctx, "Hosted Servers"), GameFiles.clean(i.name));
            JSONObject o = new JSONObject();
            o.put("name", i.name);
            o.put("seed", i.seed);
            o.put("wave", i.wave);
            o.put("last", System.currentTimeMillis());
            GameFiles.write(new File(d, "server.json"), o.toString());
        } catch (Exception ignored) {
        }
    }

    synchronized void cacheServer(String code, long seed) {
        try {
            JSONObject o = new JSONObject();
            o.put("code", code);
            o.put("seed", seed);
            o.put("last", System.currentTimeMillis());
            GameFiles.write(new File(GameFiles.dir(ctx, "Server Cache"), GameFiles.clean(code) + ".json"), o.toString());
        } catch (Exception ignored) {
        }
    }

    private void migrate() {
        SharedPreferences sp = ctx.getSharedPreferences("worlds", Context.MODE_PRIVATE);
        if (sp.getBoolean("migrated", false)) return;
        try {
            JSONArray a = new JSONArray(sp.getString("w", "[]"));
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                Info i = add(o.getLong("seed"), o.optString("name", "World"));
                i.wave = o.optInt("wave");
                i.kills = o.optInt("kills");
                save(i);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putBoolean("migrated", true).apply();
    }
}
