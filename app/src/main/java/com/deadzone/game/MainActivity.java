package com.deadzone.game;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity implements Game.Ui {
    private Game game;
    private GLSurfaceView gl;
    private Overlay overlay;
    private View menu, over;
    private TextView status, overText;
    private Button again;
    private EditText code;
    private float d;

    private int dp(float v) {
        return Math.round(v * d);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        game = new Game(this);
        gl = new GLSurfaceView(this);
        gl.setEGLContextClientVersion(2);
        gl.setRenderer(game);
        game.view = gl;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int big = Math.max(dm.widthPixels, dm.heightPixels), small = Math.min(dm.widthPixels, dm.heightPixels);
        int fh = Math.min(260, small);
        int fw = Math.round(big * (float) fh / small);
        gl.getHolder().setFixedSize(fw, fh);

        overlay = new Overlay(this, game);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(gl, new FrameLayout.LayoutParams(-1, -1));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        menu = buildMenu();
        over = buildOver();
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
        root.addView(over, new FrameLayout.LayoutParams(-1, -1));
        over.setVisibility(View.GONE);
        setContentView(root);
    }

    private TextView text(String s, float sp, int color, boolean mono) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        return t;
    }

    private Button button(String s, float sp) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(s);
        b.setTextSize(sp);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF5A0F10);
        bg.setStroke(dp(2), 0xFFB22A2A);
        b.setBackground(bg);
        b.setPadding(dp(18), dp(6), dp(18), dp(6));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        return b;
    }

    private TextView para(String s) {
        TextView t = text(s, 13, 0xFFd8d8d8, false);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private View buildMenu() {
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text("DEAD ZONE", 34, 0xFFcc2222, true);
        title.setGravity(Gravity.CENTER);
        left.addView(title);
        left.addView(para("شب است و مرده‌های متحرک همه‌جا هستند. تا جایی که می‌توانی زنده بمان."));
        left.addView(para("جوی‌استیک چپ حرکت، کشیدن انگشت روی سمت راست نشانه‌گیری، دکمه FIRE شلیک. شلیک به سر ۳ برابر آسیب دارد."));
        left.addView(para("چندنفره: HOST را بزن و کد ۴ حرفی را برای دوستانت بفرست؛ آن‌ها کد را وارد و JOIN می‌زنند (تا ۴ نفر)."));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);

        Button solo = button("START (SOLO)", 16);
        Button host = button("HOST ROOM", 16);
        Button join = button("JOIN", 16);
        code = new EditText(this);
        code.setHint("CODE");
        code.setHintTextColor(0xFF777777);
        code.setTextColor(Color.WHITE);
        code.setTextSize(18);
        code.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        code.setGravity(Gravity.CENTER);
        code.setSingleLine(true);
        code.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(4)});
        status = text("", 13, 0xFFcccc88, false);
        status.setTextDirection(View.TEXT_DIRECTION_RTL);
        status.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(8);
        right.addView(solo, bp);
        right.addView(host, bp);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(code, new LinearLayout.LayoutParams(dp(100), -2));
        LinearLayout.LayoutParams jp = new LinearLayout.LayoutParams(0, -2, 1f);
        jp.leftMargin = dp(8);
        row.addView(join, jp);
        right.addView(row, bp);
        right.addView(status, bp);

        solo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gl.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        game.startSolo();
                    }
                });
            }
        });
        host.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gl.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        game.startHost();
                    }
                });
            }
        });
        join.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String c = code.getText().toString();
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (im != null) im.hideSoftInputFromWindow(code.getWindowToken(), 0);
                gl.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        game.startJoin(c);
                    }
                });
            }
        });

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setGravity(Gravity.CENTER_VERTICAL);
        cols.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.rightMargin = dp(12);
        cols.addView(left, lp);
        cols.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(0xE6000000);
        sv.addView(cols, new FrameLayout.LayoutParams(-1, -1));
        return sv;
    }

    private View buildOver() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xCC000000);
        TextView t = text("YOU DIED", 38, 0xFFcc2222, true);
        t.setGravity(Gravity.CENTER);
        overText = text("", 16, 0xFFd8d8d8, false);
        overText.setGravity(Gravity.CENTER);
        overText.setTextDirection(View.TEXT_DIRECTION_RTL);
        again = button("RESTART", 18);
        col.addView(t);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.topMargin = dp(8);
        col.addView(overText, p);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(16);
        col.addView(again, bp);
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gl.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        game.again();
                    }
                });
            }
        });
        return col;
    }

    /* ---- Game.Ui ---- */

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
        overlay.postInvalidateOnAnimation();
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
        gl.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gl.onResume();
    }

    @Override
    protected void onDestroy() {
        game.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
