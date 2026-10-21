package com.peak885.ui;

public final class Rgba {
    public final float r;
    public final float g;
    public final float b;
    public final float a;

    public Rgba(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public static Rgba hex(int rgb) {
        return new Rgba(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }

    public Rgba alpha(float alpha) {
        return new Rgba(r, g, b, alpha);
    }

    public Rgba mix(Rgba other, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        return new Rgba(
                r + (other.r - r) * k,
                g + (other.g - g) * k,
                b + (other.b - b) * k,
                a + (other.a - a) * k);
    }
}
