package com.deadzone.game;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

final class WorldStore {
    static final class Info {
        String id, name;
        long seed, last;
        int wave, kills;
    }

    private final SharedPreferences sp;

    WorldStore(Context c) {
        sp = c.getSharedPreferences("worlds", Context.MODE_PRIVATE);
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

    private synchronized ArrayList<Info> read() {
        ArrayList<Info> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("w", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Info n = new Info();
                n.id = o.getString("id");
                n.name = o.optString("name", "World");
                n.seed = o.getLong("seed");
                n.wave = o.optInt("wave");
                n.kills = o.optInt("kills");
                n.last = o.optLong("last");
                out.add(n);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private synchronized void write(ArrayList<Info> l) {
        JSONArray a = new JSONArray();
        try {
            for (Info n : l) {
                JSONObject o = new JSONObject();
                o.put("id", n.id);
                o.put("name", n.name);
                o.put("seed", n.seed);
                o.put("wave", n.wave);
                o.put("kills", n.kills);
                o.put("last", n.last);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString("w", a.toString()).apply();
    }

    synchronized ArrayList<Info> list() {
        ArrayList<Info> l = read();
        Collections.sort(l, new Comparator<Info>() {
            @Override
            public int compare(Info a, Info b) {
                return Long.compare(b.last, a.last);
            }
        });
        return l;
    }

    synchronized Info add(long seed) {
        int n = sp.getInt("n", 0) + 1;
        sp.edit().putInt("n", n).apply();
        Info i = new Info();
        i.id = Long.toString(System.nanoTime(), 36) + n;
        i.name = "World " + n;
        i.seed = seed;
        i.last = System.currentTimeMillis();
        ArrayList<Info> l = read();
        l.add(i);
        write(l);
        return i;
    }

    synchronized void update(String id, int wave, int kills) {
        ArrayList<Info> l = read();
        for (Info i : l) {
            if (!i.id.equals(id)) continue;
            if (wave >= i.wave) {
                i.wave = wave;
                i.kills = kills;
            }
            i.last = System.currentTimeMillis();
        }
        write(l);
    }

    synchronized void delete(String id) {
        ArrayList<Info> l = read();
        for (int k = l.size() - 1; k >= 0; k--) if (l.get(k).id.equals(id)) l.remove(k);
        write(l);
    }
}
