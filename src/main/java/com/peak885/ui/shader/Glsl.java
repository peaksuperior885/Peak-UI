package com.peak885.ui.shader;

public final class Glsl {

    private Glsl() {
    }

    public static String loadShaderSource(String path) {
                String source = Preprocessing.preprocess(path);
                return source;
    }
}
