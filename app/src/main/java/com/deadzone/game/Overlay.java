package com.deadzone.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

final class Overlay extends View {
    private final Game game;
    private final Hud hud;
    private final Settings set;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path mmPath = new Path();
    private final Path mmArrow = new Path();
    boolean editMode;
    int selected = -1;
    Runnable onPause;

    private int jid = -1, lid = -1, fid = -1, dragId = -1;
    private float jx, jy, lx, ly;

    Overlay(Context ctx, Game game, Settings set) {
        super(ctx);
        this.game = game;
        this.hud = game.hud;
        this.set = set;
        this.d = ctx.getResources().getDisplayMetrics().density;
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    }

    private float bx(int i) {
        return set.x[i] * getWidth();
    }

    private float by(int i) {
        return set.y[i] * getHeight();
    }

    private float br(int i) {
        return Settings.BASE_R[i] * d * set.s[i];
    }

    private String label(int i) {
        switch (i) {
            case Settings.RUN:
                return L.t("b_run");
            case Settings.FIRE:
                return L.t("b_fire");
            case Settings.RELOAD:
                return L.t("b_rel");
            case Settings.JUMP:
                return L.t("b_jump");
            case Settings.DIVE:
                return L.t("b_dive");
            case Settings.SLIDE:
                return L.t("b_slide");
            case Settings.CRAWL:
                return L.t("b_crawl");
            default:
                return "";
        }
    }

    void changeSize(float delta) {
        if (selected < 0) return;
        set.s[selected] = Math.max(.6f, Math.min(2f, set.s[selected] + delta));
        invalidate();
    }

    void resetLayout() {
        set.resetLayout();
        invalidate();
    }

    private void controls(Canvas c) {
        // joystick
        float jxc = bx(Settings.JOY), jyc = by(Settings.JOY), jr = br(Settings.JOY);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * d);
        p.setColor(editMode && selected == Settings.JOY ? 0xCCffcc33 : 0x33ffffff);
        c.drawCircle(jxc, jyc, jr, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44ffffff);
        float kx = editMode ? 0 : (float) hud.stickX, ky = editMode ? 0 : (float) hud.stickY;
        c.drawCircle(jxc + kx * jr * .65f, jyc + ky * jr * .65f, jr * .4f, p);

        p.setTextAlign(Paint.Align.CENTER);
        for (int i = 1; i < Settings.COUNT; i++) {
            float x = bx(i), y = by(i), r = br(i);
            boolean down = i == Settings.FIRE && fid >= 0 || i == Settings.RUN && game.sprint || i == Settings.CRAWL && game.crawling;
            p.setStyle(Paint.Style.FILL);
            p.setColor(i == Settings.FIRE ? (down ? 0x66cc2222 : 0x33aa1111) : (down ? 0x66ffffff : 0x33ffffff));
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setColor(editMode && selected == i ? 0xFFffcc33 : (i == Settings.FIRE ? 0x55ff6666 : 0x44ffffff));
            c.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCCffffff);
            String t = label(i);
            p.setTextSize(r * (t.length() > 4 ? .36f : .5f));
            c.drawText(t, x, y + p.getTextSize() * .35f, p);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (editMode) {
            controls(c);
            return;
        }
        if (!hud.playing) return;

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
        c.drawText(L.t("h_wave") + " " + hud.wave, 16 * d, 44 * d, p);
        c.drawText(L.t("h_kills") + " " + hud.kills, 16 * d, 62 * d, p);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(22 * d);
        p.setColor(0xCCdddddd);
        c.drawText(hud.mag + " / " + hud.res, w - 62 * d, 32 * d, p);
        String note = hud.reloading ? L.t("h_rel") : (hud.mag == 0 && hud.res == 0 ? L.t("h_noammo") : "");
        if (note.length() > 0) {
            p.setTextSize(14 * d);
            p.setColor(0xFFcc3333);
            c.drawText(note, w - 62 * d, 52 * d, p);
        }

        p.setTextAlign(Paint.Align.CENTER);
        String room = hud.room;
        if (room != null && room.length() > 0) {
            p.setTextSize(18 * d);
            p.setColor(0xFF99cc99);
            c.drawText(room, w / 2f, 28 * d, p);
        }
        StringBuilder sb = new StringBuilder();
        if (set.showFps) sb.append("FPS ").append(hud.fps);
        if (set.showPing) {
            if (sb.length() > 0) sb.append("   ");
            sb.append(L.t("h_ping")).append(' ');
            sb.append(hud.ping >= 0 ? hud.ping + " ms" : "--");
        }
        if (sb.length() > 0) {
            p.setTextSize(12 * d);
            p.setColor(0xCCbbbbbb);
            c.drawText(sb.toString(), w / 2f, 46 * d, p);
        }

        drawMap(c, w);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d));
        p.setColor(0xB3ffffff);
        c.drawCircle(w / 2f, h / 2f, 3 * d, p);
        p.setColor(0x55ffffff);
        c.drawCircle(w - 30 * d, 30 * d, 14 * d, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(13 * d);
        c.drawText("II", w - 30 * d, 30 * d + 4.5f * d, p);

        if (hud.banT > 0 && hud.banner != null) {
            p.setTextSize(30 * d);
            int a = (int) (255 * Math.min(1, hud.banT / .6));
            p.setColor((a << 24) | 0x00cc3333);
            c.drawText(hud.banner, w / 2f, h * .3f, p);
        }

        if (hud.dead) {
            p.setTextSize(20 * d);
            p.setColor(0xFFcc3333);
            c.drawText(L.t("h_dead"), w / 2f, h * .38f, p);
        }

        controls(c);
    }

    private void drawMap(Canvas c, int w) {
        float r = 50 * d, mx = 16 * d + r, my = 72 * d + r, s = r / 30f;
        double yaw = hud.pyaw, cs = Math.cos(yaw), sn = Math.sin(yaw);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA050709);
        c.drawCircle(mx, my, r, p);
        c.save();
        mmPath.reset();
        mmPath.addCircle(mx, my, r, Path.Direction.CW);
        c.clipPath(mmPath);
        c.translate(mx, my);
        c.rotate((float) Math.toDegrees(yaw));
        c.translate(-(float) hud.px * s, -(float) hud.pz * s);
        p.setColor(0xFF3a4048);
        for (double[] ob : game.world.obs) {
            float x = (float) (ob[0] * s), z = (float) (ob[1] * s);
            float hw = (float) (ob[2] * s / 2), hd = (float) (ob[3] * s / 2);
            c.drawRect(x - hw, z - hd, x + hw, z + hd, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d));
        p.setColor(0xFF6a7078);
        c.drawRect(-45 * s, -45 * s, 45 * s, 45 * s, p);
        p.setStyle(Paint.Style.FILL);
        int[] zc = {0xFFdd3322, 0xFFff8822, 0xFFdd33aa, 0xFF9955ff, 0xFFffdd33};
        float[] zz = hud.mz;
        for (int i = 0; i + 2 < zz.length; i += 3) {
            int t = Math.max(0, Math.min(4, (int) zz[i + 2]));
            p.setColor(zc[t]);
            c.drawCircle(zz[i] * s, zz[i + 1] * s, (2f + t * .6f) * d, p);
        }
        p.setColor(0xFF33ddff);
        float[] pp = hud.mp;
        for (int i = 0; i + 1 < pp.length; i += 2) c.drawCircle(pp[i] * s, pp[i + 1] * s, 2.4f * d, p);
        c.restore();

        mmArrow.reset();
        mmArrow.moveTo(mx, my - 6 * d);
        mmArrow.lineTo(mx - 4 * d, my + 4 * d);
        mmArrow.lineTo(mx + 4 * d, my + 4 * d);
        mmArrow.close();
        p.setColor(0xFFffffff);
        c.drawPath(mmArrow, p);

        double bx = -30, bz = -50, bl = Math.hypot(bx, bz);
        bx /= bl;
        bz /= bl;
        float sx = (float) (bx * cs - bz * sn), sy = (float) (bx * sn + bz * cs);
        float rr = r - 5 * d;
        p.setColor(0x55ff8a2a);
        c.drawCircle(mx + sx * rr, my + sy * rr, (5f + 2f * (float) hud.bh) * d, p);
        p.setColor(0xFFffb060);
        c.drawCircle(mx + sx * rr, my + sy * rr, (2.5f + (float) hud.bh) * d, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, d));
        p.setColor(0x66aaaaaa);
        c.drawCircle(mx, my, r, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static float dist(float x, float y, float a, float b) {
        return (float) Math.hypot(x - a, y - b);
    }

    private void down(int id, float x, float y) {
        float w = getWidth();
        if (dist(x, y, w - 30 * d, 30 * d) <= 24 * d) {
            if (onPause != null) onPause.run();
            return;
        }
        if (fid < 0 && dist(x, y, bx(Settings.FIRE), by(Settings.FIRE)) <= br(Settings.FIRE) * 1.2f) {
            fid = id;
            game.fireIn = true;
        } else if (dist(x, y, bx(Settings.JUMP), by(Settings.JUMP)) <= br(Settings.JUMP) * 1.2f) {
            game.jumpReq = true;
        } else if (dist(x, y, bx(Settings.DIVE), by(Settings.DIVE)) <= br(Settings.DIVE) * 1.2f) {
            game.diveReq = true;
        } else if (dist(x, y, bx(Settings.SLIDE), by(Settings.SLIDE)) <= br(Settings.SLIDE) * 1.2f) {
            game.slideReq = true;
        } else if (dist(x, y, bx(Settings.CRAWL), by(Settings.CRAWL)) <= br(Settings.CRAWL) * 1.2f) {
            game.crawlReq = true;
        } else if (dist(x, y, bx(Settings.RELOAD), by(Settings.RELOAD)) <= br(Settings.RELOAD) * 1.2f) {
            game.reloadReq = true;
        } else if (dist(x, y, bx(Settings.RUN), by(Settings.RUN)) <= br(Settings.RUN) * 1.2f) {
            game.sprintReq = true;
        } else if (jid < 0 && dist(x, y, bx(Settings.JOY), by(Settings.JOY)) <= br(Settings.JOY) * 1.8f) {
            jid = id;
            jx = bx(Settings.JOY);
            jy = by(Settings.JOY);
            move(id, x, y);
        } else if (lid < 0) {
            lid = id;
            lx = x;
            ly = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == jid) {
            float dx = x - jx, dy = y - jy, m = br(Settings.JOY) * .85f, len = (float) Math.hypot(dx, dy);
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

    private boolean edit(MotionEvent e) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        if (a == MotionEvent.ACTION_DOWN) {
            int best = -1;
            float bd = 1e9f;
            for (int i = 0; i < Settings.COUNT; i++) {
                float dd = dist(x, y, bx(i), by(i));
                if (dd <= br(i) + 10 * d && dd < bd) {
                    bd = dd;
                    best = i;
                }
            }
            selected = best;
            dragId = best >= 0 ? e.getPointerId(0) : -1;
            invalidate();
        } else if (a == MotionEvent.ACTION_MOVE && selected >= 0 && dragId >= 0) {
            set.x[selected] = Math.max(.04f, Math.min(.96f, x / getWidth()));
            set.y[selected] = Math.max(.06f, Math.min(.94f, y / getHeight()));
            invalidate();
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            dragId = -1;
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (editMode) return edit(e);
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
