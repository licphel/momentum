cbuffer T : register(b0) {
    float4 u_vp[4];
};

struct VSInput {
    [[vk::location(0)]] float2 quadPos : TEXCOORD0;
    [[vk::location(1)]] float2 quadUv  : TEXCOORD1;
    [[vk::location(2)]] float2 pos     : TEXCOORD2;
    [[vk::location(3)]] float2 sz      : TEXCOORD3;
    [[vk::location(4)]] float4 color   : TEXCOORD4;
    [[vk::location(5)]] float  rotation : TEXCOORD5;
    [[vk::location(6)]] float4 texUv   : TEXCOORD6;
};

struct VSOutput {
    [[vk::location(0)]] float4 color : COLOR0;
    [[vk::location(1)]] float2 uv    : TEXCOORD0;
    float4 pos : SV_POSITION;
};

VSOutput main(VSInput input) {
    VSOutput output;
    float2 centered = (input.quadPos - 0.5) * input.sz;
    float c, s;
    sincos(input.rotation, s, c);
    float2 rotated = float2(centered.x * c - centered.y * s,
                             centered.x * s + centered.y * c);
    float4 world = float4(rotated + input.pos, 0.0, 1.0);
    output.color = input.color;
    output.uv = input.texUv.xy + input.quadUv * input.texUv.zw;
    output.pos = u_vp[0] * world.x + u_vp[1] * world.y + u_vp[2] * world.z + u_vp[3] * world.w;
    return output;
}
