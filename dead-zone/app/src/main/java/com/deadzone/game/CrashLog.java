package com.deadzone.game;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

final class CrashLog {
    private CrashLog() {
    }

    static void install(final Context ctx) {
        final Context app = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("thread: " + t.getName());
                    pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + " android " + Build.VERSION.SDK_INT);
                    e.printStackTrace(pw);
                    pw.flush();
                    Log.e("deadzone", sw.toString());
                    save(app, "deadzone-crash.txt", sw.toString());
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    static void exitInfo(Context ctx) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> list = am.getHistoricalProcessExitReasons(null, 0, 4);
            StringBuilder sb = new StringBuilder();
            sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    .append(" android ").append(Build.VERSION.SDK_INT).append('\n');
            for (ApplicationExitInfo i : list) {
                sb.append("\n--- ").append(i.getTimestamp()).append(" reason=").append(i.getReason())
                        .append(" status=").append(i.getStatus()).append(" importance=").append(i.getImportance())
                        .append("\n").append(i.getDescription()).append('\n');
                if (i.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    InputStream in = i.getTraceInputStream();
                    if (in != null) {
                        byte[] buf = new byte[8000];
                        int n = in.read(buf);
                        if (n > 0) sb.append(new String(buf, 0, n, "UTF-8")).append('\n');
                        in.close();
                    }
                }
            }
            save(ctx, "deadzone-exit.txt", sb.toString());
        } catch (Throwable ignored) {
        }
    }

    private static void save(Context ctx, String name, String text) {
        try {
            FileOutputStream fo = new FileOutputStream(new File(ctx.getFilesDir(), name));
            fo.write(text.getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) {
        }
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            FileOutputStream fo = new FileOutputStream(new File(dir, name));
            fo.write(text.getBytes("UTF-8"));
            fo.close();
            return;
        } catch (Throwable ignored) {
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) {
                    OutputStream os = ctx.getContentResolver().openOutputStream(u);
                    if (os != null) {
                        os.write(text.getBytes("UTF-8"));
                        os.close();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
