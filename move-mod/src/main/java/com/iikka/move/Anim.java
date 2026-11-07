package com.iikka.move;

import java.util.HashMap;
import java.util.Map;

/** Client-side: which players are flipping or dashing right now. */
public final class Anim {
    public static final byte FLIP = 1, DASH = 2;
    public static final long FLIP_MS = 520, DASH_MS = 380;
    private static final Map<Integer, long[]> running = new HashMap<>();

    public static void start(int id, byte kind) { running.put(id, new long[] { kind, System.nanoTime() }); }

    /** 0..1 progress of that animation, or -1 if it isn't playing. */
    public static float progress(int id, byte kind) {
        long[] a = running.get(id);
        if (a == null || a[0] != kind) return -1;
        float t = (System.nanoTime() - a[1]) / 1_000_000f / (kind == FLIP ? FLIP_MS : DASH_MS);
        if (t >= 1) { running.remove(id); return -1; }
        return t;
    }

    public static Map<Integer, long[]> all() { return running; }

    public static float easeInOut(float t) { return t < 0.5f ? 2 * t * t : 1 - (float) Math.pow(-2 * t + 2, 2) / 2; }

    /** Goes 0 -> 1 -> 0 smoothly. */
    public static float bump(float t) { return (float) Math.sin(Math.PI * t); }
}
