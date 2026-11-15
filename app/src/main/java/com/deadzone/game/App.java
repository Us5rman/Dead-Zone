package com.deadzone.game;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this);
        CrashLog.exitInfo(this);
    }
}
