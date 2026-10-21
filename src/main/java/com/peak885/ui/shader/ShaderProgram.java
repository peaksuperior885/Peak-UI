package com.peak885.ui.shader;

import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

public final class ShaderProgram implements AutoCloseable {
    private final int programId;

    public ShaderProgram(String vertexPath, String fragmentPath) {
        this(vertexPath, fragmentPath, null, -1, null, -1, true);
    }

    public ShaderProgram(String vertexPath, String fragmentPath,
                         String attribute0, int location0,
                         String attribute1, int location1) {
        this(vertexPath, fragmentPath, attribute0, location0, attribute1, location1, true);
    }

    private ShaderProgram(String vertexPath, String fragmentPath,
                          String attribute0, int location0,
                          String attribute1, int location1, boolean ignored) {
        int vertex = compileShader(GL_VERTEX_SHADER, Glsl.loadShaderSource(vertexPath));
        int fragment = 0, program = 0;
        try {
            fragment = compileShader(GL_FRAGMENT_SHADER, Glsl.loadShaderSource(fragmentPath));
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            if (attribute0 != null) glBindAttribLocation(program, location0, attribute0);
            if (attribute1 != null) glBindAttribLocation(program, location1, attribute1);
            glLinkProgram(program);
            IntBuffer status = BufferUtils.createIntBuffer(1);
            glGetProgramiv(program, GL_LINK_STATUS, status);
            if (status.get(0) == GL_FALSE) {
                throw new IllegalStateException("Shader program linking failed:\n" + glGetProgramInfoLog(program));
            }
            programId = program;
        } catch (RuntimeException | Error e) {
            if (program != 0) glDeleteProgram(program);
            throw e;
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        IntBuffer status = BufferUtils.createIntBuffer(1);
        glGetShaderiv(shader, GL_COMPILE_STATUS, status);
        if (status.get(0) == GL_FALSE) {
            String name = type == GL_VERTEX_SHADER ? "Vertex" : "Fragment";
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(name + " shader compilation failed:\n" + log);
        }
        return shader;
    }

    public void bind() { glUseProgram(programId); }
    public void unbind() { glUseProgram(0); }
    public int getUniformLocation(String name) { return glGetUniformLocation(programId, name); }
    public int getProgramId() { return programId; }
    @Override public void close() { glDeleteProgram(programId); }
}
