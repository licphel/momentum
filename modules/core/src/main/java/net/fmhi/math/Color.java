/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fmhi.math;

/**
 * Immutable float RGBA color.
 *
 * <p>Channel values are typically in {@code [0, 1]}, but are not clamped, allowing HDR usage.
 *
 * @param red   red channel (0 = none, 1 = full)
 * @param green green channel
 * @param blue  blue channel
 * @param alpha alpha channel (0 = transparent, 1 = opaque)
 */
public record Color(float red, float green, float blue, float alpha) {
  public static final Color EMPTY = new Color(0.0F, 0.0F, 0.0F, 0.0F);
  public static final Color WHITE = new Color(1.0F, 1.0F, 1.0F, 1.0F);
  public static final Color BLACK = new Color(0.0F, 0.0F, 0.0F, 1.0F);
  public static final Color RED = new Color(1.0F, 0.0F, 0.0F, 1.0F);
  public static final Color GREEN = new Color(0.0F, 1.0F, 0.0F, 1.0F);
  public static final Color BLUE = new Color(0.0F, 0.0F, 1.0F, 1.0F);

  /**
   * Creates a fully-opaque color.
   *
   * @param red   red channel
   * @param green green channel
   * @param blue  blue channel
   */
  public Color(float red, float green, float blue) {
    this(red, green, blue, 1.0F);
  }

  /**
   * Copies RGB from another color, overriding alpha.
   *
   * @param color source color
   * @param alpha new alpha value
   */
  public Color(Color color, float alpha) {
    this(color.red, color.green, color.blue, alpha);
  }

  /**
   * Creates a color from an ARGB hex string like {@code "#FF7744"} (opaque)
   * or {@code "#FF774488"} (with alpha).
   *
   * <p>Accepts {@code #RGB}, {@code #ARGB}, {@code #RRGGBB}, or
   * {@code #AARRGGBB} forms. The leading {@code #} is optional.
   *
   * @param hex the hex color string
   * @return the parsed color
   * @throws IllegalArgumentException if the string cannot be parsed
   */
  public static Color createHex(String hex) {
    String s = hex.startsWith("#") ? hex.substring(1) : hex;
    int v = Integer.parseUnsignedInt(s, 16);
    return switch (s.length()) {
      case 3 -> create((v >> 8 & 0xF) * 17, (v >> 4 & 0xF) * 17, (v & 0xF) * 17);
      case 4 -> create((v >> 8 & 0xF) * 17, (v >> 4 & 0xF) * 17, (v & 0xF) * 17, (v >> 12 & 0xF) * 17);
      case 6 -> create(v >> 16 & 0xFF, v >> 8 & 0xFF, v & 0xFF);
      case 8 -> create(v >> 16 & 0xFF, v >> 8 & 0xFF, v & 0xFF, v >> 24 & 0xFF);
      default -> throw new IllegalArgumentException("Invalid hex color: " + hex);
    };
  }

  /**
   * Creates a color from 0 to 255 byte channels.
   *
   * @param red   red channel (0–255)
   * @param green green channel (0–255)
   * @param blue  blue channel (0–255)
   * @param alpha alpha channel (0–255)
   * @return new color
   */
  public static Color create(int red, int green, int blue, int alpha) {
    return new Color(red / 255.0F, green / 255.0F, blue / 255.0F, alpha / 255.0F);
  }

  /**
   * Creates an opaque color from 0 to 255 byte channels.
   *
   * @param red   red channel (0–255)
   * @param green green channel (0–255)
   * @param blue  blue channel (0–255)
   * @return new color
   */
  public static Color create(int red, int green, int blue) {
    return create(red, green, blue, 255);
  }

  /**
   * Creates RGBA color from HSVA color.
   *
   * @param hue        hue in {@code [0, 360)}
   * @param saturation saturation in {@code [0, 1]}
   * @param value      value in {@code [0, 1]}
   * @param alpha      alpha in {@code [0, 1]}
   * @return RGB color
   */
  public static Color createHsv(float hue, float saturation, float value, float alpha) {
    if (saturation == 0.0F) {
      return new Color(value, value, value, alpha);
    }
    float h = hue * 6;
    int i = (int) h;
    float f = h - i;
    float p = value * (1.0F - saturation);
    float q = value * (1.0F - saturation * f);
    float t = value * (1.0F - saturation * (1.0F - f));
    return switch (i) {
      case 0 -> new Color(value, t, p, alpha);
      case 1 -> new Color(q, value, p, alpha);
      case 2 -> new Color(p, value, t, alpha);
      case 3 -> new Color(p, q, value, alpha);
      case 4 -> new Color(t, p, value, alpha);
      default -> new Color(value, p, q, alpha);
    };
  }

  /**
   * Creates RGBA color from HSV color.
   *
   * @param hue        hue in {@code [0, 1)}
   * @param saturation saturation in {@code [0, 1]}
   * @param value      value in {@code [0, 1]}
   * @return opaque RGB color
   */
  public static Color createHsv(float hue, float saturation, float value) {
    return createHsv(hue, saturation, value, 1.0F);
  }

  /**
   * Linearly interpolates between two colors.
   *
   * @param a start color
   * @param b end color
   * @param t interpolation factor (0 = {@code a}, 1 = {@code b})
   * @return interpolated color
   */
  public static Color lerp(Color a, Color b, float t) {
    float it = 1.0F - t;
    return new Color(a.red * it + b.red * t, a.green * it + b.green * t, a.blue * it + b.blue * t,
        a.alpha * it + b.alpha * t);
  }

  /**
   * Pack RGBA as half-float ABGR without allocating a Color object.
   *
   * <p>Layout: {@code [R:15..0 | G:31..16 | B:47..32 | A:63..48]}
   *
   * @param r red channel
   * @param g green channel
   * @param b blue channel
   * @param a alpha channel
   * @return packed half-precision values
   */
  public static long packF16LE(float r, float g, float b, float a) {
    long hr = Float.floatToFloat16(r) & 0xFFFFL;
    long hg = Float.floatToFloat16(g) & 0xFFFFL;
    long hb = Float.floatToFloat16(b) & 0xFFFFL;
    long ha = Float.floatToFloat16(a) & 0xFFFFL;
    return hr | (hg << 16) | (hb << 32) | (ha << 48);
  }

  /**
   * Returns the sum of this color and another (component-wise).
   *
   * @param o the other color
   * @return this + o
   */
  public Color add(Color o) {
    return new Color(red + o.red, green + o.green, blue + o.blue, alpha + o.alpha);
  }

  /**
   * Returns the component-wise product of this color and another.
   *
   * @param o the other color
   * @return this * o
   */
  public Color multiply(Color o) {
    return new Color(red * o.red, green * o.green, blue * o.blue, alpha * o.alpha);
  }

  /**
   * Multiplies RGB channels by {@code v}; alpha is unchanged.
   *
   * @param v scaling factor
   * @return scaled color
   */
  public Color multiply(float v) {
    return new Color(red * v, green * v, blue * v, alpha);
  }

  /**
   * Returns the complement color (inverts RGB, preserves alpha).
   *
   * @return the complement color
   */
  public Color complement() {
    return new Color(1.0F - red, 1.0F - green, 1.0F - blue, alpha);
  }

  /**
   * Packs RGBA as four IEEE 754 float16 values into a {@code long}.
   *
   * <p>Layout: {@code [R:15..0 | G:31..16 | B:47..32 | A:63..48]}
   *
   * @return packed half-precision values
   */
  public long packF16LE() {
    return packF16LE(red, green, blue, alpha);
  }
}