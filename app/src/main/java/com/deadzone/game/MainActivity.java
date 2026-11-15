package com.deadzone.game;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity implements Game.Ui {
    private Settings set;
    private Game game;
    private GLSurfaceView gl;
    private Overlay overlay;
    private View menu, settingsView, over, editBar, worldsView;
    private LinearLayout worldsCol;
    private WorldStore store;
    private boolean worldsHost;
    private EditText seedIn;
    private TextView status, overText;
    private Button again, resume;
    private EditText code;
    private float d;

    private int dp(float v) {
        return Math.round(v * d);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        set = new Settings(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        store = new WorldStore(this);
        game = new Game(this, set);
        game.store = store;
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setRenderer(game);
        game.view = gl;
        applyGraphics();

        overlay = new Overlay(this, game, set);
        overlay.onPause = new Runnable() {
            @Override
            public void run() {
                gl.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        game.pause();
                    }
                });
            }
        };

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(gl, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        menu = buildMenu();
        worldsView = buildWorlds();
        settingsView = buildSettings();
        over = buildOver();
        editBar = buildEditBar();
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        root.addView(worldsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(over, new FrameLayout.LayoutParams(-1, -1));
        root.addView(editBar, new FrameLayout.LayoutParams(-1, -2));
        settingsView.setVisibility(View.GONE);
        worldsView.setVisibility(View.GONE);
        over.setVisibility(View.GONE);
        editBar.setVisibility(View.GONE);
        setContentView(root);
    }

    /** resolution, refresh rate and vsync */
    private void applyGraphics() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int big = Math.max(dm.widthPixels, dm.heightPixels), small = Math.min(dm.widthPixels, dm.heightPixels);
        if (set.nativeRes) {
            gl.getHolder().setSizeFromLayout();
        } else {
            int fh = Math.min(260, small);
            gl.getHolder().setFixedSize(Math.round(big * (float) fh / small), fh);
        }
        float best = 60f;
        try {
            float[] rates = getWindowManager().getDefaultDisplay().getSupportedRefreshRates();
            for (float r : rates) if (r > best) best = r;
        } catch (Exception e) {
            // keep 60
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.preferredRefreshRate = best;
        getWindow().setAttributes(lp);
        gl.queueEvent(new Runnable() {
            @Override
            public void run() {
                game.applyGl();
            }
        });
    }

    /* ---- small view helpers ---- */

    private TextView text(String s, float sp, int color, boolean mono) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        return t;
    }

    private void style(Button b, int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setStroke(dp(2), stroke);
        b.setBackground(bg);
    }

    private Button button(String s, float sp) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(sp);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        style(b, 0xFF5A0F10, 0xFFB22A2A);
        b.setPadding(dp(18), dp(6), dp(18), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private TextView para(String s) {
        TextView t = text(s, 13, 0xFFd8d8d8, false);
        t.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private void onGl(Runnable r) {
        gl.queueEvent(r);
    }

    /* ---- main menu ---- */

    private View buildMenu() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.TOP | Gravity.START);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));
        col.addView(text("DEAD ZONE", 26, 0xFFcc2222, true));

        resume = button(L.t("resume"), 15);
        Button solo = button(L.t("start"), 15);
        Button host = button(L.t("host"), 15);
        Button join = button(L.t("join"), 15);
        Button sett = button(L.t("settings"), 15);
        resume.setVisibility(View.GONE);
        code = new EditText(this);
        code.setHint(L.t("code"));
        code.setHintTextColor(0xFF777777);
        code.setTextColor(Color.WHITE);
        code.setTextSize(18);
        code.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        code.setGravity(Gravity.CENTER);
        code.setSingleLine(true);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(4)});
        status = text("", 13, 0xFFcccc88, false);
        status.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(230), -2);
        bp.topMargin = dp(8);
        col.addView(resume, bp);
        col.addView(solo, bp);
        col.addView(host, bp);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(code, new LinearLayout.LayoutParams(dp(90), -2));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(0, -2, 1f);
        jp.leftMargin = dp(8);
        row.addView(join, jp);
        col.addView(row, bp);
        col.addView(sett, bp);
        col.addView(status, bp);

        resume.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu.setVisibility(View.GONE);
                onGl(new Runnable() {
                    @Override
                    public void run() {
                        game.paused = false;
                    }
                });
            }
        });
        solo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWorlds(false);
            }
        });
        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWorlds(true);
            }
        });
        join.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String c = code.getText().toString();
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.hideSoftInputFromWindow(code.getWindowToken(), 0);
                onGl(new Runnable() {
                    @Override
                    public void run() {
                        game.startJoin(c);
                    }
                });
            }
        });
        sett.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu.setVisibility(View.GONE);
                settingsView.setVisibility(View.VISIBLE);
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    /* ---- worlds ---- */

    private View buildWorlds() {
        worldsCol = new LinearLayout(this);
        worldsCol.setOrientation(LinearLayout.VERTICAL);
        worldsCol.setGravity(Gravity.TOP | Gravity.START);
        worldsCol.setPadding(dp(14), dp(10), dp(14), dp(10));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);
        sv.addView(worldsCol, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    private void openWorlds(boolean host) {
        worldsHost = host;
        fillWorlds();
        menu.setVisibility(View.GONE);
        worldsView.setVisibility(View.VISIBLE);
    }

    private void playWorld(final WorldStore.Info inf) {
        worldsView.setVisibility(View.GONE);
        if (worldsHost) menu.setVisibility(View.VISIBLE);
        final boolean host = worldsHost;
        onGl(new Runnable() {
            @Override
            public void run() {
                if (host) game.startHost(inf.seed, inf.id, inf.wave);
                else game.startSolo(inf.seed, inf.id, inf.wave);
            }
        });
    }

    private void fillWorlds() {
        worldsCol.removeAllViews();
        worldsCol.addView(text(L.t("w_title"), 26, 0xFFcc2222, true));

        seedIn = new EditText(this);
        seedIn.setHint(L.t("w_seed"));
        seedIn.setHintTextColor(0xFF777777);
        seedIn.setTextColor(Color.WHITE);
        seedIn.setTextSize(16);
        seedIn.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        seedIn.setSingleLine(true);
        seedIn.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        Button create = button(L.t("w_create"), 15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(300), -2);
        bp.topMargin = dp(8);
        worldsCol.addView(seedIn, bp);
        worldsCol.addView(create, bp);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.hideSoftInputFromWindow(seedIn.getWindowToken(), 0);
                playWorld(store.add(WorldStore.parseSeed(seedIn.getText().toString())));
            }
        });

        java.util.ArrayList<WorldStore.Info> list = store.list();
        if (list.isEmpty()) {
            TextView t = text(L.t("w_empty"), 13, 0xFF888888, false);
            t.setPadding(0, dp(14), 0, 0);
            worldsCol.addView(t);
        }
        for (final WorldStore.Info inf : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(inf.name, 16, Color.WHITE, true));
            info.addView(text(String.format(L.t("w_info"), Long.toString(inf.seed), inf.wave, inf.kills), 12, 0xFFaaaaaa, false));
            Button play = button(L.t("w_play"), 13);
            Button del = button(L.t("w_del"), 13);
            row.addView(info, new LinearLayout.LayoutParams(dp(200), -2));
            LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(dp(90), -2);
            pb.leftMargin = dp(8);
            row.addView(play, pb);
            row.addView(del, pb);
            worldsCol.addView(row);
            play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    playWorld(inf);
                }
            });
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.delete(inf.id);
                    fillWorlds();
                }
            });
        }

        Button back = button(L.t("s_back"), 15);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(300), -2);
        kp.topMargin = dp(16);
        worldsCol.addView(back, kp);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                worldsView.setVisibility(View.GONE);
                menu.setVisibility(View.VISIBLE);
            }
        });
    }

    /* ---- settings ---- */

    private View toggle(String label, boolean on, final CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView t = text(label, 15, Color.WHITE, false);
        t.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        BlockSwitch s = new BlockSwitch(this, on);
        s.setListener(new BlockSwitch.Listener() {
            @Override
            public void changed(boolean now) {
                l.onCheckedChanged(null, now);
            }
        });
        row.addView(s);
        return row;
    }

    private View buildSettings() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(12), dp(24), dp(12));
        TextView title = text(L.t("s_title"), 24, 0xFFcc2222, true);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        col.addView(toggle(L.t("s_native"), set.nativeRes, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.nativeRes = on;
                set.save();
                applyGraphics();
            }
        }), rowLp);
        col.addView(toggle(L.t("s_mb"), set.motionBlur, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.motionBlur = on;
                set.save();
            }
        }), rowLp);
        col.addView(toggle(L.t("s_fps"), set.showFps, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.showFps = on;
                set.save();
            }
        }), rowLp);
        col.addView(toggle(L.t("s_ping"), set.showPing, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.showPing = on;
                set.save();
            }
        }), rowLp);
        col.addView(toggle(L.t("s_autorun"), set.autoRun, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.autoRun = on;
                set.save();
            }
        }), rowLp);
        col.addView(toggle(L.t("s_vsync"), set.vsync, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.vsync = on;
                set.save();
                applyGraphics();
            }
        }), rowLp);

        TextView rdT = text(L.t("s_rd"), 15, 0xFFd8d8d8, false);
        rdT.setPadding(dp(4), dp(10), dp(4), dp(4));
        col.addView(rdT);
        LinearLayout rds = new LinearLayout(this);
        rds.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = button(L.t("s_rd" + i), 14);
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            if (i == set.renderDist) style(b, 0xFF8a1a1a, 0xFFffcc33);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    set.renderDist = idx;
                    set.save();
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(6);
            rds.addView(b, lp);
        }
        col.addView(rds, new LinearLayout.LayoutParams(-1, -2));

        TextView lt = text(L.t("s_lang"), 15, 0xFFd8d8d8, false);
        lt.setPadding(dp(4), dp(10), dp(4), dp(4));
        col.addView(lt);
        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < L.CODES.length; i++) {
            final String lang = L.CODES[i];
            Button b = button(L.NAMES[i], 14);
            b.setPadding(dp(10), dp(6), dp(10), dp(6));
            if (lang.equals(set.lang)) style(b, 0xFF8a1a1a, 0xFFffcc33);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    set.lang = lang;
                    set.save();
                    L.set(lang);
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = dp(6);
            langs.addView(b, lp);
        }
        col.addView(langs, new LinearLayout.LayoutParams(-1, -2));

        Button layout = button(L.t("s_layout"), 15);
        Button back = button(L.t("s_back"), 15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(12);
        col.addView(layout, bp);
        col.addView(back, bp);

        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsView.setVisibility(View.GONE);
                overlay.editMode = true;
                overlay.selected = -1;
                overlay.invalidate();
                editBar.setVisibility(View.VISIBLE);
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsView.setVisibility(View.GONE);
                menu.setVisibility(View.VISIBLE);
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    private View buildEditBar() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setBackgroundColor(0xAA000000);
        col.setPadding(dp(8), dp(4), dp(8), dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button minus = button("−", 16);
        Button plus = button("+", 16);
        Button reset = button(L.t("e_reset"), 14);
        Button done = button(L.t("e_done"), 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(6);
        lp.rightMargin = dp(6);
        row.addView(minus, lp);
        row.addView(plus, lp);
        row.addView(reset, lp);
        row.addView(done, lp);
        col.addView(row);
        TextView hint = text(L.t("e_hint"), 12, 0xFFcccccc, false);
        hint.setGravity(Gravity.CENTER);
        hint.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        col.addView(hint);

        minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overlay.changeSize(-.1f);
            }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overlay.changeSize(.1f);
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overlay.resetLayout();
            }
        });
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                set.save();
                overlay.editMode = false;
                overlay.invalidate();
                editBar.setVisibility(View.GONE);
                settingsView.setVisibility(View.VISIBLE);
            }
        });
        return col;
    }

    private View buildOver() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xCC000000);
        TextView t = text(L.t("died"), 38, 0xFFcc2222, true);
        t.setGravity(Gravity.CENTER);
        overText = text("", 16, 0xFFd8d8d8, false);
        overText.setGravity(Gravity.CENTER);
        overText.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        again = button(L.t("restart"), 18);
        Button toMenu = button(L.t("menu"), 16);
        col.addView(t);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.topMargin = dp(8);
        col.addView(overText, p);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(220), -2);
        bp.topMargin = dp(14);
        col.addView(again, bp);
        col.addView(toMenu, bp);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onGl(new Runnable() {
                    @Override
                    public void run() {
                        game.again();
                    }
                });
            }
        });
        toMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onGl(new Runnable() {
                    @Override
                    public void run() {
                        game.toMenu();
                    }
                });
            }
        });
        return col;
    }

    /* ---- Game.Ui ---- */

    @Override
    public void showMenu(final boolean canResume) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resume.setVisibility(canResume ? View.VISIBLE : View.GONE);
                menu.setBackgroundColor(canResume ? 0xF2000000 : 0xFF000000);
                settingsView.setVisibility(View.GONE);
                worldsView.setVisibility(View.GONE);
                menu.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void hideMenu() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                menu.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void showOver(final String s, final boolean canRestart) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overText.setText(s);
                again.setVisibility(canRestart ? View.VISIBLE : View.GONE);
                over.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void hideOver() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                over.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void status(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                status.setText(s);
            }
        });
    }

    @Override
    public void redraw() {
        long n = android.os.SystemClock.uptimeMillis();
        if (n - lastRedraw < 28) return;
        lastRedraw = n;
        overlay.postInvalidateOnAnimation();
    }

    private long lastRedraw;
    private final MediaPlayer[] rainP = new MediaPlayer[3];
    private float rainNow = -1;

    @Override
    public void rain(final float level) {
        if (Math.abs(level - rainNow) < .02f && !(level == 0 && rainNow != 0)) return;
        rainNow = level;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applyRain(level);
            }
        });
    }

    private static float tri(float x, float a, float b, float c) {
        if (x <= a || x >= c) return 0;
        return x < b ? (x - a) / (b - a) : (c - x) / (c - b);
    }

    private void applyRain(float level) {
        try {
            int[] ids = {R.raw.rain_light, R.raw.rain_mid, R.raw.rain_heavy};
            float[] v = {Math.max(tri(level, 0f, .25f, .6f), level < .25f ? level * 4f : 0f), tri(level, .3f, .6f, .9f),
                    Math.max(tri(level, .55f, 1f, 1.5f), level > 1f ? 1f : 0f)};
            float master = Math.min(1f, .25f + level * .75f);
            for (int i = 0; i < 3; i++) {
                if (level < .03f) {
                    if (rainP[i] != null && rainP[i].isPlaying()) rainP[i].pause();
                    continue;
                }
                if (rainP[i] == null) {
                    rainP[i] = MediaPlayer.create(this, ids[i]);
                    if (rainP[i] == null) continue;
                    rainP[i].setLooping(true);
                }
                float vol = v[i] * master * .8f;
                rainP[i].setVolume(vol, vol);
                if (!rainP[i].isPlaying()) rainP[i].start();
            }
        } catch (Exception ignored) {
        }
    }

    private void stopRain() {
        for (int i = 0; i < 3; i++) {
            if (rainP[i] != null) {
                try {
                    rainP[i].release();
                } catch (Exception ignored) {
                }
                rainP[i] = null;
            }
        }
        rainNow = -1;
    }

    /* ---- lifecycle ---- */

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRain();
        gl.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gl.onResume();
    }

    @Override
    protected void onDestroy() {
        stopRain();
        game.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (editBar.getVisibility() == View.VISIBLE || over.getVisibility() == View.VISIBLE) return;
        if (worldsView.getVisibility() == View.VISIBLE) {
            worldsView.setVisibility(View.GONE);
            menu.setVisibility(View.VISIBLE);
            return;
        }
        if (settingsView.getVisibility() == View.VISIBLE) {
            settingsView.setVisibility(View.GONE);
            menu.setVisibility(View.VISIBLE);
            return;
        }
        if (menu.getVisibility() == View.VISIBLE) {
            if (resume.getVisibility() == View.VISIBLE) resume.performClick();
            return;
        }
        if (game.hud.playing && overlay.onPause != null) overlay.onPause.run();
    }

    @Override
    public void thunder(final int idx, final float vol) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    int[] ids = {R.raw.thunder_close, R.raw.thunder_mid, R.raw.thunder_far};
                    MediaPlayer mp = MediaPlayer.create(MainActivity.this, ids[idx]);
                    if (mp == null) return;
                    mp.setVolume(vol, vol);
                    mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer m) {
                            m.release();
                        }
                    });
                    mp.start();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
