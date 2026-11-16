package com.deadzone.game;

final class Weapons {
    static final String[] NAME = {"PISTOL", "SMG", "SHOTGUN", "RIFLE", "LMG", "SNIPER"};
    static final int[] UNLOCK = {0, 30, 80, 150, 260, 400};
    static final int[] MAG = {12, 30, 8, 24, 60, 6};
    static final double[] CD = {.30, .085, .85, .13, .075, 1.1};
    static final double[] DMG = {1, .7, .9, 1.5, 1.1, 6};
    static final int[] PEL = {1, 1, 6, 1, 1, 1};
    static final double[] SPREAD = {1, 1.2, 5, .8, 1.5, .1};
    static final double[] RELOAD = {1, .85, 1.3, 1, 1.5, 1.2};
    static final double[] KICK = {1, .6, 1.6, .8, .7, 2.2};
    static final double[] MOB = {1, .97, .93, .90, .80, .87};

    private Weapons() {
    }

    static int clamp(int w) {
        return Math.max(0, Math.min(NAME.length - 1, w));
    }
}
