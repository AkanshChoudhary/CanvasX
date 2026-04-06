package com.my_app.art_collab.engine


object AgslShaders {
    val MULTIPLY_BLEND = """
        uniform shader base;
        uniform shader blend;
        uniform float opacity;
        half4 main(float2 coord){
            half4 baseColor = base.eval(coord);
            half4 blendColor = blend.eval(coord);
            half4 result = baseColor * blendColor;
            return mix(baseColor, result, blendColor.a * opacity);
        }
    """.trimIndent()

    val SCREEN_BLEND = """
          uniform shader base;
          uniform shader blend;
          uniform float opacity;
          half4 main(float2 coord) {
              half4 b = base.eval(coord);
              half4 s = blend.eval(coord);
              half4 result = half4(1.0) - (half4(1.0) - b) * (half4(1.0) - s);
              result.a = b.a;
              return mix(b, result, s.a * opacity);
          }
      """.trimIndent()
    val OVERLAY_BLEND = """
          uniform shader base;
          uniform shader blend;
          uniform float opacity;
          half4 main(float2 coord) {
              half4 b = base.eval(coord);
              half4 s = blend.eval(coord);
              half3 result;
              result.r = b.r < 0.5 ? 2.0*b.r*s.r : 1.0 - 2.0*(1.0-b.r)*(1.0-s.r);
              result.g = b.g < 0.5 ? 2.0*b.g*s.g : 1.0 - 2.0*(1.0-b.g)*(1.0-s.g);
              result.b = b.b < 0.5 ? 2.0*b.b*s.b : 1.0 - 2.0*(1.0-b.b)*(1.0-s.b);
              return mix(b, half4(result, b.a), s.a * opacity);
          }
      """.trimIndent()
    val SOFT_LIGHT_BLEND = """
          uniform shader base;
          uniform shader blend;
          uniform float opacity;
          half4 main(float2 coord) {
              half4 b = base.eval(coord);
              half4 s = blend.eval(coord);
              half3 result;
              result.r = s.r < 0.5
                  ? b.r - (1.0-2.0*s.r)*b.r*(1.0-b.r)
                  : b.r + (2.0*s.r-1.0)*(sqrt(b.r)-b.r);
              result.g = s.g < 0.5
                  ? b.g - (1.0-2.0*s.g)*b.g*(1.0-b.g)
                  : b.g + (2.0*s.g-1.0)*(sqrt(b.g)-b.g);
              result.b = s.b < 0.5
                  ? b.b - (1.0-2.0*s.b)*b.b*(1.0-b.b)
                  : b.b + (2.0*s.b-1.0)*(sqrt(b.b)-b.b);
              return mix(b, half4(result, b.a), s.a * opacity);
          }
      """.trimIndent()

    val BRIGHTNESS_CONTRAST = """
        uniform shader source;
        uniform float brightness;
        uniform float contrast;
        half4 main(float2 coord) {
            half4 color = source.eval(coord);
            half3 rgb = color.rgb + half3(brightness);
            float contrastFactor = (1.0 + contrast);
            rgb = (rgb - 0.5) * contrastFactor + 0.5;
            rgb = clamp(rgb, 0.0, 1.0);
            return half4(rgb, color.a);
        }
    """.trimIndent()
    val EXPOSURE = """
          uniform shader source;
          uniform float stops;
          half4 main(float2 coord) {
              half4 color = source.eval(coord);
              float factor = pow(2.0, stops);
              half3 rgb = clamp(color.rgb * factor, 0.0, 1.0);
              return half4(rgb, color.a);
          }
      """.trimIndent()
    /**
     * RuntimeShader / AGSL requires for-loop bounds to be constant. Kernel radius is applied
     * by masking taps outside |i|<=r (r clamped to max below).
     */
    private const val BLUR_KERNEL_MAX_RADIUS = 64

    val GAUSSIAN_BLUR_HORIZONTAL = """
          uniform shader source;
          uniform float radius;
          uniform float imageWidth;
          half4 main(float2 coord) {
              int r = int(clamp(radius, 0.0, float($BLUR_KERNEL_MAX_RADIUS)));
              if (r == 0) return source.eval(coord);
              half4 sum = half4(0.0);
              float weightSum = 0.0;
              float sigma = max(float(r) / 3.0, 0.001);
              for (int i = -$BLUR_KERNEL_MAX_RADIUS; i <= $BLUR_KERNEL_MAX_RADIUS; i++) {
                  float fi = float(i);
                  float inKernel = (fi >= -float(r) && fi <= float(r)) ? 1.0 : 0.0;
                  float weight = inKernel * exp(-fi * fi / (2.0 * sigma * sigma));
                  sum += source.eval(float2(coord.x + fi, coord.y)) * weight;
                  weightSum += weight;
              }
              return weightSum > 0.0 ? sum / weightSum : source.eval(coord);
          }
      """.trimIndent()
    val GAUSSIAN_BLUR_VERTICAL = """
          uniform shader source;
          uniform float radius;
          uniform float imageHeight;
          half4 main(float2 coord) {
              int r = int(clamp(radius, 0.0, float($BLUR_KERNEL_MAX_RADIUS)));
              if (r == 0) return source.eval(coord);
              half4 sum = half4(0.0);
              float weightSum = 0.0;
              float sigma = max(float(r) / 3.0, 0.001);
              for (int i = -$BLUR_KERNEL_MAX_RADIUS; i <= $BLUR_KERNEL_MAX_RADIUS; i++) {
                  float fi = float(i);
                  float inKernel = (fi >= -float(r) && fi <= float(r)) ? 1.0 : 0.0;
                  float weight = inKernel * exp(-fi * fi / (2.0 * sigma * sigma));
                  sum += source.eval(float2(coord.x, coord.y + fi)) * weight;
                  weightSum += weight;
              }
              return weightSum > 0.0 ? sum / weightSum : source.eval(coord);
          }
      """.trimIndent()
    val SHARPEN = """
          uniform shader source;
          uniform float amount;
          uniform float2 resolution;
          half4 main(float2 coord) {
              half4 center = source.eval(coord);
              half4 blur = (
                  source.eval(coord + float2(-1, 0)) +
                  source.eval(coord + float2(1, 0)) +
                  source.eval(coord + float2(0, -1)) +
                  source.eval(coord + float2(0, 1))
              ) / 4.0;
              half4 sharpened = center + (center - blur) * amount;
              return clamp(sharpened, 0.0, 1.0);
          }
      """.trimIndent()
    val VIGNETTE = """
          uniform shader source;
          uniform float intensity;
          uniform float feather;
          uniform float2 resolution;
          half4 main(float2 coord) {
              half4 color = source.eval(coord);
              float2 uv = (coord / resolution) * 2.0 - 1.0;
              float dist = length(uv);
              float vignetteRadius = 1.0 - feather * 0.5;
              float vignette = smoothstep(vignetteRadius, vignetteRadius - feather * 0.5, dist);
              float darkening = 1.0 - intensity * (1.0 - vignette);
              return half4(color.rgb * darkening, color.a);
          }
      """.trimIndent()
    val SATURATION = """
          uniform shader source;
          uniform float amount;
          half4 main(float2 coord) {
              half4 color = source.eval(coord);
              float luminance = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
              half3 gray = half3(luminance);
              float satFactor = 1.0 + amount;
              half3 rgb = mix(gray, color.rgb, satFactor);
              rgb = clamp(rgb, 0.0, 1.0);
              return half4(rgb, color.a);
          }
      """.trimIndent()
    val COLOR_TEMPERATURE = """
          uniform shader source;
          uniform float temperature;
          uniform float tint;
          half4 main(float2 coord) {
              half4 color = source.eval(coord);
              float warmShift = temperature * 0.15;
              float tintShift = tint * 0.1;
              half3 rgb = color.rgb;
              rgb.r = clamp(rgb.r + warmShift + tintShift, 0.0, 1.0);
              rgb.g = clamp(rgb.g - tintShift * 0.5, 0.0, 1.0);
              rgb.b = clamp(rgb.b - warmShift + tintShift, 0.0, 1.0);
              return half4(rgb, color.a);
          }
      """.trimIndent()
    val GRAIN = """
          uniform shader source;
          uniform float amount;
          uniform float size;
          uniform float time;
          float rand(float2 co) {
              return fract(sin(dot(co, float2(12.9898, 78.233))) * 43758.5453);
          }
          half4 main(float2 coord) {
              half4 color = source.eval(coord);
              float grain = rand(floor(coord / size) + time) * 2.0 - 1.0;
              half3 noisy = color.rgb + half3(grain * amount);
              return half4(clamp(noisy, 0.0, 1.0), color.a);
          }
      """.trimIndent()
    val PIXELATE = """
          uniform shader source;
          uniform float blockSize;
          half4 main(float2 coord) {
              float2 snapped = floor(coord / blockSize) * blockSize + blockSize * 0.5;
              return source.eval(snapped);
          }
      """.trimIndent()

}