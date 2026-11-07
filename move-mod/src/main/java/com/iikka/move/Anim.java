package com.iikka.move;

import java.util.HashMap;
import java.util.Map;

/** Client-side: which players are flipping or dashing right now. */
public final class Anim {
    public static final byte STOP = 0, FLIP = 1, DASH = 2, WAVE = 3, DANCE = 4, CLAP = 5, BOW = 6, CHEER = 7;
    public static final byte SIT = 10, LAY = 11, CRAWL = 12;

    static long length(long kind) {
        return switch ((int) kind) {
            case FLIP -> 520; case DASH -> 380; case WAVE -> 2400; case DANCE -> 10000;
            case CLAP -> 2200; case BOW -> 1600; case CHEER -> 2400; default -> 1000;
        };
    }

    /** Emotes that stop when you move. */
    public static boolean isGesture(long kind) { return kind >= WAVE && kind <= CHEER; }

    public static void stop(int id) { running.remove(id); }

    /** Seconds since the animation started, or -1. */
    public static float seconds(int id, byte kind) {
        float t = progress(id, kind);
        return t < 0 ? -1 : t * length(kind) / 1000f;
    }
    private static final Map<Integer, long[]> running = new HashMap<>();

    public static void start(int id, byte kind) { running.put(id, new long[] { kind, System.nanoTime() }); }

    /** 0..1 progress of that animation, or -1 if it isn't playing. */
    public static float progress(int id, byte kind) {
        long[] a = running.get(id);
        if (a == null || a[0] != kind) return -1;
        float t = (System.nanoTime() - a[1]) / 1_000_000f / length(kind);
        if (t >= 1) { running.remove(id); return -1; }
        return t;
    }

    public static Map<Integer, long[]> all() { return running; }

    public static float easeInOut(float t) { return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    /** Goes 0 -> 1 -> 0 smoothly. */
    public static float bump(float t) { return (float) Math.sin(Math.PI * t); }
}
