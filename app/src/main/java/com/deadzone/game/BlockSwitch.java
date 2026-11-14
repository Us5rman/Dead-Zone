package com.deadzone.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class BlockSwitch extends View {
    interface Listener {
        void changed(boolean on);
    }

    private boolean on;
    private Listener listener;
    private final Paint p = new Paint();
    private final float d;

    BlockSwitch(Context c, boolean on) {
        super(c);
        this.on = on;
        d = c.getResources().getDisplayMetrics().density;
        p.setAntiAlias(false);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BlockSwitch.this.on = !BlockSwitch.this.on;
                invalidate();
                if (listener != null) listener.changed(BlockSwitch.this.on);
            }
        });
    }

    void setListener(Listener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(60 * d), Math.round(30 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), b = 2 * d;
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? 0xFFB22A2A : 0xFF4A1818);
        c.drawRect(0, 0, w, h, p);
        p.setColor(on ? 0xFF5A0F10 : 0xFF1A0808);
        c.drawRect(b, b, w - b, h - b, p);
        float t = h - 4 * b;
        float x = on ? w - 2 * b - t : 2 * b;
        p.setColor(0xFF000000);
        c.drawRect(x - d, 2 * b - d, x + t + d, 2 * b + t + d, p);
        p.setColor(on ? 0xFFE23030 : 0xFF777777);
        c.drawRect(x, 2 * b, x + t, 2 * b + t, p);
        p.setColor(on ? 0xFFFF6A5A : 0xFF9A9A9A);
        c.drawRect(x, 2 * b, x + t, 2 * b + 2 * d, p);
        c.drawRect(x, 2 * b, x + 2 * d, 2 * b + t, p);
        p.setColor(on ? 0xFF8A1818 : 0xFF4A4A4A);
        c.drawRect(x, 2 * b + t - 2 * d, x + t, 2 * b + t, p);
        c.drawRect(x + t - 2 * d, 2 * b, x + t, 2 * b + t, p);
    }
}
