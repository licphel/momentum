#version 330 core
#extension GL_ARB_shading_language_420pack : require

layout(binding = 0) uniform sampler2D u_tex;

layout(std140, binding = 1) uniform B {
    vec2 u_texel;
    vec2 u_padding;
};

in vec4 vertex_color;
in vec2 vertex_uv;

layout(location = 0) out vec4 fragment_color;

void main() {
    vec2 x = vec2(u_texel.x, 0.0) * 2.5;
    vec2 y = vec2(0.0, u_texel.y) * 2.5;
    vec4 blurred = texture(u_tex, vertex_uv) * 0.25;
    blurred += texture(u_tex, vertex_uv + x) * 0.125;
    blurred += texture(u_tex, vertex_uv - x) * 0.125;
    blurred += texture(u_tex, vertex_uv + y) * 0.125;
    blurred += texture(u_tex, vertex_uv - y) * 0.125;
    blurred += texture(u_tex, vertex_uv + x + y) * 0.0625;
    blurred += texture(u_tex, vertex_uv + x - y) * 0.0625;
    blurred += texture(u_tex, vertex_uv - x + y) * 0.0625;
    blurred += texture(u_tex, vertex_uv - x - y) * 0.0625;
    fragment_color = vertex_color * blurred;
}
