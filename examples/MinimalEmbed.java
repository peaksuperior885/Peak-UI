package examples;

import com.peak885.ui.Rgba;
import com.peak885.ui.Theme;
import com.peak885.ui.Ui;
import com.peak885.ui.animation.AnimationDriver;
import com.peak885.ui.animation.Easing;
import com.peak885.ui.animation.Spring;
import com.peak885.ui.animation.Tween;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class MinimalEmbed {

    private final Ui ui = new Ui(1280, 720);
    private final AnimationDriver anims = new AnimationDriver();

    private boolean buttonWasClicked;
    private boolean glassClicked;
    private boolean chipOn;
    private int selectedTab;
    private boolean pulseStarted;

    private String searchQuery = "";
    private String nameField = "";
    private boolean darkMode = true;
    private boolean notifications = false;
    private float volume = 0.65f;
    private int segment = 0;
    private float loadProgress = 0.35f;

    /** Call once after GL.createCapabilities(). */
    public void init(long window) {
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_1) return;
            if (action == GLFW_PRESS) {
                ui.mouseDown = true;
                ui.mousePressed = true;
            } else if (action == GLFW_RELEASE) {
                ui.mouseDown = false;
                ui.mouseReleased = true;
            }
        });
        glfwSetScrollCallback(window, (w, x, y) -> ui.scrollY += (float) y);
        glfwSetCharCallback(window, (w, cp) -> {
            if (cp >= 32 && cp <= 126) ui.typed.appendCodePoint(cp);
        });
        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (key == GLFW_KEY_BACKSPACE && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
                ui.backspaces++;
            } else if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                ui.escapePressed = true;
            }
        });
    }

    public void frame(int fbW, int fbH, float mouseX, float mouseY, double timeSeconds) {
        ui.beginFrame(fbW, fbH, mouseX, mouseY, timeSeconds);

        ui.rect(0, 0, ui.width, ui.height, Theme.SURFACE, 0f);

        ui.rect(200, 50, 260, 190, Theme.PRIMARY_CONTAINER.alpha(0.55f), 48f, 0f, null, 30f);
        ui.rect(500, 140, 220, 160, Theme.accent(2).alpha(0.42f), 40f, 0f, null, 26f);
        ui.rect(60, 380, 180, 130, Theme.accent(1).alpha(0.38f), 36f, 0f, null, 22f);

        ui.text(Ui.Font.HEADLINE, "Peak UI", 32, 44, Theme.ON_SURFACE);
        ui.text(Ui.Font.BODY, "Compose-style components · glass & soft edges",
                32, 72, Theme.ON_SURFACE_VARIANT);

        float by = 100;
        float bh = 40;
        float gap = 12;
        float bw = 132;
        float x = 32;

        Ui.Interaction filled = ui.filledButton("btn-filled", x, by, bw, bh, 20f);
        ui.textCentered(Ui.Font.BODY, "Filled", x + bw * 0.5f, by + bh * 0.5f, Theme.ON_PRIMARY);
        x += bw + gap;

        Ui.Interaction tonal = ui.tonalButton("btn-tonal", x, by, bw, bh, 20f);
        ui.textCentered(Ui.Font.BODY, "Tonal", x + bw * 0.5f, by + bh * 0.5f, Theme.ON_PRIMARY_CONTAINER);
        x += bw + gap;

        Ui.Interaction outlined = ui.outlinedButton("btn-out", x, by, bw, bh, 20f);
        ui.textCentered(Ui.Font.BODY, "Outlined", x + bw * 0.5f, by + bh * 0.5f, Theme.PRIMARY);
        x += bw + gap;

        Ui.Interaction textBtn = ui.textButton("btn-text", x, by, 100, bh, 20f);
        ui.textCentered(Ui.Font.BODY, "Text", x + 50, by + bh * 0.5f, Theme.PRIMARY);

        if (filled.clicked || tonal.clicked || outlined.clicked || textBtn.clicked) {
            buttonWasClicked = true;
        }
        if (buttonWasClicked) {
            ui.text(Ui.Font.LABEL, "Button clicked", 32, by + bh + 18, Theme.PRIMARY);
        }

        float cy = 180;
        float cw = 280;
        float ch = 108;

        ui.card(32, cy, cw, ch, 16f, 6f, Theme.SURFACE_CONTAINER);
        ui.text(Ui.Font.TITLE, "Elevated card", 52, cy + 34, Theme.ON_SURFACE);
        ui.text(Ui.Font.BODY, "Shadow + Material surface", 52, cy + 62, Theme.ON_SURFACE_VARIANT);

        ui.outlinedCard(32 + cw + 16, cy, cw, ch, 16f,
                Theme.SURFACE_CONTAINER_LOW, Theme.OUTLINE_VARIANT);
        ui.text(Ui.Font.TITLE, "Outlined card", 32 + cw + 36, cy + 34, Theme.ON_SURFACE);
        ui.text(Ui.Font.BODY, "Border, no elevation", 32 + cw + 36, cy + 62, Theme.ON_SURFACE_VARIANT);

        float gy = 310;
        float gw = 340;
        float gh = 112;

        Ui.Interaction glass = ui.glassSurface("glass-panel", 32, gy, gw, gh, 20f);
        ui.text(Ui.Font.TITLE, "Glass surface", 56, gy + 36, Theme.ON_SURFACE);
        ui.text(Ui.Font.BODY, "Transparency + soft edge (hover / click)", 56, gy + 64, Theme.ON_SURFACE_VARIANT);
        if (glass.clicked) {
            glassClicked = true;
        }
        if (glassClicked) {
            ui.text(Ui.Font.LABEL, "Glass clicked", 56, gy + 92, Theme.PRIMARY);
        }

        ui.glass(32 + gw + 16, gy, 200, gh, 24f,
                Theme.GLASS_LIGHT, Theme.GLASS_BORDER_SUBTLE, 16f);
        ui.textCentered(Ui.Font.BODY, "Soft glass", 32 + gw + 16 + 100, gy + gh * 0.5f, Theme.ON_SURFACE);

        float dy = gy + gh + 24;
        ui.divider(32, dy, Math.min(600, ui.width - 64));

        float chipY = dy + 16;
        Ui.Interaction chip = ui.chip("chip-filter", 32, chipY, 100, 32, chipOn);
        ui.textCentered(Ui.Font.LABEL, chipOn ? "On" : "Filter", 32 + 50, chipY + 16, Theme.ON_SURFACE);
        if (chip.clicked) {
            chipOn = !chipOn;
        }

        ui.chip("chip-tag", 144, chipY, 88, 32, false);
        ui.textCentered(Ui.Font.LABEL, "Tag", 144 + 44, chipY + 16, Theme.ON_SURFACE);

        ui.rect(250, chipY - 4, 160, 40, Theme.GLASS, 12f, 1.2f, Theme.GLASS_BORDER, 10f);
        ui.textCentered(Ui.Font.LABEL, "rect + softness", 250 + 80, chipY + 16, Theme.ON_SURFACE);

        float tabsY = chipY + 52;
        ui.text(Ui.Font.TITLE, "Tabs", 32, tabsY, Theme.ON_SURFACE);
        Ui.TabsResult tabs = ui.tabs("demo-tabs", 32, tabsY + 12, 420, 40,
                new String[]{"Home", "Profile", "Settings"}, selectedTab);
        selectedTab = tabs.selected;

        float contentY = tabsY + 60;
        ui.outlinedCard(32, contentY, 420, 72, 12f, Theme.SURFACE_CONTAINER_LOW, Theme.OUTLINE_VARIANT);
        String content = switch (selectedTab) {
            case 1 -> "Profile content — avatar, bio, etc.";
            case 2 -> "Settings content — toggles, prefs.";
            default -> "Home content — feed / dashboard.";
        };
        ui.text(Ui.Font.BODY, content, 52, contentY + 40, Theme.ON_SURFACE_VARIANT);
        if (tabs.changed) {
            anims.put("tab-pulse", Tween.of(0f, 1f, 0.45f, Easing.EASE_OUT_CUBIC));
            pulseStarted = true;
        }

        anims.update(ui.dt);
        float pulse = pulseStarted ? anims.value("tab-pulse", 0f) : 0f;
        Spring bar = (Spring) anims.computeIfAbsent("tab-bar-x",
                () -> Spring.snappy(tabs.indicatorX, tabs.indicatorX));
        bar.target(tabs.indicatorX);
        float barX = bar.value();

        if (pulse > 0.01f) {
            ui.rect(barX, contentY - 4, tabs.indicatorW, 3f,
                    Theme.PRIMARY.alpha(1f - pulse * 0.7f), 1.5f);
        }

        float rx = 500;
        float ry = 100;

        ui.text(Ui.Font.TITLE, "Inputs & controls", rx, ry, Theme.ON_SURFACE);

        Ui.TextFieldResult search = ui.searchField("search", rx, ry + 16, 320, 40,
                searchQuery, "Search Peak UI…");
        searchQuery = search.text;

        Ui.TextFieldResult name = ui.textField("name", rx, ry + 68, 320, 44,
                nameField, "Display name", 32);
        nameField = name.text;

        float ty = ry + 130;
        ui.text(Ui.Font.LABEL, "Dark mode", rx, ty + 10, Theme.ON_SURFACE_VARIANT);
        darkMode = ui.toggle("tog-dark", rx + 90, ty, 28, darkMode);

        ui.text(Ui.Font.LABEL, "Notify", rx + 180, ty + 10, Theme.ON_SURFACE_VARIANT);
        notifications = ui.checkbox("chk-notify", rx + 230, ty + 4, 22, notifications);

        float sy = ty + 48;
        ui.text(Ui.Font.LABEL, "Volume  " + Math.round(volume * 100) + "%", rx, sy, Theme.ON_SURFACE_VARIANT);
        volume = ui.slider("vol", rx, sy + 14, 320, 28, volume, 0f, 1f);

        float py = sy + 56;
        ui.text(Ui.Font.LABEL, "Download", rx, py, Theme.ON_SURFACE_VARIANT);
        loadProgress = Math.min(1f, loadProgress + ui.dt * 0.04f);
        if (loadProgress >= 1f) loadProgress = 0f;
        ui.progressBar(rx, py + 14, 220, 8, loadProgress);
        ui.progressIndeterminate("ind-bar", rx, py + 32, 220, 6);

        float segY = py + 56;
        ui.text(Ui.Font.LABEL, "View", rx, segY, Theme.ON_SURFACE_VARIANT);
        segment = ui.segmented("view-seg", rx, segY + 12, 280, 36,
                new String[]{"List", "Grid", "Board"}, segment);

        float ly = segY + 64;
        ui.accentRail(rx, ly, 120, 4f, Theme.PRIMARY);
        ui.listItem("li-1", rx + 12, ly, 300, 40, "Inbox", "3 unread messages");
        ui.listItem("li-2", rx + 12, ly + 40, 300, 40, "Archive", "Last week");
        ui.listItem("li-3", rx + 12, ly + 80, 300, 40, "Spam", "Empty");

        ui.statusPill(rx, ly + 136, 26, "Online", Rgba.hex(0x4CAF50));
        ui.badge(rx + 280, ly + 8, 3);

        Ui.Interaction fab = ui.fab("fab-add", rx + 260, ly + 130, 52);
        ui.textCentered(Ui.Font.HEADLINE, "+", rx + 286, ly + 156, Theme.ON_PRIMARY_CONTAINER);
        if (fab.hovered) {
            ui.setLayer(2);
            ui.tooltip(rx + 200, ly + 110, "Create new");
            ui.setLayer(0);
        }

        ui.glowOrb(rx + 340, ry + 200, 48f, Theme.PRIMARY.alpha(0.12f));

        ui.flush();
        ui.endFrame();
    }

    public void dispose() {
        ui.close();
    }
}