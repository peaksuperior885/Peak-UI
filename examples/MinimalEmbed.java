/**
 * Minimal example of embedding Peak UI in an existing LWJGL + GLFW app.
 *
 * Not compiled as part of the library — copy the pattern into your game loop.
 *
 * Prerequisites: OpenGL 2.1 context already current, LWJGL natives on the classpath.
 *
 * Demonstrates Compose-style components: filled/tonal/outlined/text buttons,
 * elevated + outlined cards, glass surfaces (with softness), divider, and chips.
 */
package examples;

import com.peak885.ui.Rgba;
import com.peak885.ui.Theme;
import com.peak885.ui.Ui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

public final class MinimalEmbed {

    private final Ui ui = new Ui(1280, 720);

    private boolean buttonWasClicked;
    private boolean glassClicked;
    private boolean chipOn;

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

    /**
     * Call once per frame after clearing the color buffer.
     *
     * @param fbW framebuffer width
     * @param fbH framebuffer height
     * @param mouseX mouse X in framebuffer pixels
     * @param mouseY mouse Y in framebuffer pixels
     * @param timeSeconds glfwGetTime() or equivalent
     */
    public void frame(int fbW, int fbH, float mouseX, float mouseY, double timeSeconds) {
        ui.beginFrame(fbW, fbH, mouseX, mouseY, timeSeconds);

        // Background
        ui.rect(0, 0, ui.width, ui.height, Theme.SURFACE, 0f);

        // Soft decorative blobs so glass panels can show transparency / edge glow
        ui.rect(200, 50, 260, 190, Theme.PRIMARY_CONTAINER.alpha(0.55f), 48f, 0f, null, 30f);
        ui.rect(500, 140, 220, 160, Theme.accent(2).alpha(0.42f), 40f, 0f, null, 26f);
        ui.rect(60, 380, 180, 130, Theme.accent(1).alpha(0.38f), 36f, 0f, null, 22f);

        // Header
        ui.text(Ui.Font.HEADLINE, "Peak UI", 32, 44, Theme.ON_SURFACE);
        ui.text(Ui.Font.BODY, "Compose-style components · glass & soft edges",
                32, 72, Theme.ON_SURFACE_VARIANT);

        // ----- Buttons: Filled / Tonal / Outlined / Text -----
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

        // ----- Cards -----
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

        // ----- Glass panels -----
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

        // Static glass with stronger softness over the decorative blobs
        ui.glass(32 + gw + 16, gy, 200, gh, 24f,
                Theme.GLASS_LIGHT, Theme.GLASS_BORDER_SUBTLE, 16f);
        ui.textCentered(Ui.Font.BODY, "Soft glass", 32 + gw + 16 + 100, gy + gh * 0.5f, Theme.ON_SURFACE);

        // ----- Divider + chips -----
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

        // Low-level rect with explicit border + softness (same building blocks as glass)
        ui.rect(250, chipY - 4, 160, 40, Theme.GLASS, 12f, 1.2f, Theme.GLASS_BORDER, 10f);
        ui.textCentered(Ui.Font.LABEL, "rect + softness", 250 + 80, chipY + 16, Theme.ON_SURFACE);

        ui.flush();
        ui.endFrame();
    }

    public void dispose() {
        ui.close();
    }
}