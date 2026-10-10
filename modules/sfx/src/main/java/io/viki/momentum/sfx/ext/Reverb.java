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

/**
 * Backend-neutral parameters for the standard EFX environmental reverb.
 * Values intentionally correspond one-to-one with OpenAL's reverb object;
 * {@code sendGain} is the auxiliary-slot gain used to mix the wet signal.
 *
 * @param density modal density of the reverberation
 * @param diffusion echo density of the reverberation
 * @param gain overall reverb gain
 * @param gainHF high-frequency reverb gain
 * @param decayTime reverb decay time in seconds
 * @param decayHFRatio ratio of high-frequency decay time to overall decay time
 * @param reflectionsGain early-reflections gain
 * @param reflectionsDelay early-reflections delay in seconds
 * @param lateReverbGain late-reverberation gain
 * @param lateReverbDelay late-reverberation delay in seconds
 * @param airAbsorptionGainHF high-frequency air-absorption factor
 * @param roomRolloffFactor distance attenuation factor
 * @param decayHFLimit whether high-frequency decay is limited
 * @param sendGain gain sent to the auxiliary reverb slot
 */
public record Reverb(float density, float diffusion, float gain, float gainHF,
                     float decayTime, float decayHFRatio, float reflectionsGain,
                     float reflectionsDelay, float lateReverbGain,
                     float lateReverbDelay, float airAbsorptionGainHF,
                     float roomRolloffFactor, boolean decayHFLimit, float sendGain) {
  /**
   * Creates a compact environmental reverb preset from wet strength and delay.
   *
   * @param strength normalized wet strength
   * @param delay early-reflections delay in seconds
   * @return reverb parameters derived from the supplied strength and delay
   */
  public static Reverb simple(float strength, float delay) {
    return new Reverb(0.65F + Math.clamp(strength, 0F, 1F) * 0.35F,
        0.75F + Math.clamp(strength, 0F, 1F) * 0.25F,
        Math.clamp(strength, 0F, 1F) * 0.35F,
        0.7F,
        0.8F + Math.clamp(strength, 0F, 1F) * 3.2F,
        0.75F,
        Math.clamp(strength, 0F, 1F) * 0.25F,
        delay,
        Math.clamp(strength, 0F, 1F) * 0.4F,
        Math.min(Math.max(delay, 0F) * 0.5F, 0.1F),
        0.9F + Math.clamp(strength, 0F, 1F) * 0.1F,
        0F,
        true,
        Math.clamp(strength, 0F, 1F));
  }
}
