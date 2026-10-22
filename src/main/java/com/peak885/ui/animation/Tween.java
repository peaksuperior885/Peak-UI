package com.peak885.ui.animation;

public final class Tween implements Animation {

    private float from;
    private float to;
    private float duration;
    private Easing easing;
    private float elapsed;
    private float current;
    private boolean finished;

    public Tween(float from, float to, float durationSeconds, Easing easing) {
        this.from = from;
        this.to = to;
        this.duration = Math.max(1e-6f, durationSeconds);
        this.easing = easing != null ? easing : Easing.LINEAR;
        this.current = from;
    }

    public static Tween of(float from, float to, float durationSeconds, Easing easing) {
        return new Tween(from, to, durationSeconds, easing);
    }

    public static Tween of(float from, float to, float durationSeconds) {
        return new Tween(from, to, durationSeconds, Easing.EASE_OUT_CUBIC);
    }

    public Tween restart(float from, float to, float durationSeconds, Easing easing) {
        this.from = from;
        this.to = to;
        this.duration = Math.max(1e-6f, durationSeconds);
        this.easing = easing != null ? easing : Easing.LINEAR;
        reset();
        return this;
    }

    public Tween retarget(float newTo) {
        this.from = current;
        this.to = newTo;
        this.elapsed = 0f;
        this.finished = false;
        return this;
    }

    @Override
    public float update(float dt) {
        if (finished) {
            return current;
        }
        elapsed += Math.max(0f, dt);
        float t = elapsed / duration;
        if (t >= 1f) {
            t = 1f;
            finished = true;
        }
        float k = easing.ease(t);
        current = from + (to - from) * k;
        return current;
    }

    @Override
    public float value() {
        return current;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void reset() {
        elapsed = 0f;
        current = from;
        finished = false;
    }

    public float from() {
        return from;
    }

    public float to() {
        return to;
    }

    public float duration() {
        return duration;
    }

    public float progress() {
        return Math.min(1f, elapsed / duration);
    }
}
