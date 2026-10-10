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

package io.viki.momentum.sfx.ext;

import java.util.List;

/**
 * Backend-neutral parameters for the built-in filter effect.
 *
 * <p>The direct gains mirror the EFX filter object ({@code gain},
 * {@code gainLF}, and {@code gainHF}). When {@code points} is non-empty,
 * {@code strength} selects a point on that response curve and replaces the
 * two frequency gains. This keeps the full backend parameter set available
 * while allowing a game system to drive it with one normalized control.
 *
 * @param mode filter response mode
 * @param strength normalized response-curve position
 * @param gain overall filter gain
 * @param gainLowFrequency low-frequency gain
 * @param gainHighFrequency high-frequency gain
 * @param points optional response-curve points
 */
public record Filter(FilterMode mode, float strength, float gain,
                     float gainLowFrequency, float gainHighFrequency,
                     List<FilterPoint> points) {
  /**
   * Creates filter parameters with explicit gains and response-curve points.
   *
   * @param mode filter response mode
   * @param strength normalized response-curve position
   * @param gain overall filter gain
   * @param gainLowFrequency low-frequency gain
   * @param gainHighFrequency high-frequency gain
   * @param points optional response-curve points
   */
  public Filter {
    points = List.copyOf(points);
  }

  /**
   * Creates filter parameters with a response curve and unity direct gains.
   *
   * @param mode filter response mode
   * @param strength normalized response-curve position
   * @param points response-curve points
   */
  public Filter(FilterMode mode, float strength, List<FilterPoint> points) {
    this(mode, strength, 1F, 1F, 1F, points);
  }

  /**
   * Creates filter parameters with a response curve and unity direct gains.
   *
   * @param mode filter response mode
   * @param strength normalized response-curve position
   * @param points response-curve points
   */
  public Filter(FilterMode mode, float strength, FilterPoint... points) {
    this(mode, strength, List.of(points));
  }

  /**
   * Creates filter parameters using explicit gains without a response curve.
   *
   * @param mode filter response mode
   * @param gain overall filter gain
   * @param gainLowFrequency low-frequency gain
   * @param gainHighFrequency high-frequency gain
   */
  public Filter(FilterMode mode, float gain, float gainLowFrequency,
                float gainHighFrequency) {
    this(mode, 0F, gain, gainLowFrequency, gainHighFrequency, List.of());
  }

  /**
   * Creates low-pass filter parameters driven by a response curve.
   *
   * @param strength normalized response-curve position
   * @param points response-curve points
   * @return low-pass filter parameters
   */
  public static Filter lowPass(float strength, FilterPoint... points) {
    return new Filter(FilterMode.LOWPASS, strength, points);
  }

  /**
   * Creates high-pass filter parameters driven by a response curve.
   *
   * @param strength normalized response-curve position
   * @param points response-curve points
   * @return high-pass filter parameters
   */
  public static Filter highPass(float strength, FilterPoint... points) {
    return new Filter(FilterMode.HIGHPASS, strength, points);
  }

  /**
   * Creates band-pass filter parameters driven by a response curve.
   *
   * @param strength normalized response-curve position
   * @param points response-curve points
   * @return band-pass filter parameters
   */
  public static Filter bandPass(float strength, FilterPoint... points) {
    return new Filter(FilterMode.BANDPASS, strength, points);
  }

  /**
   * Creates filter parameters using explicit direct gains.
   *
   * @param mode filter response mode
   * @param gain overall filter gain
   * @param gainLowFrequency low-frequency gain
   * @param gainHighFrequency high-frequency gain
   * @return filter parameters
   */
  public static Filter gains(FilterMode mode, float gain,
                             float gainLowFrequency, float gainHighFrequency) {
    return new Filter(mode, gain, gainLowFrequency, gainHighFrequency);
  }
}
