package com.peak885.ui;

public final class Theme {
    private Theme() {
    }

    public static final Rgba SURFACE = Rgba.hex(0x141218);
    public static final Rgba SURFACE_CONTAINER_LOW = Rgba.hex(0x1D1B20);
    public static final Rgba SURFACE_CONTAINER = Rgba.hex(0x211F26);
    public static final Rgba SURFACE_CONTAINER_HIGH = Rgba.hex(0x2B2930);
    public static final Rgba SURFACE_CONTAINER_HIGHEST = Rgba.hex(0x36343B);

    public static final Rgba ON_SURFACE = Rgba.hex(0xE6E0E9);
    public static final Rgba ON_SURFACE_VARIANT = Rgba.hex(0xCAC4D0);
    public static final Rgba OUTLINE = Rgba.hex(0x938F99);
    public static final Rgba OUTLINE_VARIANT = Rgba.hex(0x49454F);

    public static final Rgba PRIMARY = Rgba.hex(0xD0BCFF);
    public static final Rgba ON_PRIMARY = Rgba.hex(0x381E72);
    public static final Rgba PRIMARY_CONTAINER = Rgba.hex(0x4F378B);
    public static final Rgba ON_PRIMARY_CONTAINER = Rgba.hex(0xEADDFF);

    public static final Rgba SECONDARY_CONTAINER = Rgba.hex(0x4A4458);
    public static final Rgba ON_SECONDARY_CONTAINER = Rgba.hex(0xE8DEF8);

    public static final Rgba INVERSE_SURFACE = Rgba.hex(0xE6E0E9);
    public static final Rgba INVERSE_ON_SURFACE = Rgba.hex(0x322F35);

    public static final Rgba GLASS = new Rgba(0.30f, 0.28f, 0.35f, 0.42f);
    public static final Rgba GLASS_LIGHT = new Rgba(0.95f, 0.94f, 0.98f, 0.18f);
    public static final Rgba GLASS_BORDER = new Rgba(1f, 1f, 1f, 0.22f);
    public static final Rgba GLASS_BORDER_SUBTLE = new Rgba(1f, 1f, 1f, 0.10f);

    private static final Rgba[] ACCENT = {
            Rgba.hex(0x4F378B), Rgba.hex(0x633B48), Rgba.hex(0x1F4D5C), Rgba.hex(0x5C4300), Rgba.hex(0x2E4B2F)
    };
    private static final Rgba[] ON_ACCENT = {
            Rgba.hex(0xEADDFF), Rgba.hex(0xFFD8E4), Rgba.hex(0xBFE9FF), Rgba.hex(0xFFDFA0), Rgba.hex(0xBDEFB8)
    };

    public static Rgba accent(int index) {
        return ACCENT[Math.floorMod(index, ACCENT.length)];
    }

    public static Rgba onAccent(int index) {
        return ON_ACCENT[Math.floorMod(index, ON_ACCENT.length)];
    }
}
