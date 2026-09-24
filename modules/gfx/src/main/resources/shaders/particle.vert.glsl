#version 330 core
#extension GL_ARB_shading_language_420pack : require

layout(std140, binding = 0) uniform T {
    vec4 u_vp[4];
};

layout(location = 0) in vec2 in_quad_pos;
layout(location = 1) in vec2 in_quad_uv;
layout(location = 2) in vec2 in_position;
layout(location = 3) in vec2 in_size;
layout(location = 4) in vec4 in_color;
layout(location = 5) in float in_rotation;
layout(location = 6) in vec4 in_texture_uv;

out vec4 vertex_color;
out vec2 vertex_uv;

void main() {
    vec2 centered = (in_quad_pos - 0.5) * in_size;
    float sine = sin(in_rotation);
    float cosine = cos(in_rotation);
    vec2 rotated = vec2(
        centered.x * cosine - centered.y * sine,
        centered.x * sine + centered.y * cosine
    );
    vec4 world = vec4(rotated + in_position, 0.0, 1.0);
    vertex_color = in_color;
    vertex_uv = in_texture_uv.xy + in_quad_uv * in_texture_uv.zw;
    gl_Position = u_vp[0] * world.x
        + u_vp[1] * world.y
        + u_vp[2] * world.z
        + u_vp[3] * world.w;
}
