package com.peak885.ui;

import com.peak885.ui.rendering.BoxRenderer;

public final class Box {
    private static final double RIPPLE_DURATION_SECONDS = 0.55;

    private float x;
    private float y;
    private float width;
    private float height;
    private float red;
    private float green;
    private float blue;
    private float opacity = 1.0f;
    private float cornerRadius;
    private float borderWidth;
    private float borderR;
    private float borderG;
    private float borderB;
    private float borderA;
    private float softness; // soft outer edge / faux blur in pixels
    private float rippleX;
    private float rippleY;
    private double rippleStartedAt = Double.NEGATIVE_INFINITY;

    public Box() {
    }

    public Box(float x, float y, float width, float height,
               float red, float green, float blue, float opacity,
               float cornerRadius) {
        bounds(x, y, width, height);
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.opacity = opacity;
        this.cornerRadius = cornerRadius;
    }

    public Box bounds(float x, float y, float width, float height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Box dimensions cannot be negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public Box color(float red, float green, float blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        return this;
    }

    public Box color(Rgba color) {
        this.red = color.r;
        this.green = color.g;
        this.blue = color.b;
        this.opacity = color.a;
        return this;
    }

    public Box opacity(float opacity) {
        this.opacity = opacity;
        return this;
    }

    public Box cornerRadius(float radius) {
        this.cornerRadius = radius;
        return this;
    }

    /** Stroke width in pixels (drawn inside the box edge). */
    public Box border(float width, Rgba color) {
        this.borderWidth = Math.max(0f, width);
        if (color != null) {
            this.borderR = color.r;
            this.borderG = color.g;
            this.borderB = color.b;
            this.borderA = color.a;
        }
        return this;
    }

    public Box border(float width, float r, float g, float b, float a) {
        this.borderWidth = Math.max(0f, width);
        this.borderR = r;
        this.borderG = g;
        this.borderB = b;
        this.borderA = a;
        return this;
    }

    /**
     * Soft outer falloff in pixels. Higher values give a frosted / blurred edge
     * suitable for glassmorphism-style panels (approximate, not a true background blur).
     */
    public Box softness(float pixels) {
        this.softness = Math.max(0f, pixels);
        return this;
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public void draw(BoxRenderer renderer) {
        renderer.drawBox(x, y, width, height, red, green, blue, opacity, cornerRadius,
                borderWidth, borderR, borderG, borderB, borderA, softness);
    }

    public void press(float localX, float localY, double timeSeconds) {
        rippleX = localX;
        rippleY = localY;
        rippleStartedAt = timeSeconds;
    }

    public void draw(BoxRenderer renderer, double timeSeconds) {
        double elapsed = timeSeconds - rippleStartedAt;
        if (elapsed < 0 || elapsed >= RIPPLE_DURATION_SECONDS) {
            draw(renderer);
            return;
        }
        float progress = (float) (elapsed / RIPPLE_DURATION_SECONDS);
        float easedProgress = 1.0f - (1.0f - progress) * (1.0f - progress);
        float fade = 1.0f - progress;
        renderer.drawRippleBox(x, y, width, height, red, green, blue, opacity,
                cornerRadius, borderWidth, borderR, borderG, borderB, borderA, softness,
                rippleX, rippleY, easedProgress, fade * 0.32f);
    }

    public static Builder at(float x, float y, float width, float height) {
        return new Builder(x, y, width, height);
    }

    public static final class Builder {
        private final float x, y, width, height;
        private float red, green, blue, opacity = 1.0f, cornerRadius;
        private float borderWidth, borderR, borderG, borderB, borderA;
        private float softness;

        private Builder(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Builder color(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            return this;
        }

        public Builder color(Rgba c) {
            this.red = c.r;
            this.green = c.g;
            this.blue = c.b;
            this.opacity = c.a;
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = opacity;
            return this;
        }

        public Builder cornerRadius(float radius) {
            this.cornerRadius = radius;
            return this;
        }

        public Builder border(float width, Rgba color) {
            this.borderWidth = Math.max(0f, width);
            if (color != null) {
                this.borderR = color.r;
                this.borderG = color.g;
                this.borderB = color.b;
                this.borderA = color.a;
            }
            return this;
        }

        public Builder softness(float pixels) {
            this.softness = Math.max(0f, pixels);
            return this;
        }

        public Box build() {
            Box b = new Box(x, y, width, height, red, green, blue, opacity, cornerRadius);
            b.border(borderWidth, borderR, borderG, borderB, borderA);
            b.softness(softness);
            return b;
        }
    }
}
