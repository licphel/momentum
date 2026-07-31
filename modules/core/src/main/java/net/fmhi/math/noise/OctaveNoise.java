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

/**
 * Fractal (octave) noise: sums multiple scaled layers of a base {@link NoiseGenerator}.
 */
public final class OctaveNoise implements NoiseGenerator {
  private final NoiseGenerator base;
  private final int octaves;
  private final double persistence;
  private final double lacunarity;

  /**
   * Creates an octave noise.
   *
   * @param base        the underlying noise function
   * @param octaves     number of frequency layers
   * @param persistence amplitude multiplier per octave (typically 0.5)
   * @param lacunarity  frequency multiplier per octave (typically 2.0)
   */
  public OctaveNoise(NoiseGenerator base, int octaves, double persistence, double lacunarity) {
    this.base = base;
    this.octaves = octaves;
    this.persistence = persistence;
    this.lacunarity = lacunarity;
  }

  @Override
  public double generate(double x, double y, double z) {
    double value = 0.0;
    double amplitude = 1.0;
    double frequency = 1.0;
    double maxValue = 0.0;

    for (int i = 0; i < octaves; i++) {
      value += base.generate(x * frequency, y * frequency, z * frequency) * amplitude;
      maxValue += amplitude;
      amplitude *= persistence;
      frequency *= lacunarity;
    }
    return maxValue > 0.0 ? value / maxValue : 0.0;
  }
}
