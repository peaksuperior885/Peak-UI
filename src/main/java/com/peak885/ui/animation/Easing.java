package com.peak885.ui.animation;

@FunctionalInterface
public interface Easing {

    float ease(float t);

    Easing LINEAR = t -> t;

    Easing EASE_IN_QUAD = t -> t * t;
    Easing EASE_OUT_QUAD = t -> 1f - (1f - t) * (1f - t);
    Easing EASE_IN_OUT_QUAD = t -> t < 0.5f ? 2f * t * t : 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f;

    Easing EASE_IN_CUBIC = t -> t * t * t;
    Easing EASE_OUT_CUBIC = t -> 1f - (1f - t) * (1f - t) * (1f - t);
    Easing EASE_IN_OUT_CUBIC = t -> {
        if (t < 0.5f) return 4f * t * t * t;
        float u = -2f * t + 2f;
        return 1f - u * u * u / 2f;
    };

    Easing EASE_IN_QUART = t -> t * t * t * t;
    Easing EASE_OUT_QUART = t -> {
        float u = 1f - t;
        return 1f - u * u * u * u;
    };
    Easing EASE_IN_OUT_QUART = t -> {
        if (t < 0.5f) return 8f * t * t * t * t;
        float u = -2f * t + 2f;
        return 1f - u * u * u * u / 2f;
    };

    Easing EASE = cubicBezier(0.25f, 0.1f, 0.25f, 1f);
    Easing EASE_IN = cubicBezier(0.42f, 0f, 1f, 1f);
    Easing EASE_OUT = cubicBezier(0f, 0f, 0.58f, 1f);
    Easing EASE_IN_OUT = cubicBezier(0.42f, 0f, 0.58f, 1f);

    Easing EMPHASIZED_DECELERATE = cubicBezier(0.05f, 0.7f, 0.1f, 1f);
    Easing EMPHASIZED_ACCELERATE = cubicBezier(0.3f, 0f, 0.8f, 0.15f);

    static Easing cubicBezier(float x1, float y1, float x2, float y2) {
        return t -> {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            float s = t;
            for (int i = 0; i < 8; i++) {
                float x = bezierSample(s, x1, x2) - t;
                float dx = bezierSampleDerivative(s, x1, x2);
                if (Math.abs(dx) < 1e-6f) break;
                s -= x / dx;
                if (s < 0f) s = 0f;
                else if (s > 1f) s = 1f;
            }
            return bezierSample(s, y1, y2);
        };
    }

    private static float bezierSample(float s, float a, float b) {
        float u = 1f - s;
        return 3f * u * u * s * a + 3f * u * s * s * b + s * s * s;
    }

    private static float bezierSampleDerivative(float s, float a, float b) {
        float u = 1f - s;
        return 3f * u * u * a + 6f * u * s * (b - a) + 3f * s * s * (1f - b);
    }
}
