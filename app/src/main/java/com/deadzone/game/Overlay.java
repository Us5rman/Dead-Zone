package com.deadzone.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

final class Overlay extends View {
    private final Game game;
    private final Hud hud;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int jid = -1, lid = -1, fid = -1;
    private float jx, jy, lx, ly;

    Overlay(Context ctx, Game game) {
        super(ctx);
        this.game = game;
        this.hud = game.hud;
        this.d = ctx.getResources().getDisplayMetrics().density;
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    }

    private float fireX() {
        return getWidth() - 74 * d - 42 * d;
    }

    private float fireY() {
        return getHeight() - 34 * d - 42 * d;
    }

    private float relX() {
        return getWidth() - 156 * d - 27 * d;
    }

    private float relY() {
        return getHeight() - 128 * d - 27 * d;
    }

    private float joyX() {
        return 24 * d + 60 * d;
    }

    private float joyY() {
        return getHeight() - 24 * d - 60 * d;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!hud.playing) return;
        int w = getWidth(), h = getHeight();

        if (hud.vig > 0) {
            int a = (int) (Math.min(1, hud.vig) * 190);
            p.setShader(new RadialGradient(w / 2f, h / 2f, (float) Math.hypot(w, h) / 2f,
                    new int[]{0x00aa0000, (a << 24) | 0x00aa0000}, new float[]{.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF2a0808);
        c.drawRect(16 * d, 10 * d, 216 * d, 22 * d, p);
        p.setColor(0xFFb22222);
        c.drawRect(16 * d, 10 * d, (16 + 200 * (float) Math.max(0, Math.min(100, hud.hp)) / 100f) * d, 22 * d, p);

        p.setColor(0xAAcccccc);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(15 * d);
        c.drawText("WAVE " + hud.wave, 16 * d, 44 * d, p);
        c.drawText("KILLS " + hud.kills, 16 * d, 62 * d, p);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(22 * d);
        p.setColor(0xCCdddddd);
        c.drawText(hud.mag + " / " + hud.res, w - 16 * d, 32 * d, p);
        String note = hud.reloading ? "RELOADING..." : (hud.mag == 0 && hud.res == 0 ? "NO AMMO" : "");
        if (note.length() > 0) {
            p.setTextSize(14 * d);
            p.setColor(0xFFcc3333);
            c.drawText(note, w - 16 * d, 52 * d, p);
        }

        String room = hud.room;
        if (room != null && room.length() > 0) {
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(18 * d);
            p.setColor(0xFF99cc99);
            c.drawText(room, w / 2f, 28 * d, p);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d));
        p.setColor(0xB3ffffff);
        c.drawCircle(w / 2f, h / 2f, 3 * d, p);

        if (hud.banT > 0 && hud.banner != null) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(30 * d);
            int a = (int) (255 * Math.min(1, hud.banT / .6));
            p.setColor((a << 24) | 0x00cc3333);
            c.drawText(hud.banner, w / 2f, h * .3f, p);
        }

        if (hud.dead) {
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(20 * d);
            p.setColor(0xFFcc3333);
            c.drawText("YOU ARE DEAD  -  NEXT WAVE", w / 2f, h * .38f, p);
        }

        // sticks and buttons
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(0x33ffffff);
        c.drawCircle(joyX(), joyY(), 60 * d, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44ffffff);
        c.drawCircle(joyX() + (float) hud.stickX * 40 * d, joyY() + (float) hud.stickY * 40 * d, 24 * d, p);

        p.setColor(fid >= 0 ? 0x66cc2222 : 0x33aa1111);
        c.drawCircle(fireX(), fireY(), 42 * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0x55ff6666);
        c.drawCircle(fireX(), fireY(), 42 * d, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAAffcccc);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(14 * d);
        c.drawText("FIRE", fireX(), fireY() + 5 * d, p);

        p.setColor(0x33ffffff);
        c.drawCircle(relX(), relY(), 27 * d, p);
        p.setColor(0xAAffffff);
        c.drawText("R", relX(), relY() + 5 * d, p);
    }

    private static float dist(float x, float y, float a, float b) {
        return (float) Math.hypot(x - a, y - b);
    }

    private void down(int id, float x, float y) {
        if (fid < 0 && dist(x, y, fireX(), fireY()) <= 54 * d) {
            fid = id;
            game.fireIn = true;
        } else if (dist(x, y, relX(), relY()) <= 36 * d) {
            game.reloadReq = true;
        } else if (x < getWidth() * .45f && jid < 0) {
            jid = id;
            jx = x;
            jy = y;
        } else if (lid < 0) {
            lid = id;
            lx = x;
            ly = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == jid) {
            float dx = x - jx, dy = y - jy, m = 50 * d, len = (float) Math.hypot(dx, dy);
            if (len > m) {
                dx = dx / len * m;
                dy = dy / len * m;
            }
            game.mvx = dx / m;
            game.mvy = dy / m;
        } else if (id == lid) {
            float s = .006f / d;
            game.addLook(-(x - lx) * s, -(y - ly) * s);
            lx = x;
            ly = y;
        }
    }

    private void up(int id) {
        if (id == jid) {
            jid = -1;
            game.mvx = game.mvy = 0;
        }
        if (id == lid) lid = -1;
        if (id == fid) {
            fid = -1;
            game.fireIn = false;
        }
    }

    private void releaseAll() {
        jid = lid = fid = -1;
        game.mvx = game.mvy = 0;
        game.fireIn = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!hud.playing) {
            releaseAll();
            return true;
        }
        int a = e.getActionMasked(), ai = e.getActionIndex();
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(e.getPointerId(ai), e.getX(ai), e.getY(ai));
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(e.getPointerId(ai));
                break;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }
}
