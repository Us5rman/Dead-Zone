package com.deadzone.game;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
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
    private View menu, settingsView, over, editBar, worldsView, multiView;
    private Button exitBtn, wPlay, wRen, wDel;
    private LinearLayout wList;
    private WorldStore.Info selWorld;
    private EditText nameIn;
    private View srDynRow;
    private final android.os.Handler thermalH = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable thermalTick = new Runnable() {
        @Override
        public void run() {
            float cap = 1f;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
                    int ts = pm.getCurrentThermalStatus();
                    if (ts >= 4) cap = .5f;
                    else if (ts >= 3) cap = .6f;
                    else if (ts >= 2) cap = .75f;
                    else if (ts >= 1) cap = .9f;
                }
                Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int t = bi != null ? bi.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) : 0;
                if (t >= 450) cap = Math.min(cap, .55f);
                else if (t >= 420) cap = Math.min(cap, .7f);
                else if (t >= 400) cap = Math.min(cap, .85f);
            } catch (Exception ignored) {
            }
            Upscaler.thermalCap = cap;
            thermalH.postDelayed(this, 4000);
        }
    };
    private WorldStore store;
    private boolean worldsHost;
    private TextView status, overText;
    private Button again, resume;
    private EditText code;
    private float d;
    private FrameLayout root;
    private SoundPool pool;
    /* ---- themed chat: transparent panel docked at the bottom with its own keyboard ---- */
    private View chatPanel;
    private TextView chatLog, chatInput;
    private final StringBuilder chatText = new StringBuilder();
    private boolean chatCaps;
    private int chatLang;
    private LinearLayout chatKeys;
    private final android.os.Handler chatH = new android.os.Handler();
    private final Runnable chatTick = new Runnable() {
        @Override
        public void run() {
            if (chatPanel == null) return;
            chatLog.setText(android.text.TextUtils.join("\n", lastLines(5)));
            chatH.postDelayed(this, 600);
        }
    };
    private static final String[] LANG_NAME = {"EN", "RU", "FA", "AR"};
    private static final String[][] LAYOUTS = {
            {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
            {"\u0439\u0446\u0443\u043a\u0435\u043d\u0433\u0448\u0449\u0437\u0445", "\u0444\u044b\u0432\u0430\u043f\u0440\u043e\u043b\u0434\u0436\u044d", "\u044f\u0447\u0441\u043c\u0438\u0442\u044c\u0431\u044e"},
            {"\u0636\u0635\u062b\u0642\u0641\u063a\u0639\u0647\u062e\u062d\u062c", "\u0634\u0633\u06cc\u0628\u0644\u0627\u062a\u0646\u0645\u06a9", "\u0638\u0637\u0632\u0631\u0630\u062f\u067e\u0648"},
            {"\u0636\u0635\u062b\u0642\u0641\u063a\u0639\u0647\u062e\u062d\u062c", "\u0634\u0633\u064a\u0628\u0644\u0627\u062a\u0646\u0645\u0643", "\u0626\u0621\u0624\u0631\u0649\u0629\u0648\u0632\u0638"}};

    private java.util.List<String> lastLines(int n) {
        String[] all = game.chatLines();
        java.util.ArrayList<String> r = new java.util.ArrayList<>();
        for (int i = Math.max(0, all.length - n); i < all.length; i++) r.add(all[i]);
        return r;
    }

    private void closeChat() {
        if (chatPanel == null) return;
        root.removeView(chatPanel);
        chatPanel = null;
        chatH.removeCallbacks(chatTick);
    }

    private void refreshChatInput() {
        chatInput.setText(chatText.length() == 0 ? "..." : chatText.toString());
        chatInput.setTextColor(chatText.length() == 0 ? 0x88ffffff : Color.WHITE);
    }

    private Button key(String label, final Runnable act, int fill, int stroke) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(fill);
        bg.setStroke(dp(1), stroke);
        b.setBackground(bg);
        b.setOnClickListener(v -> act.run());
        return b;
    }

    private void addRow(LinearLayout parent, java.util.List<Button> keys, java.util.List<Float> weights) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < keys.size(); i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weights.get(i));
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(keys.get(i), lp);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(32)));
    }

    private void buildChatKeys() {
        chatKeys.removeAllViews();
        final int kf = 0x99401010, ks = 0xCCB22A2A;
        java.util.ArrayList<Button> ks0 = new java.util.ArrayList<>();
        java.util.ArrayList<Float> w0 = new java.util.ArrayList<>();
        for (char c : "1234567890".toCharArray()) {
            final String s = String.valueOf(c);
            ks0.add(key(s, () -> typeChar(s), kf, ks));
            w0.add(1f);
        }
        addRow(chatKeys, ks0, w0);
        String[] rows = LAYOUTS[chatLang];
        for (int r = 0; r < rows.length; r++) {
            java.util.ArrayList<Button> kk = new java.util.ArrayList<>();
            java.util.ArrayList<Float> ww = new java.util.ArrayList<>();
            if (r == 2) {
                kk.add(key(chatCaps ? "SHIFT*" : "SHIFT", () -> {
                    chatCaps = !chatCaps;
                    buildChatKeys();
                }, chatCaps ? 0xCC8a1a1a : 0x99202020, 0xCC555555));
                ww.add(1.6f);
            }
            for (int i = 0; i < rows[r].length(); i++) {
                String ch = rows[r].substring(i, i + 1);
                final String shown = chatCaps ? ch.toUpperCase() : ch;
                kk.add(key(shown, () -> typeChar(shown), kf, ks));
                ww.add(1f);
            }
            if (r == 2) {
                kk.add(key("DEL", () -> {
                    if (chatText.length() > 0) chatText.deleteCharAt(chatText.length() - 1);
                    refreshChatInput();
                }, 0x99202020, 0xCC555555));
                ww.add(1.6f);
            }
            addRow(chatKeys, kk, ww);
        }
        java.util.ArrayList<Button> kb = new java.util.ArrayList<>();
        java.util.ArrayList<Float> wb = new java.util.ArrayList<>();
        kb.add(key(LANG_NAME[chatLang], () -> {
            chatLang = (chatLang + 1) % LAYOUTS.length;
            buildChatKeys();
        }, 0x99202020, 0xCC555555));
        wb.add(1.3f);
        for (String s : new String[]{".", ",", "!", "?"}) {
            final String t = s;
            kb.add(key(t, () -> typeChar(t), kf, ks));
            wb.add(.9f);
        }
        kb.add(key("SPACE", () -> typeChar(" "), kf, ks));
        wb.add(3.4f);
        kb.add(key("SEND", () -> {
            final String s = chatText.toString().trim();
            if (!s.isEmpty()) onGl(() -> game.sendChat(s));
            chatText.setLength(0);
            refreshChatInput();
        }, 0xCCB22A2A, 0xFFff5a4a));
        wb.add(1.6f);
        kb.add(key("X", this::closeChat, 0x99202020, 0xCC555555));
        wb.add(.9f);
        addRow(chatKeys, kb, wb);
    }

    private void typeChar(String s) {
        if (chatText.length() >= 80) return;
        chatText.append(s);
        refreshChatInput();
    }

    private void openChat() {
        if (chatPanel != null) return;
        chatText.setLength(0);
        chatCaps = false;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB30b0b0b);
        bg.setStroke(dp(2), 0xCCB22A2A);
        box.setBackground(bg);
        chatLog = text("", 11, 0xFFe0e0e0, true);
        chatLog.setLines(5);
        chatLog.setGravity(Gravity.BOTTOM);
        box.addView(chatLog, new LinearLayout.LayoutParams(-1, -2));
        chatInput = text("...", 14, Color.WHITE, true);
        chatInput.setGravity(Gravity.CENTER_VERTICAL);
        chatInput.setPadding(dp(10), 0, dp(10), 0);
        chatInput.setSingleLine(true);
        chatInput.setEllipsize(android.text.TextUtils.TruncateAt.START);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(0x99120808);
        ib.setStroke(dp(2), 0xCC5A0F10);
        chatInput.setBackground(ib);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(32));
        ip.topMargin = dp(4);
        box.addView(chatInput, ip);
        chatKeys = new LinearLayout(this);
        chatKeys.setOrientation(LinearLayout.VERTICAL);
        box.addView(chatKeys, new LinearLayout.LayoutParams(-1, -2));
        buildChatKeys();
        refreshChatInput();
        chatPanel = box;
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int sw = getResources().getDisplayMetrics().widthPixels;
        fp.width = Math.min(sw, dp(560));
        fp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        root.addView(chatPanel, fp);
        chatH.post(chatTick);
    }

    /* ---- admin panel (name Us5rman only) ---- */
    private Dialog adminDlg;

    private Button adminBtn(final String label, final boolean on, final Runnable act) {
        final Button b = button(label, 12);
        if (on) style(b, 0xFF8a1a1a, 0xFFff5a4a);
        b.setOnClickListener(v -> {
            act.run();
            if (adminDlg != null) adminDlg.dismiss();
            openAdmin();
        });
        return b;
    }

    private void adminRow(LinearLayout box, Button a, Button b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(38), 1f);
        l1.setMargins(0, dp(4), dp(4), 0);
        row.addView(a, l1);
        if (b != null) {
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(38), 1f);
            l2.setMargins(dp(4), dp(4), 0, 0);
            row.addView(b, l2);
        }
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private Runnable cmd(final int c) {
        return () -> onGl(() -> game.adminCmd(c));
    }

    private String onOff(String n, boolean v) {
        return n + ": " + (v ? "ON" : "OFF");
    }

    private void openAdmin() {
        if (!game.admin()) return;
        if (adminDlg != null && adminDlg.isShowing()) adminDlg.dismiss();
        final Dialog dlg = new Dialog(this);
        adminDlg = dlg;
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0b0b0b);
        bg.setStroke(dp(2), 0xFFB22A2A);
        box.setBackground(bg);
        box.addView(text("ADMIN", 22, 0xFFcc2222, true));
        boolean host = game.role != Game.CLIENT;

        box.addView(text("PLAYER", 11, 0xFF999999, true));
        adminRow(box, adminBtn("REVIVE / HEAL", false, cmd(0)), adminBtn(onOff("GOD MODE", game.god), game.god, cmd(1)));
        adminRow(box, adminBtn(onOff("INFINITE AMMO", game.infAmmo), game.infAmmo, cmd(2)), adminBtn("REFILL AMMO", false, cmd(3)));
        adminRow(box, adminBtn(onOff("SPEED x1.8", game.speedUp), game.speedUp, cmd(4)), adminBtn(onOff("ONE SHOT KILL", game.oneShot), game.oneShot, cmd(5)));
        adminRow(box, adminBtn("+100 KILLS", false, cmd(12)), null);

        box.addView(text("WEAPONS", 11, 0xFF999999, true));
        for (int i = 0; i < Weapons.NAME.length; i += 2) {
            final int a = i, b2 = i + 1;
            adminRow(box,
                    adminBtn(Weapons.NAME[a] + (game.weapon == a ? " *" : ""), game.weapon == a, () -> onGl(() -> game.equip(a))),
                    adminBtn(Weapons.NAME[b2] + (game.weapon == b2 ? " *" : ""), game.weapon == b2, () -> onGl(() -> game.equip(b2))));
        }

        box.addView(text(host ? "WORLD" : "WORLD (HOST ONLY)", 11, 0xFF999999, true));
        adminRow(box, adminBtn("SUMMON LIGHTNING", false, cmd(6)),
                adminBtn("RAIN: " + (game.rainForce == -1 ? "AUTO" : game.rainForce == 1 ? "STORM" : "CLEAR"), game.rainForce != -1, cmd(7)));
        adminRow(box, adminBtn("KILL ALL ZOMBIES", false, cmd(8)), adminBtn("NEXT WAVE NOW", false, cmd(9)));
        adminRow(box, adminBtn("SPAWN 5 ZOMBIES", false, cmd(10)), adminBtn(onOff("FREEZE ZOMBIES", game.freezeZ), game.freezeZ, cmd(11)));

        Button close = button(L.t("s_back"), 14);
        style(close, 0xFF1a1a1a, 0xFF555555);
        close.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(40));
        cp.topMargin = dp(10);
        box.addView(close, cp);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg.setContentView(sv);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.15f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(dp(520), sw * 8 / 10), WindowManager.LayoutParams.WRAP_CONTENT);
            w.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
        }
        dlg.show();
    }

    private void openShop() {
        if (game.admin()) {
            openAdmin();
            return;
        }
        if (!game.shopOpen()) return;
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC0b0b0b);
        bg.setStroke(dp(2), 0xFFB22A2A);
        box.setBackground(bg);
        box.addView(text(L.t("h_shop"), 22, 0xFFcc2222, true));
        for (int i = 0; i < Weapons.NAME.length; i++) {
            final int wi = i;
            boolean eq = game.weapon == i, ok = game.canUse(i);
            Button b = button(Weapons.NAME[i] + (eq ? "  [EQUIPPED]" : ok ? "" : "  NEED " + Weapons.UNLOCK[i] + " KILLS") + "   MOBILITY " + Math.round(Weapons.MOB[i] * 100) + "%", 14);
            if (eq) style(b, 0xFF8a1a1a, 0xFFff5a4a);
            setBtn(b, ok && !eq);
            b.setOnClickListener(v -> {
                onGl(() -> game.equip(wi));
                dlg.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
            lp.topMargin = dp(8);
            box.addView(b, lp);
        }
        Button back = button(L.t("s_back"), 14);
        style(back, 0xFF1a1a1a, 0xFF555555);
        back.setOnClickListener(v -> dlg.dismiss());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(40));
        bp.topMargin = dp(14);
        box.addView(back, bp);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg.setContentView(sv);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.8f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(dp(440), sw * 8 / 10), WindowManager.LayoutParams.WRAP_CONTENT);
            w.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
        }
        dlg.show();
    }

    private MediaPlayer bhP;
    private boolean bhResume;
    private final java.util.HashMap<String, Integer> soundIds = new java.util.HashMap<>();

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

        initSounds();
        store = new WorldStore(this);
        game = new Game(this, set);
        game.store = store;
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setRenderer(game);
        game.view = gl;
        applyGraphics();

        overlay = new Overlay(this, game, set);
        overlay.onChat = this::openChat;
        overlay.onShop = this::openShop;
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

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(gl, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        menu = buildMenu();
        worldsView = buildWorlds();
        multiView = buildMulti();
        settingsView = buildSettings();
        over = buildOver();
        editBar = buildEditBar();
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        menu.setVisibility(View.VISIBLE);
        root.addView(worldsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(multiView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(over, new FrameLayout.LayoutParams(-1, -1));
        root.addView(editBar, new FrameLayout.LayoutParams(-1, -2));
        settingsView.setVisibility(View.GONE);
        worldsView.setVisibility(View.GONE);
        multiView.setVisibility(View.GONE);
        over.setVisibility(View.GONE);
        editBar.setVisibility(View.GONE);
        setContentView(root);
    }

    private void initSounds() {
        try {
            pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            names.add("multiplayer_join_connected");
            names.add("black_hole_ambient_rumble");
            names.add("bh_grow_small");
            names.add("bh_grow_big");
            for (int i = 1; i <= 3; i++) {
                names.add("gunshot_close_player_" + i);
                names.add("gunshot_far_away_" + i);
            }
            for (String surf : new String[]{"asphalt", "grass", "wood", "water_splash"}) {
                for (int i = 1; i <= 4; i++) {
                    names.add("footstep_" + surf + "_" + i);
                    names.add("footstep_" + surf + "_run_" + i);
                }
            }
            for (String n : names) {
                int res = getResources().getIdentifier(n, "raw", getPackageName());
                if (res != 0) soundIds.put(n, pool.load(this, res, 1));
            }
        } catch (Exception e) {
            pool = null;
        }
    }

    @Override
    public void bhSound(final int cmd, final int posMs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (cmd == 0 || cmd == 1) {
                        if (bhP != null) {
                            bhP.release();
                            bhP = null;
                        }
                        bhResume = false;
                    }
                    if (cmd == 1) {
                        bhP = MediaPlayer.create(MainActivity.this, R.raw.black_hole_growing_60s);
                        if (bhP == null) return;
                        bhP.setVolume(.9f, .9f);
                        if (posMs > 0) bhP.seekTo(posMs);
                        bhP.start();
                    } else if (cmd == 2) {
                        if (bhP != null && bhP.isPlaying()) bhP.pause();
                    } else if (cmd == 3) {
                        if (bhP != null && !bhP.isPlaying()) bhP.start();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    @Override
    public void sound(String name, float vol, float rate) {
        SoundPool p = pool;
        Integer id = soundIds.get(name);
        if (p == null || id == null) return;
        p.play(id, Math.min(1f, vol), Math.min(1f, vol), 1, 0, rate);
    }

    private void rebuildUi() {
        int resumeVis = resume.getVisibility();
        CharSequence st = status.getText();
        int bg = menu.getBackground() instanceof android.graphics.drawable.ColorDrawable
                ? ((android.graphics.drawable.ColorDrawable) menu.getBackground()).getColor() : 0x00000000;
        int menuVis = menu.getVisibility(), setVis = settingsView.getVisibility(), worldsVis = worldsView.getVisibility();
        int multiVis = multiView.getVisibility();
        int overVis = over.getVisibility(), editVis = editBar.getVisibility();
        CharSequence ot = overText.getText();
        int againVis = again.getVisibility();
        root.removeView(menu);
        root.removeView(worldsView);
        root.removeView(multiView);
        root.removeView(settingsView);
        root.removeView(over);
        root.removeView(editBar);
        menu = buildMenu();
        worldsView = buildWorlds();
        multiView = buildMulti();
        settingsView = buildSettings();
        over = buildOver();
        editBar = buildEditBar();
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        root.addView(worldsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(multiView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(settingsView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(over, new FrameLayout.LayoutParams(-1, -1));
        root.addView(editBar, new FrameLayout.LayoutParams(-1, -2));
        menu.setBackgroundColor(bg);
        menu.setVisibility(menuVis);
        settingsView.setVisibility(setVis);
        worldsView.setVisibility(worldsVis);
        multiView.setVisibility(multiVis);
        exitBtn.setVisibility(resumeVis);
        over.setVisibility(overVis);
        editBar.setVisibility(editVis);
        resume.setVisibility(resumeVis);
        status.setText(st);
        overText.setText(ot);
        again.setVisibility(againVis);
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
        Upscaler.targetFps = best;
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

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF777777);
        e.setTextColor(Color.WHITE);
        e.setTextSize(16);
        e.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        e.setSingleLine(true);
        e.setHighlightColor(0x66cc2222);
        GradientDrawable fb = new GradientDrawable();
        fb.setColor(0xFF120808);
        fb.setStroke(dp(2), 0xFF5A0F10);
        e.setBackground(fb);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        return e;
    }

    private void themedDialog(String title, String message, View content, String okText, final Runnable ok, String cancelText) {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF0b0b0b);
        bg.setStroke(dp(2), 0xFFB22A2A);
        box.setBackground(bg);
        box.addView(text(title, 20, 0xFFcc2222, true));
        if (message != null) {
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
            mp.topMargin = dp(8);
            box.addView(text(message, 13, 0xFFcccccc, false), mp);
        }
        if (content != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.topMargin = dp(10);
            box.addView(content, cp);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        Button no = button(cancelText, 14);
        style(no, 0xFF1a1a1a, 0xFF555555);
        no.setOnClickListener(v -> dlg.dismiss());
        Button yes = button(okText, 14);
        yes.setOnClickListener(v -> {
            dlg.dismiss();
            ok.run();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.leftMargin = dp(10);
        row.addView(no);
        row.addView(yes, bp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(14);
        box.addView(row, rp);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg.setContentView(sv);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.8f);
            int sw = getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(dp(440), sw * 8 / 10), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
        }
        dlg.show();
    }

    private boolean askedStorage;
    private boolean lastAccess;

    private void ensureStorage() {
        if (GameFiles.hasAccess(this) || askedStorage) return;
        askedStorage = true;
        themedDialog("Dead Zone folder",
                "Allow file access so your worlds, name and server files are saved in /storage/emulated/0/Dead Zone and survive updates.",
                null, "Allow", this::requestStorage, "Not now");
    }

    private void requestStorage() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
            }
        }
    }

    private View buildMenu() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.TOP | Gravity.START);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));
        col.addView(text("DEAD ZONE", 26, 0xFFcc2222, true));

        resume = button(L.t("resume"), 15);
        Button solo = button(L.t("start"), 15);
        Button multi = button(L.t("multi"), 15);
        Button sett = button(L.t("settings"), 15);
        exitBtn = button(L.t("exit"), 15);
        resume.setVisibility(View.GONE);
        exitBtn.setVisibility(View.GONE);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(230), -2);
        bp.topMargin = dp(8);
        col.addView(resume, bp);
        col.addView(solo, bp);
        col.addView(multi, bp);
        col.addView(sett, bp);
        col.addView(exitBtn, bp);

        resume.setOnClickListener(v -> {
            menu.setVisibility(View.GONE);
            onGl(() -> game.paused = false);
        });
        solo.setOnClickListener(v -> openWorlds(false));
        multi.setOnClickListener(v -> {
            menu.setVisibility(View.GONE);
            multiView.setVisibility(View.VISIBLE);
        });
        sett.setOnClickListener(v -> {
            menu.setVisibility(View.GONE);
            settingsView.setVisibility(View.VISIBLE);
        });
        exitBtn.setOnClickListener(v -> onGl(() -> game.toMenu()));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0x00000000);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        final FrameLayout wrap = new FrameLayout(this) {
            @Override
            public void setVisibility(int v) {
                super.setVisibility(v);
                game.menuUp = v == View.VISIBLE;
                if (overlay != null) overlay.setVisibility(v == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
            }
        };
        wrap.setBackgroundColor(0x00000000);
        wrap.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        // one red line at 76% of the screen height; the black hole behind it is cut off there
        final View bot = new View(this);
        bot.setBackgroundColor(0xFFcc2222);
        final FrameLayout.LayoutParams bp2 = new FrameLayout.LayoutParams(-1, dp(2), Gravity.TOP);
        bp2.topMargin = dp(300);
        wrap.addView(bot, bp2);
        wrap.addOnLayoutChangeListener((lv, lL, lT, lR, lB, lOl, lOt, lOr, lOb) -> {
            int want = (int) ((lB - lT) * Sky.MENU_LINE);
            if (lB - lT > 0 && bp2.topMargin != want) {
                bp2.topMargin = want;
                bot.setLayoutParams(bp2);
            }
        });
        return wrap;
    }

    private View buildMulti() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.TOP | Gravity.START);
        col.setPadding(dp(14), dp(10), dp(14), dp(10));
        col.addView(text(L.t("multi"), 26, 0xFFcc2222, true));

        nameIn = field(L.t("p_name"));
        nameIn.setText(GameFiles.playerName(this));
        nameIn.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        nameIn.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String n = s.toString().trim();
                if (n.isEmpty()) n = "Player";
                GameFiles.setPlayerName(MainActivity.this, n);
                game.myName = n;
            }
        });
        game.myName = GameFiles.playerName(this);

        Button host = button(L.t("host"), 15);
        Button join = button(L.t("join"), 15);
        Button back = button(L.t("s_back"), 15);
        code = field(L.t("code"));
        code.setTextSize(18);
        code.setGravity(Gravity.CENTER);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(4)});
        status = text("", 13, 0xFFcccc88, false);
        status.setTextDirection(L.rtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(230), -2);
        bp.topMargin = dp(8);
        col.addView(nameIn, bp);
        col.addView(host, bp);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(code, new LinearLayout.LayoutParams(dp(90), -2));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(0, -2, 1f);
        jp.leftMargin = dp(8);
        row.addView(join, jp);
        col.addView(row, bp);
        col.addView(back, bp);
        col.addView(status, bp);

        host.setOnClickListener(v -> openWorlds(true));
        join.setOnClickListener(v -> {
            final String c = code.getText().toString();
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(code.getWindowToken(), 0);
            onGl(() -> game.startJoin(c));
        });
        back.setOnClickListener(v -> {
            multiView.setVisibility(View.GONE);
            menu.setVisibility(View.VISIBLE);
        });

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
        return sv;
    }

    /* ---- worlds ---- */

    private View buildWorlds() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(0xFF000000);
        TextView title = text(L.t("w_title"), 24, 0xFFcc2222, true);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.topMargin = dp(8);
        f.addView(title, tp);

        ScrollView sv = new ScrollView(this);
        wList = new LinearLayout(this);
        wList.setOrientation(LinearLayout.VERTICAL);
        sv.addView(wList, new FrameLayout.LayoutParams(-1, -2));
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int lw = Math.min(dp(560), (int) (dm.widthPixels * .66f));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(lw, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sp.topMargin = dp(50);
        sp.bottomMargin = dp(92);
        f.addView(sv, sp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        Button create = button(L.t("w_createw"), 13);
        wRen = button(L.t("w_rename"), 13);
        wPlay = button(L.t("w_play"), 13);
        wDel = button(L.t("w_del"), 13);
        Button back = button(L.t("s_back"), 13);
        Button[] bs = {create, wRen, wPlay, wDel, back};
        for (Button b : bs) {
            b.setPadding(dp(8), dp(8), dp(8), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            bar.addView(b, lp);
        }
        FrameLayout.LayoutParams bpp = new FrameLayout.LayoutParams(Math.min(dp(640), (int) (dm.widthPixels * .94f)), -2,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bpp.bottomMargin = dp(18);
        f.addView(bar, bpp);

        create.setOnClickListener(v -> worldDialog(null));
        wRen.setOnClickListener(v -> {
            if (selWorld != null) worldDialog(selWorld);
        });
        wPlay.setOnClickListener(v -> {
            if (selWorld != null) playWorld(selWorld);
        });
        wDel.setOnClickListener(v -> {
            if (selWorld == null) return;
            store.delete(selWorld.id);
            selWorld = null;
            fillWorlds();
        });
        back.setOnClickListener(v -> {
            worldsView.setVisibility(View.GONE);
            (worldsHost ? multiView : menu).setVisibility(View.VISIBLE);
        });
        return f;
    }

    private void worldDialog(final WorldStore.Info ren) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), 0);
        final EditText name = field(L.t("w_name"));
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        box.addView(name);
        final EditText seed = ren == null ? field(L.t("w_seed")) : null;
        if (ren != null) name.setText(ren.name);
        else box.addView(seed);
        String ok = L.t(ren == null ? "w_createw" : "w_rename");
        themedDialog(ok, null, box, ok, () -> {
            if (ren == null) {
                selWorld = store.add(WorldStore.parseSeed(seed.getText().toString()), name.getText().toString());
            } else {
                store.rename(ren.id, name.getText().toString());
            }
            fillWorlds();
        }, L.t("s_back"));
    }

    private void openWorlds(boolean host) {
        worldsHost = host;
        selWorld = null;
        fillWorlds();
        menu.setVisibility(View.GONE);
        multiView.setVisibility(View.GONE);
        worldsView.setVisibility(View.VISIBLE);
    }

    private void playWorld(final WorldStore.Info inf) {
        worldsView.setVisibility(View.GONE);
        final boolean host = worldsHost;
        if (host) {
            multiView.setVisibility(View.VISIBLE);
            store.hostFolder(inf);
        }
        onGl(() -> {
            if (host) game.startHost(inf.seed, inf.id, inf.wave);
            else game.startSolo(inf.seed, inf.id, inf.wave);
        });
    }

    private void setBtn(Button b, boolean on) {
        b.setEnabled(on);
        b.setAlpha(on ? 1f : .35f);
    }

    private void fillWorlds() {
        wList.removeAllViews();
        java.util.ArrayList<WorldStore.Info> list = store.list();
        if (selWorld != null) {
            WorldStore.Info keep = null;
            for (WorldStore.Info i : list) if (i.id.equals(selWorld.id)) keep = i;
            selWorld = keep;
        }
        if (list.isEmpty()) {
            TextView t = text(L.t("w_empty"), 14, 0xFF888888, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(30), 0, 0);
            wList.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        for (final WorldStore.Info inf : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            boolean sel = selWorld != null && selWorld.id.equals(inf.id);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(sel ? 0xFF3a0d0d : 0xFF0e0e0e);
            bg.setStroke(dp(2), sel ? 0xFFcc2222 : 0xFF2a2a2a);
            row.setBackground(bg);
            row.addView(text(inf.name, 17, Color.WHITE, true));
            row.addView(text(String.format(L.t("w_info"), Long.toString(inf.seed), inf.wave, inf.kills), 12, 0xFFaaaaaa, false));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = dp(8);
            wList.addView(row, rp);
            row.setOnClickListener(v -> {
                selWorld = inf;
                fillWorlds();
            });
        }
        boolean has = selWorld != null;
        setBtn(wPlay, has);
        setBtn(wRen, has);
        setBtn(wDel, has);
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

        lastAccess = GameFiles.hasAccess(this);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        col.addView(text("Folder: " + GameFiles.root(this).getAbsolutePath(), 12, 0xFF999999, false));
        if (!GameFiles.hasAccess(this)) {
            Button grant = button("Allow folder access", 13);
            grant.setOnClickListener(v -> requestStorage());
            LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(240), -2);
            gp.topMargin = dp(6);
            gp.bottomMargin = dp(6);
            col.addView(grant, gp);
        }
        col.addView(toggle(L.t("s_native"), set.nativeRes, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.nativeRes = on;
                set.save();
                srDynRow.setVisibility(on ? View.VISIBLE : View.GONE);
                applyGraphics();
            }
        }), rowLp);
        srDynRow = toggle(L.t("s_srdyn"), set.srDynamic, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                set.srDynamic = on;
                set.save();
            }
        });
        srDynRow.setVisibility(set.nativeRes ? View.VISIBLE : View.GONE);
        col.addView(srDynRow, rowLp);
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
        final LinearLayout rds = new LinearLayout(this);
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
                    for (int k = 0; k < rds.getChildCount(); k++) {
                        if (k == idx) style((Button) rds.getChildAt(k), 0xFF8a1a1a, 0xFFffcc33);
                        else style((Button) rds.getChildAt(k), 0xFF5A0F10, 0xFFB22A2A);
                    }
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
                    rebuildUi();
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
                exitBtn.setVisibility(canResume ? View.VISIBLE : View.GONE);
                // solo pause / main menu: the GL view draws the black hole behind; online pause keeps the game hidden
                menu.setBackgroundColor(0x00000000);
                settingsView.setVisibility(View.GONE);
                worldsView.setVisibility(View.GONE);
                multiView.setVisibility(View.GONE);
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

    private MediaPlayer menuP;
    private final android.os.Handler menuH = new android.os.Handler();
    private final Runnable menuTick = new Runnable() {
        @Override
        public void run() {
            try {
                boolean want = menu != null && menu.getVisibility() == View.VISIBLE && menuActive;
                if (want) {
                    if (menuP == null) {
                        menuP = MediaPlayer.create(MainActivity.this, R.raw.black_hole);
                        if (menuP != null) {
                            menuP.setLooping(true);
                            menuP.setVolume(.8f, .8f);
                        }
                    }
                    if (menuP != null && !menuP.isPlaying()) menuP.start();
                } else if (menuP != null && menuP.isPlaying()) {
                    menuP.pause();
                }
            } catch (Exception ignored) {
            }
            menuH.postDelayed(this, 300);
        }
    };
    private boolean menuActive;

    @Override
    protected void onPause() {
        super.onPause();
        menuActive = false;
        menuH.removeCallbacks(menuTick);
        try {
            if (menuP != null && menuP.isPlaying()) menuP.pause();
        } catch (Exception ignored) {
        }
        thermalH.removeCallbacks(thermalTick);
        stopRain();
        try {
            bhResume = bhP != null && bhP.isPlaying();
            if (bhResume) bhP.pause();
        } catch (Exception ignored) {
        }
        gl.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        menuActive = true;
        menuH.removeCallbacks(menuTick);
        menuH.post(menuTick);
        try {
            if (bhResume && bhP != null) bhP.start();
        } catch (Exception ignored) {
        }
        bhResume = false;
        gl.onResume();
        thermalH.removeCallbacks(thermalTick);
        thermalH.post(thermalTick);
        if (GameFiles.hasAccess(this)) GameFiles.migrateFallback(this);
        else ensureStorage();
        if (settingsView != null && GameFiles.hasAccess(this) != lastAccess) rebuildUi();
    }

    @Override
    protected void onDestroy() {
        stopRain();
        if (pool != null) pool.release();
        if (bhP != null) bhP.release();
        if (menuP != null) menuP.release();
        game.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (chatPanel != null) {
            closeChat();
            return;
        }
        if (editBar.getVisibility() == View.VISIBLE || over.getVisibility() == View.VISIBLE) return;
        if (worldsView.getVisibility() == View.VISIBLE) {
            worldsView.setVisibility(View.GONE);
            (worldsHost ? multiView : menu).setVisibility(View.VISIBLE);
            return;
        }
        if (multiView.getVisibility() == View.VISIBLE) {
            multiView.setVisibility(View.GONE);
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
