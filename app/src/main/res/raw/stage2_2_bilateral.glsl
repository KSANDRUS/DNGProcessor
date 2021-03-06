#version 300 es

// Bilateral filter
precision mediump float;

// Use buf to blur luma while keeping chroma.
uniform sampler2D buf;

uniform vec2 sigma;
uniform ivec2 radius;
uniform ivec2 bufSize;

// Out
out vec3 result;

float unscaledGaussian(float d, float s) {
    return exp(-0.5f * pow(d / s, 2.f));
}

// Difference
float fr(float diffi) {
    //return unscaledGaussian(diffi, 0.06f);
    return unscaledGaussian(diffi, sigma.x);
}

// Distance
float gs(float diffx) {
    //return 1.f / (diffx * diffx + 1.f);
    return unscaledGaussian(diffx, sigma.y);
}

float pixDiff(vec3 pix1, vec3 pix2) {
    // 2.f is z bias
    return length((pix2 - pix1) * vec3(1.f, 1.f, 2.f));
}

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

    vec3 XYZCenter = texelFetch(buf, xyCenter, 0).xyz;

    ivec2 minxy = max(ivec2(0, 0), xyCenter - radius.x);
    ivec2 maxxy = min(bufSize - 1, xyCenter + radius.x);

    vec3 I = vec3(0.f);
    float W = 0.f;

    for (int y = minxy.y; y <= maxxy.y; y += radius.y) {
        for (int x = minxy.x; x <= maxxy.x; x += radius.y) {
            ivec2 xyPixel = ivec2(x, y);

            vec3 XYZPixel = texelFetch(buf, xyPixel, 0).xyz;

            vec2 dxy = vec2(xyPixel - xyCenter);

            float scale = fr(pixDiff(XYZPixel, XYZCenter)) * gs(length(dxy));
            I += XYZPixel * scale;
            W += scale;
        }
    }

    result = I / W;
}
