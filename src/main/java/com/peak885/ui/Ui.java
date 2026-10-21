package com.peak885.ui;

import com.peak885.ui.rendering.BoxRenderer;
import com.peak885.ui.rendering.TextRenderer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glScissor;

public final class Ui implements AutoCloseable {

    public static final int CURSOR_ARROW = 0;
    public static final int CURSOR_HAND = 1;
    public static final int CURSOR_IBEAM = 2;

    public enum Font {
        LABEL(12), BODY(14), TITLE(16), HEADLINE(24), DISPLAY(36);

        public final int size;

        Font(int size) {
            this.size = size;
        }
    }

    public static final class Interaction {
        public boolean hovered;
        public boolean pressed;
        public boolean clicked;
    }

    public interface TextBackend {
        int textWidth(Font font, String text);
        void drawText(Font font, String text, int x, int baseline, Rgba color);
    }

    private static final class BoxCmd {
        final Box box;
        final int[] clip;
        final int layer;

        BoxCmd(Box box, int[] clip, int layer) {
            this.box = box;
            this.clip = clip;
            this.layer = layer;
        }
    }

    private static final class TextCmd {
        final Font font;
        final String text;
        final int x;
        final int y;
        final Rgba color;
        final int[] clip;
        final int layer;

        TextCmd(Font font, String text, int x, int y, Rgba color, int[] clip, int layer) {
            this.font = font;
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.clip = clip;
            this.layer = layer;
        }
    }

    private final BoxRenderer boxRenderer;
    private final TextBackend textBackend;
    private final EnumMap<Font, TextRenderer> fonts = new EnumMap<>(Font.class);
    private final List<BoxCmd> boxCmds = new ArrayList<>();
    private final List<TextCmd> textCmds = new ArrayList<>();
    private final Deque<int[]> clips = new ArrayDeque<>();
    private final Map<String, Box> boxes = new HashMap<>();
    private final Map<String, Float> anims = new HashMap<>();

    public int width;
    public int height;
    public float mouseX;
    public float mouseY;
    public boolean mouseDown;
    public boolean mousePressed;
    public boolean mouseReleased;
    public float scrollY;
    public final StringBuilder typed = new StringBuilder();
    public int backspaces;
    public boolean escapePressed;
    public double time;
    public float dt;

    private double lastTime = -1;
    private String pressedId;
    private int cursorKind;
    private int layer;
    private int maxLayer;
    private int[] appliedClip;
    private boolean appliedValid;

    public Ui(int width, int height) {
        this(width, height, null);
    }

    /** Uses a platform text pipeline instead of Peak UI's custom glyph shader. */
    public Ui(int width, int height, TextBackend textBackend) {
        this.width = width;
        this.height = height;
        this.textBackend = textBackend;
        this.boxRenderer = new BoxRenderer(width, height);
        if (textBackend == null) {
            for (Font f : Font.values()) fonts.put(f, new TextRenderer(width, height, f.size));
        }
    }
    public void beginFrame(int fbWidth, int fbHeight, float mx, float my, double now) {
        double delta = lastTime < 0 ? 0 : now - lastTime;
        lastTime = now;
        dt = (float) Math.max(0.0, Math.min(0.1, delta));
        time = now;
        width = fbWidth;
        height = fbHeight;
        mouseX = mx;
        mouseY = my;

        boxRenderer.resize(fbWidth, fbHeight);
        for (TextRenderer tr : fonts.values()) {
            tr.resize(fbWidth, fbHeight);
        }

        boxCmds.clear();
        textCmds.clear();
        clips.clear();
        layer = 0;
        maxLayer = 0;
        cursorKind = CURSOR_ARROW;
    }

    public void endFrame() {
        if (mouseReleased) {
            pressedId = null;
        }
        mousePressed = false;
        mouseReleased = false;
        scrollY = 0f;
        escapePressed = false;
        typed.setLength(0);
        backspaces = 0;
    }

    public int cursorKind() {
        return cursorKind;
    }

    public void requestCursor(int kind) {
        cursorKind = Math.max(cursorKind, kind);
    }

    public void setLayer(int layer) {
        this.layer = layer;
        this.maxLayer = Math.max(maxLayer, layer);
    }

    public float anim(String id, float target, float speed) {
        float v = anims.containsKey(id) ? anims.get(id) : target;
        v += (target - v) * (1f - (float) Math.exp(-speed * dt));
        anims.put(id, v);
        return v;
    }

    public void pushClip(float x, float y, float w, float h) {
        int x0 = Math.round(x);
        int y0 = Math.round(y);
        int x1 = Math.round(x + w);
        int y1 = Math.round(y + h);
        int[] parent = clips.peek();
        if (parent != null) {
            x0 = Math.max(x0, parent[0]);
            y0 = Math.max(y0, parent[1]);
            x1 = Math.min(x1, parent[0] + parent[2]);
            y1 = Math.min(y1, parent[1] + parent[3]);
        }
        clips.push(new int[]{x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0)});
    }

    public void popClip() {
        clips.pop();
    }

    public boolean hit(float x, float y, float w, float h) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        int[] c = clips.peek();
        return c == null
                || (mouseX >= c[0] && mouseX < c[0] + c[2] && mouseY >= c[1] && mouseY < c[1] + c[3]);
    }

    public Interaction interact(String id, float x, float y, float w, float h) {
        Interaction in = new Interaction();
        in.hovered = hit(x, y, w, h);
        if (in.hovered) {
            requestCursor(CURSOR_HAND);
            if (mousePressed) {
                pressedId = id;
            }
        }
        in.pressed = in.hovered && mouseDown && id.equals(pressedId);
        in.clicked = in.hovered && mouseReleased && id.equals(pressedId);
        return in;
    }

    private Box boxFor(String id) {
        Box b = boxes.get(id);
        if (b == null) {
            b = new Box();
            boxes.put(id, b);
        }
        return b;
    }

    public void rect(float x, float y, float w, float h, Rgba c, float radius) {
        rect(x, y, w, h, c, radius, 0f, null, 0f);
    }

    /**
     * Rounded rect with optional border and soft outer edge (faux blur / glass glow).
     * @param softness soft outer falloff in pixels; 0 = hard AA edge only
     */
    public void rect(float x, float y, float w, float h, Rgba c, float radius,
                     float borderWidth, Rgba border, float softness) {
        if (c.a <= 0f || w <= 0f || h <= 0f) {
            return;
        }
        Box box = new Box(x, y, w, h, c.r, c.g, c.b, c.a, radius);
        if (borderWidth > 0f && border != null) {
            box.border(borderWidth, border);
        }
        if (softness > 0f) {
            box.softness(softness);
        }
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));
    }

    public void outlined(float x, float y, float w, float h, float radius, float thickness,
                         Rgba border, Rgba fill) {
        rect(x, y, w, h, border, radius);
        rect(x + thickness, y + thickness, w - 2 * thickness, h - 2 * thickness, fill,
                Math.max(0f, radius - thickness));
    }

    public void shadow(float x, float y, float w, float h, float radius) {
        Rgba black = new Rgba(0f, 0f, 0f, 0.05f);
        for (int i = 1; i <= 6; i++) {
            float s = i * 2f;
            rect(x - s, y - s + 4f, w + 2 * s, h + 2 * s, black, radius + s);
        }
    }

    public Interaction surface(String id, float hitX, float hitY, float hitW, float hitH,
                               float x, float y, float w, float h, float radius,
                               Rgba base, Rgba layerColor) {
        Interaction in = interact(id, hitX, hitY, hitW, hitH);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);
        Box box = boxFor(id);
        if (in.hovered && mousePressed) {
            box.press(mouseX - x, mouseY - y, time);
        }
        float amount = 0.08f * hover + (in.pressed ? 0.04f : 0f);
        box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                .cornerRadius(radius)
                .color(base.mix(layerColor, amount))
                .border(0f, 0f, 0f, 0f, 0f)
                .softness(0f);
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        return in;
    }

    public Interaction surface(String id, float x, float y, float w, float h, float radius,
                               Rgba base, Rgba layerColor) {
        return surface(id, x, y, w, h, x, y, w, h, radius, base, layerColor);
    }

    // -------------------------------------------------------------------------
    // Compose Multiplatform–inspired higher-level components
    // -------------------------------------------------------------------------

    /**
     * Elevated card: soft shadow + filled surface (Material / Compose Card).
     * @param elevation approximate elevation in dp-like pixels (controls shadow strength)
     */
    public void card(float x, float y, float w, float h, float radius, float elevation, Rgba fill) {
        if (elevation > 0f) {
            elevatedShadow(x, y, w, h, radius, elevation);
        }
        rect(x, y, w, h, fill, radius);
    }

    /** Card with a subtle outline (outlined Card style). */
    public void outlinedCard(float x, float y, float w, float h, float radius, Rgba fill, Rgba outline) {
        rect(x, y, w, h, fill, radius, 1.25f, outline, 0f);
    }

    /**
     * Glass / frosted panel — semi-transparent fill, light border, soft outer edge.
     * Looks closest to Compose / CSS glassmorphism when drawn over busy content.
     * Note: softness is an edge glow approximation, not a true background sample blur.
     */
    public void glass(float x, float y, float w, float h, float radius) {
        glass(x, y, w, h, radius, Theme.GLASS, Theme.GLASS_BORDER, 10f);
    }

    public void glass(float x, float y, float w, float h, float radius,
                      Rgba fill, Rgba border, float softness) {
        // Slight inner highlight for depth
        rect(x, y, w, h, fill, radius, 1.0f, border != null ? border : Theme.GLASS_BORDER, softness);
    }

    /**
     * Interactive glass surface (hover + press state layer + ripple), glassmorphism style.
     */
    public Interaction glassSurface(String id, float x, float y, float w, float h, float radius) {
        return glassSurface(id, x, y, w, h, radius, Theme.GLASS, Theme.GLASS_BORDER, 10f);
    }

    public Interaction glassSurface(String id, float x, float y, float w, float h, float radius,
                                    Rgba base, Rgba border, float softness) {
        Interaction in = interact(id, x, y, w, h);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 18f);
        Box box = boxFor(id);
        if (in.hovered && mousePressed) {
            box.press(mouseX - x, mouseY - y, time);
        }
        float amount = 0.10f * hover + (in.pressed ? 0.06f : 0f);
        Rgba tinted = base.mix(Theme.ON_SURFACE, amount * 0.35f);
        // Boost alpha slightly on hover so glass feels responsive
        float a = Math.min(1f, tinted.a + 0.08f * hover);
        box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                .cornerRadius(radius)
                .color(new Rgba(tinted.r, tinted.g, tinted.b, a))
                .border(1.15f, border != null ? border : Theme.GLASS_BORDER)
                .softness(softness);
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        return in;
    }

    /** Filled button (Compose FilledButton / Material filled). */
    public Interaction filledButton(String id, float x, float y, float w, float h, float radius) {
        Interaction in = surface(id, x, y, w, h, radius, Theme.PRIMARY, Theme.ON_PRIMARY);
        return in;
    }

    /** Tonal / container button (Compose FilledTonalButton). */
    public Interaction tonalButton(String id, float x, float y, float w, float h, float radius) {
        return surface(id, x, y, w, h, radius, Theme.PRIMARY_CONTAINER, Theme.ON_SURFACE);
    }

    /** Outlined button with transparent fill and border. */
    public Interaction outlinedButton(String id, float x, float y, float w, float h, float radius) {
        Interaction in = interact(id, x, y, w, h);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);
        Box box = boxFor(id);
        if (in.hovered && mousePressed) {
            box.press(mouseX - x, mouseY - y, time);
        }
        float amount = 0.12f * hover + (in.pressed ? 0.06f : 0f);
        Rgba fill = Theme.SURFACE.mix(Theme.PRIMARY, amount).alpha(amount > 0.01f ? 0.55f * amount + 0.08f : 0.04f);
        box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                .cornerRadius(radius)
                .color(fill)
                .border(1.25f, Theme.OUTLINE.mix(Theme.PRIMARY, hover * 0.4f));
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        return in;
    }

    /** Text-only button (no chrome until hover). */
    public Interaction textButton(String id, float x, float y, float w, float h, float radius) {
        Interaction in = interact(id, x, y, w, h);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 22f);
        if (hover > 0.01f || in.pressed) {
            Box box = boxFor(id);
            if (in.hovered && mousePressed) {
                box.press(mouseX - x, mouseY - y, time);
            }
            float amount = 0.10f * hover + (in.pressed ? 0.06f : 0f);
            Rgba fill = Theme.PRIMARY.alpha(amount);
            box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                    .cornerRadius(radius)
                    .color(fill);
            boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        }
        return in;
    }

    /** Horizontal divider (Compose HorizontalDivider). */
    public void divider(float x, float y, float w) {
        rect(x, y, w, 1f, Theme.OUTLINE_VARIANT.alpha(0.55f), 0f);
    }

    /** Soft elevated shadow scaled by elevation (1–24 typical). */
    public void elevatedShadow(float x, float y, float w, float h, float radius, float elevation) {
        float e = Math.max(0f, Math.min(24f, elevation));
        int layers = 1 + (int) (e / 4f);
        for (int i = 1; i <= layers; i++) {
            float t = i / (float) layers;
            float s = e * 0.35f * t;
            float oy = e * 0.22f * t;
            float a = 0.04f * (1f - t * 0.65f);
            rect(x - s, y - s + oy, w + 2 * s, h + 2 * s, new Rgba(0f, 0f, 0f, a), radius + s * 0.4f);
        }
    }

    /** Chip / pill (Compose AssistChip / FilterChip style). */
    public Interaction chip(String id, float x, float y, float w, float h, boolean selected) {
        float radius = h * 0.5f;
        Rgba base = selected ? Theme.SECONDARY_CONTAINER : Theme.SURFACE_CONTAINER_HIGH;
        Rgba border = selected ? Theme.SECONDARY_CONTAINER : Theme.OUTLINE_VARIANT;
        Interaction in = interact(id, x, y, w, h);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);
        Box box = boxFor(id);
        if (in.hovered && mousePressed) {
            box.press(mouseX - x, mouseY - y, time);
        }
        float amount = 0.08f * hover + (in.pressed ? 0.04f : 0f);
        box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                .cornerRadius(radius)
                .color(base.mix(Theme.ON_SURFACE, amount))
                .border(selected ? 0f : 1f, border);
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        return in;
    }

    public void text(Font font, String s, float x, float baseline, Rgba c) {
        if (s == null || s.isEmpty() || c.a <= 0f) {
            return;
        }
        textCmds.add(new TextCmd(font, s, Math.round(x), Math.round(baseline), c, clips.peek(), layer));
    }

    public void textLeft(Font font, String s, float x, float cy, Rgba c) {
        text(font, s, x, cy + font.size * 0.35f, c);
    }

    public void textCentered(Font font, String s, float cx, float cy, Rgba c) {
        text(font, s, cx - textWidth(font, s) / 2f, cy + font.size * 0.35f, c);
    }

    public int textWidth(Font font, String s) {
        return textBackend != null ? textBackend.textWidth(font, s) : fonts.get(font).getTextWidth(s);
    }

    public String fit(Font font, String s, int maxWidth) {
        if (textWidth(font, s) <= maxWidth) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && textWidth(font, t + "...") > maxWidth) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }


    public void flush() {
        for (int l = 0; l <= maxLayer; l++) {
            boxRenderer.begin();
            appliedValid = false;
            for (BoxCmd c : boxCmds) {
                if (c.layer != l) {
                    continue;
                }
                applyClip(c.clip);
                c.box.draw(boxRenderer, time);
            }
            applyClip(null);
            boxRenderer.end();
            appliedValid = false;

            for (TextCmd t : textCmds) {
                if (t.layer != l) {
                    continue;
                }
                applyClip(t.clip);
                if (textBackend != null) textBackend.drawText(t.font, t.text, t.x, t.y, t.color);
                else fonts.get(t.font).drawText(t.text, t.x, t.y, t.color.r, t.color.g, t.color.b, t.color.a);
            }
            applyClip(null);
        }
    }

    private void applyClip(int[] clip) {
        if (appliedValid && appliedClip == clip) {
            return;
        }
        if (clip == null) {
            glDisable(GL_SCISSOR_TEST);
        } else {
            glEnable(GL_SCISSOR_TEST);
            glScissor(clip[0], height - (clip[1] + clip[3]), clip[2], clip[3]);
        }
        appliedClip = clip;
        appliedValid = true;
    }

    @Override
    public void close() {
        try {
            boxRenderer.close();
        } finally {
            for (TextRenderer tr : fonts.values()) {
                tr.destroy();
            }
        }
    }
}

