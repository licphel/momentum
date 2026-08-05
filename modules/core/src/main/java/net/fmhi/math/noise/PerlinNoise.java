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

package net.fmhi.math.noise;

import net.fmhi.math.random.RandomGenerator;

/**
 * Classic Ken Perlin 3D noise.
 *
 * <p>Output is normalized to approximately {@code [0, 1]}.
 */
public final class PerlinNoise implements NoiseGenerator {
  private final int[] perm = new int[512];

  /**
   * Creates a perlin noise generator using the given rng for perms.
   *
   * @param rng perm-gen rng
   */
  public PerlinNoise(RandomGenerator rng) {
    int[] p = new int[256];
    for (int i = 0; i < 256; i++) {
      p[i] = i;
    }

    for (int i = 255; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      int tmp = p[i];
      p[i] = p[j];
      p[j] = tmp;
    }
    for (int i = 0; i < 256; i++) {
      perm[i] = perm[i + 256] = p[i];
    }
  }

  private static double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private static double lerp(double a, double b, double t) {
    return a + t * (b - a);
  }

  private static double grad(int hash, double x, double y, double z) {
    int h = hash & 15;
    double u = h < 8 ? x : y;
    double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }

  @Override
  public double generate(double x, double y, double z) {
    int xi = (int) Math.floor(x) & 255;
    int yi = (int) Math.floor(y) & 255;
    int zi = (int) Math.floor(z) & 255;

    double xf = x - Math.floor(x);
    double yf = y - Math.floor(y);
    double zf = z - Math.floor(z);

    double u = fade(xf);
    double v = fade(yf);
    double w = fade(zf);

    int aaa = perm[perm[perm[xi] + yi] + zi];
    int aba = perm[perm[perm[xi] + yi + 1] + zi];
    int aab = perm[perm[perm[xi] + yi] + zi + 1];
    int abb = perm[perm[perm[xi] + yi + 1] + zi + 1];
    int baa = perm[perm[perm[xi + 1] + yi] + zi];
    int bba = perm[perm[perm[xi + 1] + yi + 1] + zi];
    int bab = perm[perm[perm[xi + 1] + yi] + zi + 1];
    int bbb = perm[perm[perm[xi + 1] + yi + 1] + zi + 1];

    double x1 = lerp(grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf), u);
    double x2 = lerp(grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf), u);
    double y1 = lerp(x1, x2, v);

    double x3 = lerp(grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1), u);
    double x4 = lerp(grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1), u);
    double y2 = lerp(x3, x4, v);

    return (lerp(y1, y2, w) + 1.0) * 0.5;
  }
}
