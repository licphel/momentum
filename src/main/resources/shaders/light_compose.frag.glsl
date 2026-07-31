#version 330
#ifdef GL_ARB_shading_language_420pack
#extension GL_ARB_shading_language_420pack : require
#endif

in vec4 vColor;
in vec2 vTexCoord;

out vec4 fragColor;

layout(binding = 1) uniform sampler2D u_albedo;
layout(binding = 2) uniform sampler2D u_lightmap;

void main() {
    vec4 albedo = texture(u_albedo, vTexCoord);
    vec4 light = texture(u_lightmap, vTexCoord);
    fragColor = vColor * albedo * light;
}
