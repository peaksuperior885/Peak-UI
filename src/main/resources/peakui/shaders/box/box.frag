#version 120

#include "/peakui/shaders/common/ui_corners.glsl"

uniform vec4 uColor;
uniform vec4 uBorderColor;
uniform vec2 uBoxSize;
uniform float uRadius;
uniform float uBorderWidth;
uniform float uSoftness;       // outer soft edge / faux blur in pixels
uniform vec2 uRippleCenter;
uniform float uRippleRadius;
uniform float uRippleOpacity;
varying vec2 vLocalPosition;

void main() {
    vec2 halfSize = uBoxSize * 0.5;
    float sdf = roundedBoxSDF(vLocalPosition, halfSize, uRadius);

    // Soft outer edge (glass / glow). Larger softness = more blur-like falloff.
    float soft = max(uSoftness, 0.0);
    float outer = 1.0 - smoothstep(-soft, max(1.0, soft * 0.25), sdf);

    // Fill coverage (inner body)
    float fill = 1.0 - smoothstep(0.0, 1.0, sdf);

    // Border ring: between -borderWidth and 0 (inside the edge)
    float bw = max(uBorderWidth, 0.0);
    float border = 0.0;
    if (bw > 0.001) {
        float inner = 1.0 - smoothstep(-bw, -bw + 1.0, sdf);
        border = clamp(fill - inner, 0.0, 1.0);
        if (soft > 0.5) {
            border *= smoothstep(soft, soft * 0.2, abs(sdf));
        }
    }

    // Material ripple
    float d = distance(vLocalPosition, uRippleCenter);
    float ripple = (1.0 - smoothstep(uRippleRadius - 5.0,
                                     uRippleRadius + 5.0, d)) * uRippleOpacity;
    vec3 rippleTint = mix(uColor.rgb, vec3(1.0), 0.28);

    // Composite
    vec3 rgb = mix(uColor.rgb, rippleTint, ripple);
    float a = uColor.a * fill;

    // Soft halo outside the hard edge (faux blur / glass glow)
    if (soft > 0.01 && sdf > 0.0) {
        float halo = outer * (1.0 - fill);
        a = max(a, uColor.a * halo * 0.55);
        rgb = mix(rgb, vec3(1.0), halo * 0.08);
    }

    // Border on top
    if (border > 0.001) {
        rgb = mix(rgb, uBorderColor.rgb, border);
        a = max(a, uBorderColor.a * border);
    }

    gl_FragColor = vec4(rgb, a * outer);
}
