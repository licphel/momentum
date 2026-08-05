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
 * Turbulence octave noise — sums the absolute value of each layer to produce fluid, folded patterns.
 *
 * <p>Each octave applies {@code n = |n * 2 − 1|} before accumulating, which
 * creates sharp edges and a "turbulent" appearance.
 */
public final class OctaveTurbulenceNoise implements NoiseGenerator {
  private final NoiseGenerator base;
  private final int octaves;
  private final double persistence;
  private final double lacunarity;
  private final double amplitudeScale;
  private final double frequencyScale;

  /**
   * Creates a turbulence noise.
   *
   * @param base           underlying noise generator
   * @param octaves        number of frequency layers
   * @param persistence    amplitude multiplier per octave (typically 0.5)
   * @param lacunarity     frequency multiplier per octave (typically 2.0)
   * @param amplitudeScale overall output scale
   * @param frequencyScale initial frequency scale
   */
  public OctaveTurbulenceNoise(NoiseGenerator base, int octaves, double persistence, double lacunarity,
                               double amplitudeScale, double frequencyScale) {
    this.base = base;
    this.octaves = octaves;
    this.persistence = persistence;
    this.lacunarity = lacunarity;
    this.amplitudeScale = amplitudeScale;
    this.frequencyScale = frequencyScale;
  }

  /**
   * Creates a turbulence noise with default parameters (persistence=0.5, lacunarity=2, scale=1).
   *
   * @param base    underlying noise generator
   * @param octaves number of frequency layers
   */
  public OctaveTurbulenceNoise(NoiseGenerator base, int octaves) {
    this(base, octaves, 0.5, 2.0, 1.0, 1.0);
  }

  @Override
  public double generate(double x, double y, double z) {
    double amplitude = 1.0;
    double frequency = 1.0;
    double value = 0.0;
    double maxAmplitude = 0.0;

    for (int i = 0; i < octaves; i++) {
      double n = base.generate(x * frequency * frequencyScale, y * frequency * frequencyScale,
          z * frequency * frequencyScale);
      value += Math.abs(n * 2.0 - 1.0) * amplitude;
      maxAmplitude += amplitude;
      amplitude *= persistence;
      frequency *= lacunarity;
    }
    return maxAmplitude > 0.0 ? value / maxAmplitude * amplitudeScale : 0.0;
  }
}
