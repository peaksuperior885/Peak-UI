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
    private final Map<String, Boolean> dropdowns = new HashMap<>();

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
    /** Currently focused text field id, or null. */
    private String focusedId;
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

    /** Currently focused text-field id, or {@code null}. */
    public String focusedId() {
        return focusedId;
    }

    public void clearFocus() {
        focusedId = null;
    }

    public boolean isFocused(String id) {
        return id != null && id.equals(focusedId);
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

    /**
     * Result of a {@link #tabs} call: the (possibly updated) selected index and whether
     * the user just changed it this frame.
     */
    public static final class TabsResult {
        public int selected;
        public boolean changed;
        /** Pixel X of the left edge of the selected tab (for custom indicator drawing). */
        public float indicatorX;
        /** Pixel width of the selected tab. */
        public float indicatorW;
    }

    /**
     * Material / Compose {@code TabRow}-style horizontal tabs.
     * <p>
     * Draws equal-width tabs with labels, a primary-color underline indicator that
     * animates between selections, and a subtle bottom divider. Returns the active
     * index (clamped) and whether it changed this frame.
     * <p>
     * Call once per frame; keep {@code selected} in your own state and feed it back:
     * <pre>{@code
     * int tab = 0;
     * // each frame:
     * Ui.TabsResult r = ui.tabs("main-tabs", 32, 120, 400, 40,
     *         new String[]{"Home", "Profile", "Settings"}, tab);
     * tab = r.selected;
     * if (r.changed) { /* switch content *\/ }
     * }</pre>
     *
     * @param id       stable interaction / animation id prefix
     * @param x        left of the tab row
     * @param y        top of the tab row
     * @param width    total width of the row (tabs share it equally)
     * @param height   tab height in pixels
     * @param labels   tab titles (must be non-null, length ≥ 1)
     * @param selected currently selected index (will be clamped)
     */
    public TabsResult tabs(String id, float x, float y, float width, float height,
                           String[] labels, int selected) {
        return tabs(id, x, y, width, height, labels, selected,
                Theme.PRIMARY, Theme.ON_SURFACE, Theme.ON_SURFACE_VARIANT);
    }

    /**
     * Tabs with explicit colors for indicator, selected label, and unselected label.
     */
    public TabsResult tabs(String id, float x, float y, float width, float height,
                           String[] labels, int selected,
                           Rgba indicatorColor, Rgba selectedLabel, Rgba unselectedLabel) {
        TabsResult result = new TabsResult();
        if (labels == null || labels.length == 0) {
            result.selected = 0;
            return result;
        }
        int n = labels.length;
        int sel = Math.floorMod(selected, n);
        float tabW = width / n;
        float indicatorH = 3f;

        // Bottom hairline
        rect(x, y + height - 1f, width, 1f, Theme.OUTLINE_VARIANT.alpha(0.45f), 0f);

        int clicked = -1;
        for (int i = 0; i < n; i++) {
            float tx = x + i * tabW;
            String tabId = id + "#tab" + i;
            Interaction in = interact(tabId, tx, y, tabW, height);
            float hover = anim(tabId + "#hover", in.hovered ? 1f : 0f, 22f);

            // Subtle hover wash
            if (hover > 0.01f || in.pressed) {
                float amount = 0.08f * hover + (in.pressed ? 0.05f : 0f);
                rect(tx, y, tabW, height, Theme.ON_SURFACE.alpha(amount), 0f);
            }

            boolean isSel = (i == sel);
            Rgba labelColor = isSel
                    ? selectedLabel
                    : unselectedLabel.mix(selectedLabel, hover * 0.35f);
            textCentered(Font.BODY, labels[i], tx + tabW * 0.5f, y + height * 0.5f, labelColor);

            if (in.clicked) {
                clicked = i;
            }
        }

        if (clicked >= 0 && clicked != sel) {
            sel = clicked;
            result.changed = true;
        }
        result.selected = sel;

        // Animated indicator position / width (smooth slide)
        float targetX = x + sel * tabW;
        float targetW = tabW;
        float indX = anim(id + "#indX", targetX, 18f);
        float indW = anim(id + "#indW", targetW, 18f);
        // Slightly inset indicator for a cleaner look
        float inset = Math.min(12f, tabW * 0.12f);
        rect(indX + inset, y + height - indicatorH, Math.max(0f, indW - 2f * inset), indicatorH,
                indicatorColor, indicatorH * 0.5f);

        result.indicatorX = indX;
        result.indicatorW = indW;
        return result;
    }

    /** Result of a Compose-style exposed dropdown menu. */
    public static final class DropdownResult {
        public int selected;
        public boolean changed;
        public boolean expanded;
    }

    /** Exposed dropdown field with an anchored Material-style menu. */
    public DropdownResult dropdown(String id, float x, float y, float w, float h,
                                   String[] options, int selected) {
        DropdownResult result = new DropdownResult();
        if (options == null || options.length == 0) {
            result.selected = 0;
            return result;
        }
        int sel = Math.max(0, Math.min(selected, options.length - 1));
        boolean expanded = dropdowns.getOrDefault(id, false);
        Interaction anchor = interact(id + "#anchor", x, y, w, h);
        if (anchor.clicked) expanded = !expanded;
        if (escapePressed && expanded) expanded = false;

        float menuY = y + h + 4f;
        float rowH = Math.max(40f, h);
        float menuH = rowH * options.length;
        if (menuY + menuH > height && y >= menuH + 4f) menuY = y - menuH - 4f;
        if (expanded && mousePressed && !hit(x, y, w, h)
                && !hit(x, menuY, w, menuH)) expanded = false;

        surface(id + "#anchor", x, y, w, h, 10f,
                Theme.SURFACE_CONTAINER_HIGH, Theme.ON_SURFACE);
        rect(x, y, w, h, Theme.SURFACE_CONTAINER_HIGH, 10f,
                1f, expanded ? Theme.PRIMARY : Theme.OUTLINE, 0f);
        textLeft(Font.BODY, fit(Font.BODY, options[sel], Math.max(0, (int) w - 48)),
                x + 14, y + h * 0.5f, Theme.ON_SURFACE);
        float cx = x + w - 20;
        float cy = y + h * 0.5f;
        textCentered(Font.BODY, expanded ? "⌃" : "⌄", cx, cy, Theme.ON_SURFACE_VARIANT);

        if (expanded) {
            setLayer(layer + 1);
            elevatedShadow(x, menuY, w, menuH, 8f, 4f);
            rect(x, menuY, w, menuH, Theme.SURFACE_CONTAINER, 8f,
                    1f, Theme.OUTLINE_VARIANT.alpha(0.65f), 0f);
            for (int i = 0; i < options.length; i++) {
                float oy = menuY + i * rowH;
                Interaction option = interact(id + "#option" + i, x + 4, oy + 2, w - 8, rowH - 4);
                if (i == sel) rect(x + 4, oy + 2, w - 8, rowH - 4, Theme.SECONDARY_CONTAINER, 6f);
                else if (option.hovered) rect(x + 4, oy + 2, w - 8, rowH - 4,
                        Theme.ON_SURFACE.alpha(0.08f), 6f);
                textLeft(Font.BODY, fit(Font.BODY, options[i], Math.max(0, (int) w - 32)),
                        x + 16, oy + rowH * 0.5f,
                        i == sel ? Theme.ON_SECONDARY_CONTAINER : Theme.ON_SURFACE);
                if (option.clicked) {
                    sel = i;
                    result.changed = sel != selected;
                    expanded = false;
                }
            }
            setLayer(layer - 1);
        }
        dropdowns.put(id, expanded);
        result.selected = sel;
        result.expanded = expanded;
        return result;
    }

    /**
     * Scrollable / content-sized tab row: each tab is only as wide as its label (+ padding).
     * Tabs that would overflow {@code maxWidth} are still drawn; clip externally if needed.
     *
     * @return same {@link TabsResult} contract as {@link #tabs}
     */
    public TabsResult tabsAuto(String id, float x, float y, float maxWidth, float height,
                               String[] labels, int selected) {
        TabsResult result = new TabsResult();
        if (labels == null || labels.length == 0) {
            result.selected = 0;
            return result;
        }
        int n = labels.length;
        int sel = Math.floorMod(selected, n);
        float hPad = 16f;
        float gap = 4f;

        float[] widths = new float[n];
        float total = 0f;
        for (int i = 0; i < n; i++) {
            widths[i] = textWidth(Font.BODY, labels[i]) + 2f * hPad;
            total += widths[i];
            if (i > 0) total += gap;
        }
        // If under maxWidth, we can center or left-align; left-align for simplicity
        float rowW = Math.min(maxWidth, total);

        rect(x, y + height - 1f, rowW, 1f, Theme.OUTLINE_VARIANT.alpha(0.45f), 0f);

        float cursor = x;
        float[] lefts = new float[n];
        int clicked = -1;
        for (int i = 0; i < n; i++) {
            lefts[i] = cursor;
            float tw = widths[i];
            String tabId = id + "#tab" + i;
            Interaction in = interact(tabId, cursor, y, tw, height);
            float hover = anim(tabId + "#hover", in.hovered ? 1f : 0f, 22f);
            if (hover > 0.01f || in.pressed) {
                float amount = 0.08f * hover + (in.pressed ? 0.05f : 0f);
                rect(cursor, y, tw, height, Theme.ON_SURFACE.alpha(amount), 8f);
            }
            boolean isSel = (i == sel);
            Rgba labelColor = isSel
                    ? Theme.PRIMARY
                    : Theme.ON_SURFACE_VARIANT.mix(Theme.PRIMARY, hover * 0.35f);
            textCentered(Font.BODY, labels[i], cursor + tw * 0.5f, y + height * 0.5f, labelColor);
            if (in.clicked) {
                clicked = i;
            }
            cursor += tw + gap;
        }

        if (clicked >= 0 && clicked != sel) {
            sel = clicked;
            result.changed = true;
        }
        result.selected = sel;

        float targetX = lefts[sel];
        float targetW = widths[sel];
        float indX = anim(id + "#indX", targetX, 18f);
        float indW = anim(id + "#indW", targetW, 18f);
        float inset = 6f;
        rect(indX + inset, y + height - 3f, Math.max(0f, indW - 2f * inset), 3f,
                Theme.PRIMARY, 1.5f);

        result.indicatorX = indX;
        result.indicatorW = indW;
        return result;
    }

    // -------------------------------------------------------------------------
    // Text fields, toggles, progress, and other Compose-inspired controls
    // -------------------------------------------------------------------------

    /**
     * Mutable text-field state returned by {@link #textField} / {@link #searchField}.
     * Keep the string in your own state and pass it back each frame (immediate mode).
     */
    public static final class TextFieldResult {
        public String text;
        public boolean focused;
        public boolean changed;
        public boolean submitted;
        public Interaction interaction = new Interaction();
    }

    /**
     * Material-style filled text field. Handles click-to-focus, character input via
     * {@link #typed}, backspace via {@link #backspaces}, and Escape to blur.
     * <p>
     * Pass the current value each frame; apply {@code result.text} back into your state.
     *
     * @param placeholder shown when {@code value} is empty and unfocused
     * @param maxLength   hard cap on characters (0 = unlimited)
     */
    public TextFieldResult textField(String id, float x, float y, float w, float h,
                                     String value, String placeholder, int maxLength) {
        return textFieldInternal(id, x, y, w, h, value, placeholder, maxLength, false);
    }

    /**
     * Search-oriented field: rounded pill, leading magnifier glyph, and a clear (×)
     * affordance when non-empty. Same text-input contract as {@link #textField}.
     */
    public TextFieldResult searchField(String id, float x, float y, float w, float h,
                                       String value, String placeholder) {
        return textFieldInternal(id, x, y, w, h, value != null ? value : "",
                placeholder != null ? placeholder : "Search", 0, true);
    }

    private TextFieldResult textFieldInternal(String id, float x, float y, float w, float h,
                                              String value, String placeholder, int maxLength,
                                              boolean searchStyle) {
        TextFieldResult r = new TextFieldResult();
        String text = value != null ? value : "";
        r.text = text;

        float radius = searchStyle ? h * 0.5f : 8f;
        float padL = searchStyle ? h * 0.55f + 8f : 14f;
        float padR = searchStyle ? (text.isEmpty() ? 14f : h * 0.55f + 8f) : 14f;

        Interaction in = interact(id, x, y, w, h);
        r.interaction = in;
        if (in.hovered) {
            requestCursor(CURSOR_IBEAM);
        }

        // Focus management: click focuses; click outside blurs; Escape blurs
        if (in.clicked) {
            focusedId = id;
        } else if (mousePressed && !in.hovered && id.equals(focusedId)) {
            focusedId = null;
        }
        if (escapePressed && id.equals(focusedId)) {
            focusedId = null;
        }
        boolean focused = id.equals(focusedId);
        r.focused = focused;

        // Consume keyboard input only when focused
        if (focused) {
            if (backspaces > 0 && !text.isEmpty()) {
                int n = Math.min(backspaces, text.length());
                text = text.substring(0, text.length() - n);
                r.changed = true;
            }
            if (typed.length() > 0) {
                StringBuilder sb = new StringBuilder(text);
                for (int i = 0; i < typed.length(); i++) {
                    char c = typed.charAt(i);
                    if (c == '\n' || c == '\r') {
                        r.submitted = true;
                        continue;
                    }
                    if (c >= 32) {
                        if (maxLength <= 0 || sb.length() < maxLength) {
                            sb.append(c);
                            r.changed = true;
                        }
                    }
                }
                text = sb.toString();
            }
            r.text = text;
        }

        float focusAmt = anim(id + "#focus", focused ? 1f : 0f, 22f);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);

        Rgba fill = Theme.SURFACE_CONTAINER_HIGHEST.mix(Theme.SURFACE_CONTAINER_HIGH, hover * 0.3f);
        Rgba border = Theme.OUTLINE_VARIANT.mix(Theme.PRIMARY, focusAmt);
        float borderW = 1f + focusAmt * 0.75f;

        Box box = boxFor(id);
        box.bounds(x, y, Math.max(0f, w), Math.max(0f, h))
                .cornerRadius(radius)
                .color(fill)
                .border(borderW, border)
                .softness(0f);
        boxCmds.add(new BoxCmd(box, clips.peek(), layer));

        // Search icon (simple geometric magnifier)
        if (searchStyle) {
            float cx = x + h * 0.38f;
            float cy = y + h * 0.5f;
            float rIcon = h * 0.14f;
            rect(cx - rIcon, cy - rIcon, rIcon * 2f, rIcon * 2f,
                    Theme.ON_SURFACE_VARIANT.alpha(0.55f), rIcon,
                    1.5f, Theme.ON_SURFACE_VARIANT.alpha(0.75f), 0f);
            // handle
            float hx = cx + rIcon * 0.55f;
            float hy = cy + rIcon * 0.55f;
            rect(hx, hy, h * 0.12f, 1.6f, Theme.ON_SURFACE_VARIANT.alpha(0.75f), 1f);
        }

        // Clear button for search
        if (searchStyle && !text.isEmpty()) {
            float clearSize = h * 0.45f;
            float cx = x + w - h * 0.5f;
            float cy = y + h * 0.5f;
            Interaction clear = interact(id + "#clear", cx - clearSize * 0.5f, cy - clearSize * 0.5f,
                    clearSize, clearSize);
            if (clear.hovered) {
                requestCursor(CURSOR_HAND);
            }
            rect(cx - clearSize * 0.35f, cy - clearSize * 0.35f, clearSize * 0.7f, clearSize * 0.7f,
                    Theme.ON_SURFACE_VARIANT.alpha(clear.hovered ? 0.25f : 0.12f), clearSize * 0.35f);
            textCentered(Font.LABEL, "×", cx, cy, Theme.ON_SURFACE_VARIANT);
            if (clear.clicked) {
                text = "";
                r.text = text;
                r.changed = true;
                focusedId = id;
            }
        }

        // Text / placeholder
        float textX = x + padL;
        float textMaxW = w - padL - padR;
        pushClip(textX, y, textMaxW, h);
        if (text.isEmpty() && !focused) {
            String ph = placeholder != null ? placeholder : "";
            textLeft(Font.BODY, fit(Font.BODY, ph, (int) textMaxW), textX, y + h * 0.5f,
                    Theme.ON_SURFACE_VARIANT.alpha(0.65f));
        } else {
            String display = fit(Font.BODY, text, (int) textMaxW);
            // If overflowing, prefer showing the end of the string (caret at end)
            if (textWidth(Font.BODY, text) > textMaxW) {
                // simple right-align by measuring tail
                display = text;
                while (display.length() > 1 && textWidth(Font.BODY, display) > textMaxW) {
                    display = display.substring(1);
                }
            }
            textLeft(Font.BODY, display, textX, y + h * 0.5f, Theme.ON_SURFACE);

            // Blinking caret when focused
            if (focused) {
                float caretOn = ((int) (time * 2.0) % 2 == 0) ? 1f : 0f;
                if (caretOn > 0f) {
                    float cw = textWidth(Font.BODY, display);
                    rect(textX + cw + 1f, y + h * 0.25f, 1.5f, h * 0.5f, Theme.PRIMARY, 0f);
                }
            }
        }
        popClip();

        return r;
    }

    /**
     * Compose {@code Switch} / Material switch. Returns the new on/off state
     * (toggles on click). Thumb animates between positions.
     */
    public boolean toggle(String id, float x, float y, float height, boolean on) {
        float h = Math.max(20f, height);
        float w = h * 1.7f;
        Interaction in = interact(id, x, y, w, h);
        if (in.clicked) {
            on = !on;
        }
        float t = anim(id + "#on", on ? 1f : 0f, 18f);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);

        Rgba trackOff = Theme.SURFACE_CONTAINER_HIGHEST.mix(Theme.OUTLINE, 0.35f);
        Rgba trackOn = Theme.PRIMARY;
        Rgba track = trackOff.mix(trackOn, t);
        float trackR = h * 0.5f;
        rect(x, y, w, h, track.mix(Theme.ON_SURFACE, hover * 0.06f), trackR);

        float thumbPad = h * 0.12f;
        float thumbD = h - 2f * thumbPad;
        float thumbX = x + thumbPad + t * (w - thumbD - 2f * thumbPad);
        float thumbY = y + thumbPad;
        // Soft shadow under thumb
        rect(thumbX + 1f, thumbY + 1.5f, thumbD, thumbD, new Rgba(0f, 0f, 0f, 0.18f), thumbD * 0.5f);
        rect(thumbX, thumbY, thumbD, thumbD, Theme.ON_PRIMARY.mix(Theme.ON_SURFACE, 1f - t * 0.15f), thumbD * 0.5f);
        return on;
    }

    /**
     * Checkbox with animated check. Returns the new checked state.
     */
    public boolean checkbox(String id, float x, float y, float size, boolean checked) {
        float s = Math.max(14f, size);
        Interaction in = interact(id, x, y, s, s);
        if (in.clicked) {
            checked = !checked;
        }
        float t = anim(id + "#chk", checked ? 1f : 0f, 20f);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);

        Rgba fill = Theme.SURFACE_CONTAINER_HIGH.mix(Theme.PRIMARY, t);
        Rgba border = Theme.OUTLINE.mix(Theme.PRIMARY, Math.max(t, hover * 0.5f));
        rect(x, y, s, s, fill, 4f, 1.5f, border, 0f);

        if (t > 0.05f) {
            // Simple checkmark via two thin rects
            float a = Math.min(1f, t * 1.4f);
            Rgba mark = Theme.ON_PRIMARY.alpha(a);
            float cx = x + s * 0.28f;
            float cy = y + s * 0.55f;
            // short leg
            rect(cx, cy, s * 0.18f, 2f, mark, 1f);
            // long leg
            rect(cx + s * 0.12f, cy - s * 0.22f, 2f, s * 0.38f, mark, 1f);
        }
        return checked;
    }

    /**
     * Linear progress bar (determinate). {@code progress} is clamped to {@code [0, 1]}.
     */
    public void progressBar(float x, float y, float w, float h, float progress) {
        progressBar(x, y, w, h, progress, Theme.SURFACE_CONTAINER_HIGH, Theme.PRIMARY);
    }

    public void progressBar(float x, float y, float w, float h, float progress, Rgba track, Rgba fill) {
        float p = Math.max(0f, Math.min(1f, progress));
        float r = h * 0.5f;
        rect(x, y, w, h, track, r);
        if (p > 0.001f) {
            rect(x, y, Math.max(h, w * p), h, fill, r);
        }
    }

    /**
     * Indeterminate progress bar — a sliding highlight that loops using {@link #time}.
     */
    public void progressIndeterminate(String id, float x, float y, float w, float h) {
        float r = h * 0.5f;
        rect(x, y, w, h, Theme.SURFACE_CONTAINER_HIGH, r);
        float cycle = (float) ((time * 0.85) % 1.6);
        float head = cycle < 1f ? cycle : 1f;
        float tail = cycle < 0.6f ? 0f : (cycle - 0.6f) / 1f;
        float left = x + w * tail;
        float right = x + w * Math.min(1f, head * 0.55f + tail);
        float bw = Math.max(h, right - left);
        // Keep a stable anim key so callers can have multiple bars
        anim(id + "#ind", head, 1f);
        pushClip(x, y, w, h);
        rect(left, y, bw, h, Theme.PRIMARY, r);
        popClip();
    }

    /**
     * Horizontal slider. Returns the new value in {@code [min, max]}.
     * Drag while pressed; value updates continuously.
     */
    public float slider(String id, float x, float y, float w, float h, float value, float min, float max) {
        if (max <= min) {
            max = min + 1f;
        }
        float trackH = Math.max(4f, h * 0.22f);
        float thumbR = Math.max(8f, h * 0.42f);
        float trackY = y + (h - trackH) * 0.5f;

        Interaction in = interact(id, x - thumbR, y, w + 2f * thumbR, h);
        float t = (value - min) / (max - min);
        t = Math.max(0f, Math.min(1f, t));

        if (in.pressed || (in.hovered && mouseDown && id.equals(pressedId))) {
            float nx = (mouseX - x) / w;
            t = Math.max(0f, Math.min(1f, nx));
            value = min + t * (max - min);
        }

        float animT = anim(id + "#t", t, 28f);
        // Prefer live value while dragging
        float drawT = (in.pressed) ? t : animT;

        rect(x, trackY, w, trackH, Theme.SURFACE_CONTAINER_HIGHEST, trackH * 0.5f);
        float filledW = w * drawT;
        if (filledW > 0.5f) {
            rect(x, trackY, filledW, trackH, Theme.PRIMARY, trackH * 0.5f);
        }

        float tx = x + w * drawT;
        float ty = y + h * 0.5f;
        float hover = anim(id + "#hover", in.hovered || in.pressed ? 1f : 0f, 20f);
        // Outer ring on hover
        if (hover > 0.01f) {
            float ring = thumbR + 4f * hover;
            rect(tx - ring, ty - ring, ring * 2f, ring * 2f, Theme.PRIMARY.alpha(0.12f * hover), ring);
        }
        rect(tx - thumbR + 1f, ty - thumbR + 1.5f, thumbR * 2f, thumbR * 2f,
                new Rgba(0f, 0f, 0f, 0.2f), thumbR);
        rect(tx - thumbR, ty - thumbR, thumbR * 2f, thumbR * 2f, Theme.PRIMARY, thumbR);
        return value;
    }

    /**
     * Floating action button (circular). Primary colored with elevation.
     */
    public Interaction fab(String id, float x, float y, float size) {
        float s = Math.max(40f, size);
        elevatedShadow(x, y, s, s, s * 0.5f, 8f);
        Interaction in = surface(id, x, y, s, s, s * 0.5f, Theme.PRIMARY_CONTAINER, Theme.ON_SURFACE);
        return in;
    }

    /**
     * Small circular icon-style button (no label — draw your glyph with {@link #textCentered}).
     */
    public Interaction iconButton(String id, float x, float y, float size) {
        float s = Math.max(28f, size);
        Interaction in = interact(id, x, y, s, s);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 22f);
        if (hover > 0.01f || in.pressed) {
            float amount = 0.10f * hover + (in.pressed ? 0.06f : 0f);
            Box box = boxFor(id);
            if (in.hovered && mousePressed) {
                box.press(mouseX - x, mouseY - y, time);
            }
            box.bounds(x, y, s, s)
                    .cornerRadius(s * 0.5f)
                    .color(Theme.ON_SURFACE.alpha(amount));
            boxCmds.add(new BoxCmd(box, clips.peek(), layer));
        }
        return in;
    }

    /**
     * Segmented button row (Compose {@code SingleChoiceSegmentedButtonRow}).
     * Returns the selected index (clamped). Equal-width segments.
     */
    public int segmented(String id, float x, float y, float w, float h, String[] labels, int selected) {
        if (labels == null || labels.length == 0) {
            return 0;
        }
        int n = labels.length;
        int sel = Math.floorMod(selected, n);
        float segW = w / n;
        float radius = h * 0.5f;

        // Outer outline
        rect(x, y, w, h, Theme.SURFACE_CONTAINER_HIGH, radius, 1.25f, Theme.OUTLINE_VARIANT, 0f);

        for (int i = 0; i < n; i++) {
            float sx = x + i * segW;
            String segId = id + "#seg" + i;
            Interaction in = interact(segId, sx, y, segW, h);
            if (in.clicked) {
                sel = i;
            }
            boolean isSel = (i == sel);
            float hover = anim(segId + "#hover", in.hovered ? 1f : 0f, 20f);

            if (isSel) {
                // Selected pill inset slightly
                float inset = 3f;
                rect(sx + inset, y + inset, segW - 2f * inset, h - 2f * inset,
                        Theme.SECONDARY_CONTAINER, radius - inset);
            } else if (hover > 0.01f) {
                rect(sx + 2f, y + 2f, segW - 4f, h - 4f,
                        Theme.ON_SURFACE.alpha(0.06f * hover), radius - 2f);
            }

            Rgba labelC = isSel ? Theme.ON_SECONDARY_CONTAINER : Theme.ON_SURFACE_VARIANT;
            textCentered(Font.LABEL, labels[i], sx + segW * 0.5f, y + h * 0.5f, labelC);

            // Divider between segments (except last)
            if (i < n - 1 && !isSel && sel != i + 1) {
                rect(sx + segW - 0.5f, y + h * 0.25f, 1f, h * 0.5f,
                        Theme.OUTLINE_VARIANT.alpha(0.5f), 0f);
            }
        }
        return sel;
    }

    /**
     * List item row: optional leading accent, title, supporting text, and click target.
     * Height is derived from content (~56–72).
     */
    public Interaction listItem(String id, float x, float y, float w, float h,
                                String title, String supporting) {
        Interaction in = interact(id, x, y, w, h);
        float hover = anim(id + "#hover", in.hovered ? 1f : 0f, 20f);
        if (hover > 0.01f || in.pressed) {
            float amount = 0.07f * hover + (in.pressed ? 0.04f : 0f);
            rect(x, y, w, h, Theme.ON_SURFACE.alpha(amount), 8f);
        }
        float textX = x + 16f;
        if (title != null) {
            float ty = supporting != null && !supporting.isEmpty()
                    ? y + h * 0.38f
                    : y + h * 0.5f;
            textLeft(Font.BODY, fit(Font.BODY, title, (int) (w - 32f)), textX, ty, Theme.ON_SURFACE);
        }
        if (supporting != null && !supporting.isEmpty()) {
            textLeft(Font.LABEL, fit(Font.LABEL, supporting, (int) (w - 32f)),
                    textX, y + h * 0.68f, Theme.ON_SURFACE_VARIANT);
        }
        return in;
    }

    /**
     * Notification-style badge (count or dot) drawn at the top-right of a region.
     * Pass {@code count < 0} for a plain dot.
     */
    public void badge(float anchorRight, float anchorTop, int count) {
        if (count == 0) {
            return;
        }
        if (count < 0) {
            float d = 10f;
            rect(anchorRight - d * 0.5f, anchorTop - d * 0.5f, d, d, Theme.PRIMARY, d * 0.5f);
            return;
        }
        String label = count > 99 ? "99+" : Integer.toString(count);
        float tw = textWidth(Font.LABEL, label);
        float bw = Math.max(18f, tw + 10f);
        float bh = 18f;
        rect(anchorRight - bw * 0.5f, anchorTop - bh * 0.5f, bw, bh, Theme.PRIMARY, bh * 0.5f);
        textCentered(Font.LABEL, label, anchorRight, anchorTop, Theme.ON_PRIMARY);
    }

    /**
     * Vertical linear gradient-like accent bar (custom Peak UI accent rail).
     * Useful beside list panels or as a section marker.
     */
    public void accentRail(float x, float y, float h, float thickness, Rgba color) {
        float t = Math.max(2f, thickness);
        rect(x, y, t, h, color, t * 0.5f);
        rect(x + t * 0.15f, y, t * 0.7f, h, color.alpha(color.a * 0.35f), t * 0.35f, 0f, null, t);
    }

    /**
     * Soft glow orb — decorative ambient blob (custom Peak UI flair).
     */
    public void glowOrb(float cx, float cy, float radius, Rgba color) {
        float r = Math.max(8f, radius);
        rect(cx - r, cy - r, r * 2f, r * 2f, color, r, 0f, null, r * 0.65f);
    }

    /**
     * Pill-shaped status chip with a colored dot (custom: online / offline / busy).
     */
    public void statusPill(float x, float y, float h, String label, Rgba statusColor) {
        if (label == null) label = "";
        float pad = 10f;
        float dot = h * 0.28f;
        float tw = textWidth(Font.LABEL, label);
        float w = pad + dot + 6f + tw + pad;
        rect(x, y, w, h, Theme.SURFACE_CONTAINER_HIGH, h * 0.5f, 1f, Theme.OUTLINE_VARIANT.alpha(0.5f), 0f);
        float dy = y + (h - dot) * 0.5f;
        rect(x + pad, dy, dot, dot, statusColor, dot * 0.5f);
        textLeft(Font.LABEL, label, x + pad + dot + 6f, y + h * 0.5f, Theme.ON_SURFACE);
    }

    /**
     * Simple tooltip bubble near a point. Draw on a high {@link #setLayer} so it sits on top.
     * Call only when you want it visible (e.g. after a hover delay in your code).
     */
    public void tooltip(float x, float y, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        float padX = 10f;
        float padY = 6f;
        float tw = textWidth(Font.LABEL, message);
        float bw = tw + padX * 2f;
        float bh = Font.LABEL.size + padY * 2f;
        elevatedShadow(x, y, bw, bh, 6f, 3f);
        rect(x, y, bw, bh, Theme.INVERSE_SURFACE, 6f);
        textLeft(Font.LABEL, message, x + padX, y + bh * 0.5f, Theme.INVERSE_ON_SURFACE);
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

