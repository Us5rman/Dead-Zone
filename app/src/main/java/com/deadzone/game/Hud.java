package com.deadzone.game;

final class Hud {
    volatile boolean playing;
    volatile double hp = 100;
    volatile int wave = 1, kills, mag = 12, res = 60;
    volatile boolean reloading, dead;
    volatile String room = "";
    volatile String banner = "";
    volatile double banT;
    volatile double waveLeft = -1;
    volatile double vig;
    volatile double stickX, stickY;
    volatile double px, pz, pyaw, bh = .7;
    volatile float[] mz = new float[0];
    volatile float[] mp = new float[0];
    volatile int fps;
    volatile long ping = -1;
}
