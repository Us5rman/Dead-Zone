package com.deadzone.game;

final class Hud {
    volatile boolean playing;
    volatile double hp = 100;
    volatile int wave = 1, kills, mag = 12, res = 60;
    volatile boolean reloading, dead;
    volatile String room = "";
    volatile String banner = "";
    volatile double banT;
    volatile double vig;
    volatile double stickX, stickY;
    volatile int fps;
    volatile long ping = -1;
}
