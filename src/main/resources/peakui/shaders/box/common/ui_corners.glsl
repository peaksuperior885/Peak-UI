#ifndef UI_CORNERS_GLSL
#define UI_CORNERS_GLSL

float roundedBoxSDF(
    vec2 position,
    vec2 halfSize,
    float radius
) {
    vec2 q = abs(position) - halfSize + radius;

    return length(max(q, 0.0))
    + min(max(q.x, q.y), 0.0)
    - radius;
}

float roundedBoxAlpha(
    vec2 position,
    vec2 halfSize,
    float radius
) {
    float distance = roundedBoxSDF(
        position,
        halfSize,
        radius
    );

    return 1.0 - smoothstep(
        0.0,
        1.0,
        distance
    );
}

#endif
