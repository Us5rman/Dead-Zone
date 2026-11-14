package com.deadzone.game;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class Net {
    interface Callbacks {
        void status(String s);

        void hosted();

        void taken();

        void peerJoin(String id);

        void peerLeave(String id);

        void fromClient(String id, JSONObject m);

        void joined(String id);

        void fromHost(JSONObject m);

        void hostGone();

        void closed();
    }

    private static final String WAIT = "در حال اتصال... اگر سرور خواب بوده تا یک دقیقه صبر کن";

    private final Callbacks cb;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private WebSocket ws;
    private int serial;
    private boolean ready;
    private boolean finished;

    Net(Callbacks cb) {
        this.cb = cb;
    }

    static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
        } catch (JSONException e) {
            // keys are always strings here
        }
        return o;
    }

    void host(String code) {
        open(true, code);
    }

    void join(String code) {
        open(false, code);
    }

    void close() {
        serial++;
        ready = false;
        if (ws != null) {
            ws.cancel();
            ws = null;
        }
    }

    private void open(final boolean host, final String code) {
        close();
        final int my = ++serial;
        ready = false;
        finished = false;
        cb.status(host ? "در حال ساخت اتاق... اگر سرور خواب بوده تا یک دقیقه صبر کن" : WAIT);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (my == serial && !ready && !finished) {
                    finished = true;
                    cb.status("اتصال برقرار نشد (مهلت تمام شد)");
                    close();
                }
            }
        }, 75000);
        ws = http.newWebSocket(new Request.Builder().url(Config.SERVER).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket s, Response r) {
                if (my != serial) return;
                s.send(host ? obj("t", "host", "code", code).toString() : obj("t", "join", "code", code).toString());
            }

            @Override
            public void onMessage(WebSocket s, String text) {
                if (my != serial) return;
                handle(host, text);
            }

            @Override
            public void onFailure(WebSocket s, Throwable t, Response r) {
                if (my != serial) return;
                lost();
            }

            @Override
            public void onClosed(WebSocket s, int c, String reason) {
                if (my != serial) return;
                lost();
            }
        });
    }

    private void lost() {
        if (ready) {
            ready = false;
            cb.closed();
        } else if (!finished) {
            finished = true;
            cb.status("اتصال برقرار نشد");
        }
    }

    private void handle(boolean host, String text) {
        JSONObject m;
        try {
            m = new JSONObject(text);
        } catch (JSONException e) {
            return;
        }
        String t = m.optString("t");
        switch (t) {
            case "hosted":
                ready = true;
                cb.hosted();
                break;
            case "joined":
                ready = true;
                cb.joined(m.optString("id"));
                break;
            case "err":
                String e = m.optString("e");
                if (host && "taken".equals(e)) {
                    cb.taken();
                } else {
                    finished = true;
                    cb.status("nohost".equals(e)
                            ? "اتاق پیدا نشد. کد را چک کن و مطمئن شو میزبان بازی را باز دارد"
                            : "خطا: " + e);
                    close();
                }
                break;
            case "full":
                finished = true;
                cb.status("اتاق پر است");
                close();
                break;
            case "peer_join":
                cb.peerJoin(m.optString("id"));
                break;
            case "peer_leave":
                cb.peerLeave(m.optString("id"));
                break;
            case "from":
                JSONObject d = m.optJSONObject("m");
                if (d != null) cb.fromClient(m.optString("id"), d);
                break;
            case "g":
                JSONObject g = m.optJSONObject("m");
                if (g != null) cb.fromHost(g);
                break;
            case "host_gone":
                cb.hostGone();
                break;
            default:
                break;
        }
    }

    /** to == null reaches everyone in the room (from the host) or just the host (from a client) */
    void sendG(JSONObject m, String to) {
        WebSocket s = ws;
        if (s == null) return;
        JSONObject o = obj("t", "g", "m", m);
        if (to != null) {
            try {
                o.put("to", to);
            } catch (JSONException e) {
                return;
            }
        }
        s.send(o.toString());
    }
}
