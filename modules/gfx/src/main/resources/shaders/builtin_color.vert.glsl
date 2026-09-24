#version 330 core
#extension GL_ARB_shading_language_420pack : require

layout(std140, binding = 0) uniform T {
    vec4 u_vp[4];
};

layout(location = 0) in vec3 in_pos;
layout(location = 1) in vec4 in_color;

out vec4 vertex_color;

void main() {
    vec4 position = vec4(in_pos, 1.0);
    vertex_color = in_color;
    gl_Position = u_vp[0] * position.x
        + u_vp[1] * position.y
        + u_vp[2] * position.z
        + u_vp[3] * position.w;
}
