package com.iikka.move;

/** The cat dance from Iikka's video: swing arms out, cross them, hand to mouth, point, big sweep. Loops every 4.8 s. */
public final class CatDance {
    // time, rightArm x y z, leftArm x y z, head x z, body sway (degrees)
    private static final float[][] KEYS = {
        { 0.0f, -0.6f, 0f, 1.2f,  -0.6f, 0f, -1.2f,  0f, 0f,  6f },
        { 0.5f, -1.1f, 0f, 0.5f,  -1.1f, 0f, -0.5f,  0f, 0f, -6f },
        { 1.0f, -0.6f, 0f, 1.2f,  -0.6f, 0f, -1.2f,  0f, 0f,  6f },
        { 1.4f, -1.2f, -0.8f, 0f, -1.0f, 0.8f, 0f,   0.1f, 0f, 0f },
        { 1.9f, -2.3f, -0.5f, 0f, -0.9f, 0.9f, 0f,   0.2f, 0.12f, -3f },
        { 2.9f, -2.2f, -0.6f, 0f, -0.9f, 0.9f, 0f,   0.15f, -0.08f, -3f },
        { 3.3f, -2.7f, -0.3f, 0f, -0.9f, 0.9f, 0f,   0f, 0f, 0f },
        { 3.8f, -1.3f, 0.6f, 0.3f, -0.9f, 0.9f, 0f,  0f, 0f, 4f },
        { 4.3f, -1.3f, -1.0f, 0f, -0.5f, 0.3f, 0f,   0.1f, 0f, -4f },
        { 4.8f, -0.6f, 0f, 1.2f,  -0.6f, 0f, -1.2f,  0f, 0f,  6f },
    };

    /** Values at this time (seconds since start); index 0 unused. */
    public static float[] at(float sec) {
        float t = sec % 4.8f;
        int i = 0;
        while (i < KEYS.length - 2 && KEYS[i + 1][0] <= t) i++;
        float[] a = KEYS[i], b = KEYS[i + 1];
        float k = (t - a[0]) / (b[0] - a[0]);
        k = k * k * (3 - 2 * k);
        float[] out = new float[a.length];
        for (int j = 1; j < a.length; j++) out[j] = a[j] + (b[j] - a[j]) * k;
        // ease in at the start and out at the end
        float in = Math.min(1, sec * 4) * Math.min(1, (9.6f - sec) * 4);
        for (int j = 1; j < a.length; j++) out[j] *= in;
        return out;
    }
}
