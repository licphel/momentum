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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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

/**
 * Backend-neutral parameters for the standard EFX echo effect.
 *
 * @param delay delay of the first echo in seconds
 * @param leftRightDelay additional delay applied to the opposite channel
 * @param damping high-frequency damping of the echo
 * @param feedback amount of the delayed signal fed back into the echo
 * @param spread stereo spread of the echo
 * @param sendGain wet signal gain sent to the auxiliary effect slot
 */
public record Echo(float delay, float leftRightDelay, float damping,
                   float feedback, float spread, float sendGain) {
  /**
   * Creates a compact echo preset from normalized strength and delay.
   *
   * @param strength normalized echo strength
   * @param delay first-echo delay in seconds
   * @return echo parameters
   */
  public static Echo simple(float strength, float delay) {
    float value = Math.clamp(strength, 0F, 1F);
    return new Echo(delay, Math.min(delay * 0.5F, 0.404F),
        0.35F + value * 0.45F, value * 0.75F, 0F, value);
  }
}
