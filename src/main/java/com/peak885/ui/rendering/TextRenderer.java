package com.peak885.ui.rendering;

import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;

public final class TextRenderer implements AutoCloseable {

    private static final int FIRST_CHAR = 32;
    private static final int LAST_CHAR = 126;
    private static final int FALLBACK_CODEPOINT = '?';

    private static final int ATLAS_WIDTH = 1024;
    private static final int ATLAS_PADDING = 2;
    private static final int COLOR_ATLAS_INITIAL_HEIGHT = 512;

    private static final String FONT_RESOURCE = "/peakui/fonts/Roboto-Regular.ttf";

    private static final class Glyph {
        final int advance;
        final int bearingX;
        final int bearingY;
        final int width;
        final int height;
        final float u0, v0, u1, v1;
        final boolean color;

        Glyph(int advance, int bearingX, int bearingY,
              int width, int height,
              float u0, float v0, float u1, float v1,
              boolean color) {
            this.advance = advance;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.color = color;
        }
    }

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final int pixelSize;
    private final Path customFontPath;

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f().identity();

    private int grayTextureId;
    private int grayAtlasWidth;
    private int grayAtlasHeight;

    private int colorTextureId;
    private int colorAtlasWidth = ATLAS_WIDTH;
    private int colorAtlasHeight = COLOR_ATLAS_INITIAL_HEIGHT;
    private int colorAtlasX = ATLAS_PADDING;
    private int colorAtlasY = ATLAS_PADDING;
    private int colorAtlasRowHeight = 0;

    private long libraryAddress;
    private long faceAddress;
    private FT_Face face;
    private Path temporaryFont;

    private int screenWidth;
    private int screenHeight;
    private int lineHeight;
    private boolean destroyed;

    public TextRenderer(int screenWidth, int screenHeight, int pixelSize) {
        this(screenWidth, screenHeight, pixelSize, (Path) null);
    }

    public TextRenderer(int screenWidth, int screenHeight, int pixelSize, String customFontPath) {
        this(screenWidth, screenHeight, pixelSize, customFontPath != null ? Path.of(customFontPath) : null);
    }

    public TextRenderer(int screenWidth, int screenHeight, int pixelSize, Path customFontPath) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("Screen dimensions must be positive");
        }
        if (pixelSize <= 0) {
            throw new IllegalArgumentException("Font pixel size must be positive");
        }

        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.pixelSize = pixelSize;
        this.lineHeight = pixelSize;
        this.customFontPath = customFontPath;

        updateProjection();
        initFreeType();
        int previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousUnpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        try {
            this.grayTextureId = createGrayAtlas();
            this.colorTextureId = createEmptyColorAtlas();
        } finally {
            glPixelStorei(GL_UNPACK_ALIGNMENT, previousUnpackAlignment);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glActiveTexture(previousActiveTexture);
        }

    }

    public void resize(int width, int height) {
        ensureAlive();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Screen dimensions must be positive");
        }
        this.screenWidth = width;
        this.screenHeight = height;
        updateProjection();
    }

    private void updateProjection() {
        projection.setOrtho(0f, screenWidth, screenHeight, 0f, -1f, 1f);
    }

    public int getTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0;

        int currentWidth = 0;
        int maximumWidth = 0;

        Glyph space = glyphs.get((int) ' ');
        int tabWidth = space != null ? Math.max(1, space.advance * 4) : pixelSize * 2;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp == '\n') {
                maximumWidth = Math.max(maximumWidth, currentWidth);
                currentWidth = 0;
                continue;
            }
            if (cp == '\r') continue;
            if (cp == '\t') {
                currentWidth += tabWidth;
                continue;
            }

            Glyph glyph = findOrLoadGlyph(cp);
            if (glyph != null) {
                currentWidth += glyph.advance;
            }
        }
        return Math.max(maximumWidth, currentWidth);
    }

    public int getTextHeight(String text) {
        if (text == null || text.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\n') lines++;
        }
        return lines * lineHeight;
    }

    public int getLineHeight() {
        return lineHeight;
    }

    public void drawText(String text, int x, int y, float r, float g, float b, float a) {
        if (text == null || text.isEmpty()) return;
        ensureAlive();
        int previousMatrixMode = glGetInteger(GL_MATRIX_MODE);
        boolean attributesPushed = false, projectionPushed = false, modelViewPushed = false;
        try (MemoryStack stack = stackPush()) {
            glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_DEPTH_BUFFER_BIT
                    | GL_TEXTURE_BIT | GL_PIXEL_MODE_BIT);
            attributesPushed = true;
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            projectionPushed = true;
            FloatBuffer matrix = stack.mallocFloat(16);
            projection.get(matrix);
            glLoadMatrixf(matrix);

            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            modelViewPushed = true;
            FloatBuffer model = stack.mallocFloat(16);
            modelView.get(model);
            glLoadMatrixf(model);
            glEnable(GL_TEXTURE_2D);

            Glyph space = glyphs.get((int) ' ');
            int tabWidth = space != null ? Math.max(1, space.advance * 4) : pixelSize * 2;
            drawGlyphPass(text, x, y, tabWidth, grayTextureId, false, r, g, b, a);
            drawGlyphPass(text, x, y, tabWidth, colorTextureId, true, 1f, 1f, 1f, a);
        } finally {
            if (modelViewPushed) { glMatrixMode(GL_MODELVIEW); glPopMatrix(); }
            if (projectionPushed) { glMatrixMode(GL_PROJECTION); glPopMatrix(); }
            if (attributesPushed) glPopAttrib();
            glMatrixMode(previousMatrixMode);
        }
    }

    private void drawGlyphPass(String text, int x, int baseline, int tabWidth, int texture,
                               boolean colorGlyph, float r, float g, float b, float a) {
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
        glColor4f(r, g, b, a);
        glBegin(GL_QUADS);
        try {
            int penX = x, penY = baseline;
            for (int i = 0; i < text.length();) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                if (cp == '\n') { penX = x; penY += lineHeight; continue; }
                if (cp == '\r') continue;
                if (cp == '\t') { penX += tabWidth; continue; }
                Glyph glyph = findOrLoadGlyph(cp);
                if (glyph == null) continue;
                if (glyph.color == colorGlyph) emitQuad(glyph, penX, penY);
                penX += glyph.advance;
            }
        } finally {
            glEnd();
        }
    }

    private void emitQuad(Glyph glyph, int penX, int baselineY) {
        if (glyph.width <= 0 || glyph.height <= 0) return;
        float gx = penX + glyph.bearingX, gy = baselineY - glyph.bearingY;
        glTexCoord2f(glyph.u0, glyph.v0); glVertex2f(gx, gy);
        glTexCoord2f(glyph.u0, glyph.v1); glVertex2f(gx, gy + glyph.height);
        glTexCoord2f(glyph.u1, glyph.v1); glVertex2f(gx + glyph.width, gy + glyph.height);
        glTexCoord2f(glyph.u1, glyph.v0); glVertex2f(gx + glyph.width, gy);
    }
    private Glyph findOrLoadGlyph(int codepoint) {
        Glyph glyph = glyphs.get(codepoint);
        if (glyph != null) return glyph;

        glyph = loadGlyph(codepoint);
        if (glyph != null) {
            glyphs.put(codepoint, glyph);
            return glyph;
        }

        if (codepoint != FALLBACK_CODEPOINT) {
            return findOrLoadGlyph(FALLBACK_CODEPOINT);
        }
        return null;
    }

    private Glyph loadGlyph(int codepoint) {
        if (face == null) return null;

        int flags = FT_LOAD_RENDER | FT_LOAD_COLOR;
        if (FT_Load_Char(face, codepoint, flags) != 0) {
            if (FT_Load_Char(face, codepoint, FT_LOAD_RENDER) != 0) {
                return null;
            }
        }

        FT_GlyphSlot slot = face.glyph();
        FT_Bitmap bitmap = slot.bitmap();

        int width = bitmap.width();
        int height = bitmap.rows();
        int advance = (int) (slot.advance().x() >> 6);
        int bearingX = slot.bitmap_left();
        int bearingY = slot.bitmap_top();

        if (width <= 0 || height <= 0) {
            return new Glyph(advance, bearingX, bearingY, 0, 0, 0, 0, 0, 0, false);
        }

        int pixelMode = bitmap.pixel_mode();

        if (pixelMode == FT_PIXEL_MODE_BGRA) {
            return uploadColorGlyph(bitmap, width, height, advance, bearingX, bearingY);
        } else {
            byte[] pixels = copyGrayBitmap(bitmap, width, height);
            return uploadGrayGlyph(pixels, width, height, advance, bearingX, bearingY);
        }
    }

    private int createGrayAtlas() {
        for (int cp = FIRST_CHAR; cp <= LAST_CHAR; cp++) {
            if (FT_Load_Char(face, cp, FT_LOAD_RENDER) != 0) continue;
        }

        int x = ATLAS_PADDING;
        int y = ATLAS_PADDING;
        int rowHeight = 0;
        Map<Integer, int[]> positions = new HashMap<>();

        for (int cp = FIRST_CHAR; cp <= LAST_CHAR; cp++) {
            if (FT_Load_Char(face, cp, FT_LOAD_RENDER) != 0) continue;

            FT_GlyphSlot slot = face.glyph();
            FT_Bitmap bitmap = slot.bitmap();
            int w = bitmap.width();
            int h = bitmap.rows();

            if (w + ATLAS_PADDING * 2 > ATLAS_WIDTH) continue;

            if (x + w + ATLAS_PADDING > ATLAS_WIDTH) {
                x = ATLAS_PADDING;
                y += rowHeight + ATLAS_PADDING;
                rowHeight = 0;
            }

            positions.put(cp, new int[]{x, y, w, h});
            x += w + ATLAS_PADDING;
            rowHeight = Math.max(rowHeight, h);
        }

        grayAtlasWidth = ATLAS_WIDTH;
        grayAtlasHeight = Math.max(pixelSize + ATLAS_PADDING * 2, y + rowHeight + ATLAS_PADDING);

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                grayAtlasWidth, grayAtlasHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        for (int cp = FIRST_CHAR; cp <= LAST_CHAR; cp++) {
            int[] pos = positions.get(cp);
            if (pos == null) continue;

            if (FT_Load_Char(face, cp, FT_LOAD_RENDER) != 0) continue;
            FT_GlyphSlot slot = face.glyph();
            FT_Bitmap bitmap = slot.bitmap();
            int w = pos[2];
            int h = pos[3];
            byte[] pixels = copyGrayBitmap(bitmap, w, h);

            if (w > 0 && h > 0) {
                try (MemoryStack stack = stackPush()) {
                    ByteBuffer buf = stack.malloc(pixels.length * 4);
                    for (byte pixel : pixels) buf.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put(pixel);
                    buf.flip();
                    glTexSubImage2D(GL_TEXTURE_2D, 0, pos[0], pos[1], w, h,
                            GL_RGBA, GL_UNSIGNED_BYTE, buf);
                }
            }

            float u0 = (float) pos[0] / grayAtlasWidth;
            float v0 = (float) pos[1] / grayAtlasHeight;
            float u1 = (float) (pos[0] + w) / grayAtlasWidth;
            float v1 = (float) (pos[1] + h) / grayAtlasHeight;

            int advance = (int) (slot.advance().x() >> 6);
            glyphs.put(cp, new Glyph(advance, slot.bitmap_left(), slot.bitmap_top(),
                    w, h, u0, v0, u1, v1, false));
        }

        long metricsHeight = face.size().metrics().height() >> 6;
        if (metricsHeight > 0 && metricsHeight <= Integer.MAX_VALUE) {
            lineHeight = (int) metricsHeight;
        }

        return tex;
    }

    private Glyph uploadGrayGlyph(byte[] pixels, int width, int height,
                                  int advance, int bearingX, int bearingY) {
        return null;
    }

    private int createEmptyColorAtlas() {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                colorAtlasWidth, colorAtlasHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        return tex;
    }

    private Glyph uploadColorGlyph(FT_Bitmap bitmap, int width, int height,
                                   int advance, int bearingX, int bearingY) {

        if (width + ATLAS_PADDING * 2 > colorAtlasWidth) {
            return null;
        }

        if (colorAtlasX + width + ATLAS_PADDING > colorAtlasWidth) {
            colorAtlasX = ATLAS_PADDING;
            colorAtlasY += colorAtlasRowHeight + ATLAS_PADDING;
            colorAtlasRowHeight = 0;
        }

        if (colorAtlasY + height + ATLAS_PADDING > colorAtlasHeight) {
            int newHeight = colorAtlasHeight * 2;
            while (newHeight < colorAtlasY + height + ATLAS_PADDING) {
                newHeight *= 2;
            }
            growColorAtlas(newHeight);
        }

        int atlasX = colorAtlasX;
        int atlasY = colorAtlasY;

        int pitch = bitmap.pitch();
        int absPitch = Math.abs(pitch);
        ByteBuffer src = bitmap.buffer(absPitch * height);

        try (MemoryStack stack = stackPush()) {
            ByteBuffer dst = stack.malloc(width * height * 4);

            for (int row = 0; row < height; row++) {
                int srcRow = pitch >= 0 ? row : height - 1 - row;
                int srcOffset = srcRow * absPitch;

                for (int col = 0; col < width; col++) {
                    byte b = src.get(srcOffset + col * 4 + 0);
                    byte g = src.get(srcOffset + col * 4 + 1);
                    byte r = src.get(srcOffset + col * 4 + 2);
                    byte a = src.get(srcOffset + col * 4 + 3);

                    int dstIdx = (row * width + col) * 4;
                    dst.put(dstIdx + 0, b);
                    dst.put(dstIdx + 1, g);
                    dst.put(dstIdx + 2, r);
                    dst.put(dstIdx + 3, a);
                }
            }
            dst.position(0);

            glBindTexture(GL_TEXTURE_2D, colorTextureId);
            glTexSubImage2D(GL_TEXTURE_2D, 0, atlasX, atlasY, width, height,
                    GL_BGRA, GL_UNSIGNED_BYTE, dst);
        }

        colorAtlasX += width + ATLAS_PADDING;
        colorAtlasRowHeight = Math.max(colorAtlasRowHeight, height);

        float u0 = (float) atlasX / colorAtlasWidth;
        float v0 = (float) atlasY / colorAtlasHeight;
        float u1 = (float) (atlasX + width) / colorAtlasWidth;
        float v1 = (float) (atlasY + height) / colorAtlasHeight;

        return new Glyph(advance, bearingX, bearingY, width, height, u0, v0, u1, v1, true);
    }

    private void growColorAtlas(int newHeight) {
        int newTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, newTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                colorAtlasWidth, newHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, colorTextureId);

        glDeleteTextures(colorTextureId);
        colorTextureId = newTex;
        colorAtlasHeight = newHeight;
    }

    private void initFreeType() {
        try {
            Path fontToUse = null;

            if (customFontPath != null && Files.exists(customFontPath)) {
                fontToUse = customFontPath;
            } else {
                try (InputStream input = TextRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
                    if (input == null) {
                        throw new IllegalStateException("Missing font resource: " + FONT_RESOURCE);
                    }
                    temporaryFont = Files.createTempFile("peak-ui-font-", ".ttf");
                    Files.copy(input, temporaryFont, StandardCopyOption.REPLACE_EXISTING);
                    fontToUse = temporaryFont;
                }
            }

            try (MemoryStack stack = stackPush()) {
                PointerBuffer libraryPointer = stack.mallocPointer(1);
                if (FT_Init_FreeType(libraryPointer) != 0) {
                    throw new IllegalStateException("FT_Init_FreeType failed");
                }
                libraryAddress = libraryPointer.get(0);

                PointerBuffer facePointer = stack.mallocPointer(1);
                if (FT_New_Face(libraryAddress,
                        fontToUse.toAbsolutePath().toString(),
                        0, facePointer) != 0) {

                    if (customFontPath != null && fontToUse.equals(customFontPath)) {
                        try (InputStream input = TextRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
                            if (input != null) {
                                temporaryFont = Files.createTempFile("peak-ui-font-", ".ttf");
                                Files.copy(input, temporaryFont, StandardCopyOption.REPLACE_EXISTING);
                                fontToUse = temporaryFont;
                            }
                        }
                        if (FT_New_Face(libraryAddress, fontToUse.toAbsolutePath().toString(), 0, facePointer) != 0) {
                            throw new IllegalStateException("FT_New_Face failed for both user font and bundled fallback resource " + FONT_RESOURCE);
                        }
                    } else {
                        throw new IllegalStateException("FT_New_Face failed for " + fontToUse);
                    }
                }
                faceAddress = facePointer.get(0);
                face = FT_Face.create(faceAddress);

                if (FT_Set_Pixel_Sizes(face, 0, pixelSize) != 0) {
                    throw new IllegalStateException("FT_Set_Pixel_Sizes failed: " + pixelSize);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract or read font", e);
        }
    }

    private static byte[] copyGrayBitmap(FT_Bitmap bitmap, int width, int height) {
        if (width <= 0 || height <= 0) return new byte[0];

        int pixelMode = bitmap.pixel_mode();
        if (pixelMode != FT_PIXEL_MODE_GRAY && pixelMode != FT_PIXEL_MODE_MONO) {
            return new byte[width * height];
        }

        int pitch = bitmap.pitch();
        int absolutePitch = Math.abs(pitch);
        int minimumPitch = pixelMode == FT_PIXEL_MODE_MONO ? (width + 7) / 8 : width;

        if (absolutePitch < minimumPitch) {
            throw new IllegalStateException("FreeType bitmap pitch is smaller than its pixel row");
        }

        long requiredBytes = (long) absolutePitch * height;
        ByteBuffer source = bitmap.buffer((int) requiredBytes);
        byte[] pixels = new byte[width * height];
        int numGrays = bitmap.num_grays();

        for (int row = 0; row < height; row++) {
            int sourceRow = pitch >= 0 ? row : height - 1 - row;
            int rowOffset = sourceRow * absolutePitch;

            for (int column = 0; column < width; column++) {
                int value;
                if (pixelMode == FT_PIXEL_MODE_MONO) {
                    int packed = source.get(rowOffset + column / 8) & 0xFF;
                    value = (packed & (0x80 >> (column & 7))) != 0 ? 255 : 0;
                } else {
                    value = source.get(rowOffset + column) & 0xFF;
                    if (numGrays > 1 && numGrays != 256) {
                        value = Math.min(255, value * 255 / (numGrays - 1));
                    } else if (numGrays <= 1) {
                        value = value == 0 ? 0 : 255;
                    }
                }
                pixels[row * width + column] = (byte) value;
            }
        }
        return pixels;
    }

    private void ensureAlive() {
        if (destroyed) {
            throw new IllegalStateException("TextRenderer has already been destroyed");
        }
    }

    public void destroy() {
        if (destroyed) return;

if (grayTextureId != 0) {
            glDeleteTextures(grayTextureId);
            grayTextureId = 0;
        }
        if (colorTextureId != 0) {
            glDeleteTextures(colorTextureId);
            colorTextureId = 0;
        }

        if (face != null) {
            FT_Done_Face(face);
            face = null;
        }
        if (libraryAddress != 0L) {
            FT_Done_FreeType(libraryAddress);
            libraryAddress = 0L;
        }

        if (temporaryFont != null) {
            try {
                Files.deleteIfExists(temporaryFont);
            } catch (IOException ignored) {}
            temporaryFont = null;
        }

        glyphs.clear();
        destroyed = true;
    }

    @Override
    public void close() {
        destroy();
    }
}










