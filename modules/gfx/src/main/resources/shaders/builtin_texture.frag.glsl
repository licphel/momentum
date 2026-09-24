#version 330 core
#extension GL_ARB_shading_language_420pack : require

layout(binding = 0) uniform sampler2D u_tex;

in vec4 vertex_color;
in vec2 vertex_uv;

layout(location = 0) out vec4 fragment_color;

void main() {
    fragment_color = vertex_color * texture(u_tex, vertex_uv);
}
