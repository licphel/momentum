cbuffer T : register(b0) {
    float4 u_vp[4];
};

struct VSInput {
    [[vk::location(0)]] float3 pos   : POSITION;
    [[vk::location(1)]] float4 color : COLOR0;
    [[vk::location(2)]] float2 uv    : TEXCOORD0;
};

struct VSOutput {
    [[vk::location(0)]] float4 color : COLOR0;
    [[vk::location(1)]] float2 uv    : TEXCOORD0;
    float4 pos : SV_POSITION;
};

VSOutput main(VSInput input) {
    VSOutput output;
    float4 p = float4(input.pos, 1.0);
    output.color = input.color;
    output.uv = input.uv;
    output.pos = u_vp[0] * p.x + u_vp[1] * p.y + u_vp[2] * p.z + u_vp[3] * p.w;
    return output;
}
