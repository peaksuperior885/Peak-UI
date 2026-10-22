package com.peak885.ui.animation;

public final class Spring implements Animation {

    private float value;
    private float velocity;
    private float target;
    private float stiffness;
    private float damping;
    private float restDelta;
    private float restVelocity;
    private boolean finished;

    public Spring(float initial, float target, float stiffness, float damping) {
        this.value = initial;
        this.target = target;
        this.stiffness = Math.max(0.01f, stiffness);
        this.damping = Math.max(0.01f, damping);
        this.restDelta = 0.001f;
        this.restVelocity = 0.01f;
        this.finished = Math.abs(initial - target) < restDelta;
    }

    public static Spring of(float initial, float target) {
        return new Spring(initial, target, 300f, 28f);
    }

    public static Spring snappy(float initial, float target) {
        return new Spring(initial, target, 500f, 35f);
    }

    public static Spring soft(float initial, float target) {
        return new Spring(initial, target, 120f, 18f);
    }

    public Spring target(float target) {
        this.target = target;
        this.finished = false;
        return this;
    }

    public Spring configure(float stiffness, float damping) {
        this.stiffness = Math.max(0.01f, stiffness);
        this.damping = Math.max(0.01f, damping);
        this.finished = false;
        return this;
    }

    public Spring restThresholds(float delta, float velocity) {
        this.restDelta = Math.max(1e-6f, delta);
        this.restVelocity = Math.max(1e-6f, velocity);
        return this;
    }

    @Override
    public float update(float dt) {
        if (finished) {
            return value;
        }
        float step = Math.min(0.05f, Math.max(0f, dt));
        float force = -stiffness * (value - target) - damping * velocity;
        velocity += force * step;
        value += velocity * step;

        if (Math.abs(value - target) < restDelta && Math.abs(velocity) < restVelocity) {
            value = target;
            velocity = 0f;
            finished = true;
        }
        return value;
    }

    @Override
    public float value() {
        return value;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void reset() {
        velocity = 0f;
        finished = Math.abs(value - target) < restDelta;
    }

    public Spring snap(float v) {
        this.value = v;
        this.target = v;
        this.velocity = 0f;
        this.finished = true;
        return this;
    }

    public float velocity() {
        return velocity;
    }

    public float target() {
        return target;
    }
}
