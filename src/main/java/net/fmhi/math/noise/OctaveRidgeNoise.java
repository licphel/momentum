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
 * Ridge octave noise — sums ridge-transformed layers to produce sharp, vein-like patterns.
 *
 * <p>Each octave applies the transform {@code n = ridgeOffset − |n * 2 − 1|} before
 * accumulating, which folds the signal around its peak to create ridges.
 */
public final class OctaveRidgeNoise implements NoiseGenerator {
  private final NoiseGenerator base;
  private final int octaves;
  private final double persistence;
  private final double lacunarity;
  private final double amplitudeScale;
  private final double frequencyScale;
  private final double ridgeOffset;

  /**
   * Creates a ridge noise.
   *
   * @param base           underlying noise generator
   * @param octaves        number of frequency layers
   * @param persistence    amplitude multiplier per octave (typically 0.5)
   * @param lacunarity     frequency multiplier per octave (typically 2.0)
   * @param amplitudeScale overall output scale
   * @param frequencyScale initial frequency scale
   * @param ridgeOffset    ridge fold point (typically 1.0)
   */
  public OctaveRidgeNoise(NoiseGenerator base, int octaves, double persistence, double lacunarity,
                          double amplitudeScale, double frequencyScale, double ridgeOffset) {
    this.base = base;
    this.octaves = octaves;
    this.persistence = persistence;
    this.lacunarity = lacunarity;
    this.amplitudeScale = amplitudeScale;
    this.frequencyScale = frequencyScale;
    this.ridgeOffset = ridgeOffset;
  }

  /**
   * Creates a ridge noise with default parameters (persistence=0.5, lacunarity=2, scale=1, ridge=1).
   *
   * @param base    underlying noise generator
   * @param octaves number of frequency layers
   */
  public OctaveRidgeNoise(NoiseGenerator base, int octaves) {
    this(base, octaves, 0.5, 2.0, 1.0, 1.0, 1.0);
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
      n = ridgeOffset - Math.abs(n * 2.0 - 1.0);
      value += n * amplitude;
      maxAmplitude += amplitude * ridgeOffset;
      amplitude *= persistence;
      frequency *= lacunarity;
    }
    return maxAmplitude > 0.0 ? value / maxAmplitude * amplitudeScale : 0.0;
  }
}
