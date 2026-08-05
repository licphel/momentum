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
 * Voronoi (cellular / Worley) noise.
 *
 * <p>Returns a deterministic value in {@code [0, 1)} based on the nearest cell point.
 * The seed is obtained from a {@link RandomGenerator} at construction time.
 */
public final class VoronoiNoise implements NoiseGenerator {
  private final long seed;

  /**
   * Creates the voronoi noise using the given rng to generate initial seed.
   *
   * @param random source of the initial seed value
   */
  public VoronoiNoise(RandomGenerator random) {
    seed = random.nextInt(Integer.MAX_VALUE);
  }

  private static int floor(double v) {
    int i = (int) v;
    return v >= i ? i : i - 1;
  }

  private static double hash(int x, int y, int z, long seed) {
    // FNV-1a 64-bit offset basis.
    long h = -3750763034362895579L;
    h ^= x;
    h *= 1099511628211L;
    h ^= y;
    h *= 1099511628211L;
    h ^= z;
    h *= 1099511628211L;
    h ^= seed;
    h *= 1099511628211L;
    long u = h;
    u ^= (u >>> 33);
    u *= 0xff51afd7ed558ccdL;
    u ^= (u >>> 33);
    u *= 0xc4ceb9fe1a85ec53L;
    u ^= (u >>> 33);
    return (u & 0x1FFFFFFFFFFFFFL) / (double) 0x1FFFFFFFFFFFFFL;
  }

  @Override
  public double generate(double x, double y, double z) {
    int x0 = floor(x);
    int y0 = floor(y);
    int z0 = floor(z);
    double minDist = Double.MAX_VALUE;
    double cx = 0;
    double cy = 0;
    double cz = 0;

    for (int k = z0 - 2; k <= z0 + 2; k++) {
      for (int j = y0 - 2; j <= y0 + 2; j++) {
        for (int i = x0 - 2; i <= x0 + 2; i++) {
          double xp = i + hash(i, j, k, seed);
          double yp = j + hash(i, j, k, seed + 1);
          double zp = k + hash(i, j, k, seed + 2);
          double dx = xp - x;
          double dy = yp - y;
          double dz = zp - z;
          double d = dx * dx + dy * dy + dz * dz;
          if (d < minDist) {
            minDist = d;
            cx = xp;
            cy = yp;
            cz = zp;
          }
        }
      }
    }

    return hash(floor(cx), floor(cy), floor(cz), 0);
  }
}
