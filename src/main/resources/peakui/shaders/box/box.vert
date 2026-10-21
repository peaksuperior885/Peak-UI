#version 120

attribute vec2 aPosition;
attribute vec2 aLocalPosition;
uniform mat4 uProjection;
varying vec2 vLocalPosition;

void main() {
    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
    vLocalPosition = aLocalPosition;
}
