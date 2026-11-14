package com.deadzone.game;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.ArrayList;
import java.util.Random;

final class Sfx {
    static final int SINE = 0, SQUARE = 1, SAW = 2, TRI = 3;
    private static final int RATE = 22050;

    private static final class Voice {
        short[] d;
        int pos;
    }

    private final ArrayList<Voice> voices = new ArrayList<>();
    private final Random rnd = new Random();
    private AudioTrack track;
    private volatile boolean run = true;

    Sfx() {
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 4096) * 2, AudioTrack.MODE_STREAM);
            track.play();
        } catch (Exception e) {
            track = null;
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                mix();
            }
        }, "sfx");
        t.setDaemon(true);
        t.start();
    }

    private void mix() {
        short[] out = new short[512];
        int[] acc = new int[512];
        while (run) {
            java.util.Arrays.fill(acc, 0);
            synchronized (voices) {
                for (int v = voices.size() - 1; v >= 0; v--) {
                    Voice vo = voices.get(v);
                    int n = Math.min(512, vo.d.length - vo.pos);
                    for (int i = 0; i < n; i++) acc[i] += vo.d[vo.pos + i];
                    vo.pos += n;
                    if (vo.pos >= vo.d.length) voices.remove(v);
                }
            }
            for (int i = 0; i < 512; i++) out[i] = (short) Math.max(-32767, Math.min(32767, acc[i]));
            try {
                track.write(out, 0, 512);
            } catch (Exception e) {
                return;
            }
        }
    }

    private void add(short[] d) {
        if (track == null) return;
        Voice v = new Voice();
        v.d = d;
        synchronized (voices) {
            if (voices.size() < 24) voices.add(v);
        }
    }

    /** frequency sweeps down to 30 Hz while the volume fades out */
    void tone(double f, double dur, int type, double vol) {
        int n = (int) (RATE * dur);
        short[] d = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double freq = f * Math.pow(30.0 / f, t);
            phase += freq / RATE;
            double ph = phase - Math.floor(phase);
            double w;
            switch (type) {
                case SQUARE:
                    w = ph < .5 ? 1 : -1;
                    break;
                case SAW:
                    w = ph * 2 - 1;
                    break;
                case TRI:
                    w = Math.abs(ph * 4 - 2) - 1;
                    break;
                default:
                    w = Math.sin(ph * Math.PI * 2);
                    break;
            }
            double env = vol * Math.pow(.001 / Math.max(vol, .001), t);
            d[i] = (short) (w * env * 16000);
        }
        add(d);
    }

    void noise(double dur, double vol, double cut) {
        int n = (int) (RATE * dur);
        short[] d = new short[n];
        double a = 1 - Math.exp(-2 * Math.PI * cut / RATE), y = 0;
        for (int i = 0; i < n; i++) {
            double x = (rnd.nextDouble() * 2 - 1) * (1 - (double) i / n);
            y += a * (x - y);
            d[i] = (short) (y * vol * 24000);
        }
        add(d);
    }

    void release() {
        run = false;
        try {
            if (track != null) track.release();
        } catch (Exception e) {
            // already gone
        }
    }
}
