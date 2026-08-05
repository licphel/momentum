struct PSInput {
    [[vk::location(0)]] float4 color : COLOR0;
};

struct PSOutput {
    [[vk::location(0)]] float4 f : SV_TARGET0;
};

PSOutput main(PSInput input) {
    PSOutput output;
    output.f = input.color;
    return output;
}
