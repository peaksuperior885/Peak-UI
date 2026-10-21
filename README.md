# Peak UI

A small **immediate-mode UI** toolkit for [LWJGL](https://www.lwjgl.org/) (OpenGL 2.1+).

Extracted from a Compose Multiplatform–inspired experiment: rounded boxes with SDF + material ripples, FreeType text (including color emoji), scissor clipping, hover/press interaction, and layered drawing — without pulling in a full retained UI framework.

You keep ownership of the GLFW window and the game/render loop. Peak UI only draws and reports interaction.

## Features

- **Immediate-mode API** — record boxes & text each frame, then `flush()`
- **Rounded rectangles** with anti-aliased SDF corners and Material-style press ripples
- **Text** via FreeType atlas (ASCII + on-demand glyphs, color emoji from Segoe UI Emoji)
- **Hit-testing / interaction** — hover, press, click keyed by string ids
- **Animation helpers** — exponential easing keyed by id
- **Scissor clipping** stack
- **Layers** for draw order
- **Material 3 dark theme** colors included (`Theme`) — optional

## Requirements

- Java 17+
- LWJGL 3.3.x with modules: `lwjgl`, `lwjgl-opengl`, `lwjgl-freetype`
- An existing OpenGL context (legacy 2.1 matrix stack is used for projection)

## Dependency

```gradle
implementation 'com.peak885:peak-ui:0.1.0'

// You must also pull LWJGL natives for your platform, e.g.:
def lwjglNatives = "natives-windows" // or natives-linux, natives-macos, …
runtimeOnly "org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives"
runtimeOnly "org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives"
runtimeOnly "org.lwjgl:lwjgl-freetype:$lwjglVersion:$lwjglNatives"
```

Or copy the `src/` tree into your project.

## Quick embed

```java
// Once, after GL.createCapabilities():
Ui ui = new Ui(framebufferWidth, framebufferHeight);

// Each frame in your loop:
ui.beginFrame(fbWidth, fbHeight, mouseX, mouseY, glfwGetTime());

// Feed input (from GLFW callbacks or polling):
// ui.mouseDown / mousePressed / mouseReleased
// ui.scrollY, ui.typed, ui.backspaces, ui.escapePressed

// Build UI:
ui.rect(0, 0, ui.width, ui.height, Theme.SURFACE, 0);
Ui.Interaction btn = ui.surface("my-btn", 40, 40, 160, 40, 12,
        Theme.PRIMARY_CONTAINER, Theme.ON_SURFACE);
ui.textCentered(Ui.Font.BODY, "Click me", 40 + 80, 40 + 20, Theme.ON_PRIMARY_CONTAINER);
if (btn.clicked) {
    // handle click
}

ui.flush();
ui.endFrame();

// Optional cursor:
int kind = ui.cursorKind(); // Ui.CURSOR_ARROW / HAND / IBEAM
```

### Input wiring (typical GLFW)

```java
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
    if (key == GLFW_KEY_BACKSPACE && (action == GLFW_PRESS || action == GLFW_REPEAT))
        ui.backspaces++;
    else if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
        ui.escapePressed = true;
});
```

Mouse position must be in **framebuffer pixels** (scale by `fbSize / windowSize` on HiDPI).

## Core API surface

| Class / method | Role |
|---|---|
| `Ui` | Frame lifecycle, input state, drawing commands, interaction |
| `Ui.beginFrame / flush / endFrame` | Per-frame contract |
| `Ui.rect / outlined / shadow / surface` | Boxes |
| `Ui.text / textLeft / textCentered / fit` | Text |
| `Ui.interact / hit / pushClip / popClip / setLayer / anim` | Interaction & layout helpers |
| `Box` | Mutable rounded rect + ripple state |
| `Rgba` | Immutable 0..1 color (`hex`, `mix`, `alpha`) |
| `Theme` | Material 3 dark palette |
| `BoxRenderer` / `TextRenderer` | Low-level draw (used internally) |

## Coordinate system

Origin is **top-left**, Y increases **downward**, units are **framebuffer pixels**. Same convention as most 2D UI toolkits.

## Licensing

Peak UI's original source code is licensed under **PGCL-1.0**. See the [`LICENSE`](LICENSE.txt) file for details.

Peak UI also includes the Roboto 3 font family, which is distributed under the **SIL Open Font License 1.1 (OFL)**.

For third-party licensing details, copyright information, and license links, see [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md). The original font license is also included in [`OFL.txt`](OFL.txt).