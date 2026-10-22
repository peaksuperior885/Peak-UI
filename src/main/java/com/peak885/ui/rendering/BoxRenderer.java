package com.peak885.ui.rendering;

import com.peak885.ui.shader.ShaderProgram;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class BoxRenderer implements AutoCloseable {
    private static final int POSITION_ATTRIBUTE = 0;
    private static final int LOCAL_ATTRIBUTE = 1;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int VERTEX_COUNT = 6;

    private final ShaderProgram shader;
    private final int projectionLocation, colorLocation, borderColorLocation;
    private final int boxSizeLocation, radiusLocation, borderWidthLocation, softnessLocation;
    private final int rippleCenterLocation, rippleRadiusLocation, rippleOpacityLocation;
    private final int vbo;
    private final Matrix4f projection = new Matrix4f();
    private final float[] vertices = new float[VERTEX_COUNT * FLOATS_PER_VERTEX];
    private int width, height;
    private boolean inBegin;
    private boolean closed;
    private GlState saved;

    public BoxRenderer(int width, int height) {
        requireSize(width, height);
        this.width = width;
        this.height = height;
        shader = new ShaderProgram("/peakui/shaders/box/box.vert",
                "/peakui/shaders/box/box.frag", "aPosition", POSITION_ATTRIBUTE,
                "aLocalPosition", LOCAL_ATTRIBUTE);
        projectionLocation = shader.getUniformLocation("uProjection");
        colorLocation = shader.getUniformLocation("uColor");
        borderColorLocation = shader.getUniformLocation("uBorderColor");
        boxSizeLocation = shader.getUniformLocation("uBoxSize");
        radiusLocation = shader.getUniformLocation("uRadius");
        borderWidthLocation = shader.getUniformLocation("uBorderWidth");
        softnessLocation = shader.getUniformLocation("uSoftness");
        rippleCenterLocation = shader.getUniformLocation("uRippleCenter");
        rippleRadiusLocation = shader.getUniformLocation("uRippleRadius");
        rippleOpacityLocation = shader.getUniformLocation("uRippleOpacity");
        vbo = glGenBuffers();
        if (vbo == 0) {
            shader.close();
            throw new IllegalStateException("Could not create box VBO");
        }
        updateProjection();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        updateProjection();
    }

    private void updateProjection() {
        projection.identity().ortho(0f, width, height, 0f, -1f, 1f);
    }

    public void begin() {
        ensureOpen();
        if (inBegin) throw new IllegalStateException("BoxRenderer.begin() called twice");
        saved = new GlState();
        inBegin = true;
        try {
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA,
                    GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            shader.bind();
            try (MemoryStack stack = stackPush()) {
                FloatBuffer matrix = stack.mallocFloat(16);
                projection.get(matrix);
                glUniformMatrix4fv(projectionLocation, false, matrix);
            }
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glEnableVertexAttribArray(POSITION_ATTRIBUTE);
            glEnableVertexAttribArray(LOCAL_ATTRIBUTE);
            glVertexAttribPointer(POSITION_ATTRIBUTE, 2, GL_FLOAT, false,
                    FLOATS_PER_VERTEX * Float.BYTES, 0L);
            glVertexAttribPointer(LOCAL_ATTRIBUTE, 2, GL_FLOAT, false,
                    FLOATS_PER_VERTEX * Float.BYTES, 2L * Float.BYTES);
        } catch (RuntimeException | Error e) {
            end();
            throw e;
        }
    }

    public void drawBox(float x, float y, float width, float height,
                        float red, float green, float blue, float alpha) {
        drawBox(x, y, width, height, red, green, blue, alpha, 0f,
                0f, 0f, 0f, 0f, 0f, 0f);
    }

    public void drawBox(float x, float y, float width, float height,
                        float red, float green, float blue, float alpha, float radius) {
        drawBox(x, y, width, height, red, green, blue, alpha, radius,
                0f, 0f, 0f, 0f, 0f, 0f);
    }

    public void drawBox(float x, float y, float width, float height,
                        float red, float green, float blue, float alpha, float radius,
                        float borderWidth, float br, float bg, float bb, float ba,
                        float softness) {
        drawBox(x, y, width, height, red, green, blue, alpha, radius,
                borderWidth, br, bg, bb, ba, softness,
                -1f, -1f, 0f, 0f);
    }

    public void drawRippleBox(float x, float y, float width, float height,
                              float red, float green, float blue, float alpha,
                              float radius, float borderWidth,
                              float br, float bg, float bb, float ba, float softness,
                              float rippleX, float rippleY,
                              float rippleProgress, float rippleOpacity) {
        float reach = (float) Math.hypot(width, height);
        drawBox(x, y, width, height, red, green, blue, alpha, radius,
                borderWidth, br, bg, bb, ba, softness,
                rippleX - width * .5f, rippleY - height * .5f,
                Math.max(0f, rippleProgress) * reach,
                clamp(rippleOpacity));
    }

    private void drawBox(float x, float y, float w, float h, float r, float g, float b, float a,
                         float radius, float borderWidth, float br, float bg, float bb, float ba,
                         float softness,
                         float rippleX, float rippleY, float rippleRadius,
                         float rippleOpacity) {
        ensureOpen();
        if (!inBegin) throw new IllegalStateException("drawBox requires begin()/end()");
        if (w <= 0f || h <= 0f) return;

        float pad = Math.max(0f, softness) + 1f;
        float x0 = x - pad;
        float y0 = y - pad;
        float x1 = x + w + pad;
        float y1 = y + h + pad;
        float hx = w * .5f;
        float hy = h * .5f;
        float lx0 = -hx - pad;
        float ly0 = -hy - pad;
        float lx1 = hx + pad;
        float ly1 = hy + pad;

        putVertex(0, x0, y0, lx0, ly0);
        putVertex(1, x1, y1, lx1, ly1);
        putVertex(2, x1, y0, lx1, ly0);
        putVertex(3, x0, y0, lx0, ly0);
        putVertex(4, x0, y1, lx0, ly1);
        putVertex(5, x1, y1, lx1, ly1);

        float maxRadius = Math.min(w, h) * .5f;
        glUniform4f(colorLocation, clamp(r), clamp(g), clamp(b), clamp(a));
        glUniform4f(borderColorLocation, clamp(br), clamp(bg), clamp(bb), clamp(ba));
        glUniform2f(boxSizeLocation, w, h);
        glUniform1f(radiusLocation, Math.max(0f, Math.min(radius, maxRadius)));
        glUniform1f(borderWidthLocation, Math.max(0f, borderWidth));
        glUniform1f(softnessLocation, Math.max(0f, softness));
        glUniform2f(rippleCenterLocation, rippleX, rippleY);
        glUniform1f(rippleRadiusLocation, rippleRadius);
        glUniform1f(rippleOpacityLocation, clamp(rippleOpacity));
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, VERTEX_COUNT);
    }

    private void putVertex(int index, float x, float y, float lx, float ly) {
        int i = index * FLOATS_PER_VERTEX;
        vertices[i] = x;
        vertices[i + 1] = y;
        vertices[i + 2] = lx;
        vertices[i + 3] = ly;
    }

    public void end() {
        if (!inBegin) return;
        inBegin = false;
        GlState restore = saved;
        saved = null;
        if (restore != null) restore.restore();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void requireSize(int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Dimensions must be positive");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Renderer is closed");
    }

    @Override
    public void close() {
        if (closed) return;
        end();
        glDeleteBuffers(vbo);
        shader.close();
        closed = true;
    }

    private static final class GlState {
        final int program = glGetInteger(GL_CURRENT_PROGRAM);
        final int arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final boolean depth = glIsEnabled(GL_DEPTH_TEST);
        final boolean blend = glIsEnabled(GL_BLEND);
        final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        final int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        final AttributeState position = new AttributeState(POSITION_ATTRIBUTE);
        final AttributeState local = new AttributeState(LOCAL_ATTRIBUTE);

        void restore() {
            position.restore();
            local.restore();
            glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
            glUseProgram(program);
            glBlendEquationSeparate(eqRgb, eqAlpha);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            setEnabled(GL_BLEND, blend);
            setEnabled(GL_DEPTH_TEST, depth);
        }

        private static void setEnabled(int capability, boolean enabled) {
            if (enabled) glEnable(capability);
            else glDisable(capability);
        }
    }

    private static final class AttributeState {
        final int index, enabled, size, type, normalized, stride, buffer;
        final long pointer;

        AttributeState(int index) {
            this.index = index;
            enabled = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_ENABLED);
            size = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_SIZE);
            type = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_TYPE);
            normalized = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_NORMALIZED);
            stride = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_STRIDE);
            buffer = glGetVertexAttribi(index, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING);
            pointer = glGetVertexAttribPointer(index, GL_VERTEX_ATTRIB_ARRAY_POINTER);
        }

        void restore() {
            glBindBuffer(GL_ARRAY_BUFFER, buffer);
            glVertexAttribPointer(index, size, type, normalized != 0, stride, pointer);
            if (enabled != 0) glEnableVertexAttribArray(index); else glDisableVertexAttribArray(index);
        }
    }
}
