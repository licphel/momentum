Texture2D u_tex : register(t0);
SamplerState u_tex_sampler : register(s0);

struct PSInput {
    [[vk::location(0)]] float4 color : COLOR0;
    [[vk::location(1)]] float2 uv    : TEXCOORD0;
};

struct PSOutput {
    [[vk::location(0)]] float4 f : SV_TARGET0;
};

PSOutput main(PSInput input) {
    PSOutput output;
    output.f = input.color * u_tex.Sample(u_tex_sampler, input.uv);
    return output;
}
