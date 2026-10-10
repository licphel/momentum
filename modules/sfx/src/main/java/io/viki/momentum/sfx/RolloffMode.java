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

package io.viki.momentum.sfx;

/**
 * Shared distance-gain law for positional clips.
 *
 * <p>The mixer owns one mode and every clip that enables rolloff uses that
 * mode. The gain is calculated by the mixer, so backends cannot accidentally
 * apply different attenuation curves. {@link #cutoffGain()} is also used to
 * avoid allocating/starting a source when a sound is already inaudible.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @param type             distance curve
 * @param referenceDistance distance at which gain is still one
 * @param maxDistance      distance at which the source is silent
 * @param factor           curve strength
 * @param cutoffGain       gain at or below which a new source is skipped
 */
public record RolloffMode(Type type, float referenceDistance, float maxDistance,
                          float factor, float cutoffGain) {
  /** Distance curve families supported by the API. */
  public enum Type {
    /** No distance attenuation. */
    NONE,
    /** OpenAL-compatible inverse-distance attenuation. */
    INVERSE,
    /** Linear attenuation between the reference and maximum distances. */
    LINEAR,
    /** Exponential attenuation after the reference distance. */
    EXPONENTIAL
  }

  /** A conventional inverse-distance curve for world effects. */
  public static final RolloffMode DEFAULT = inverse(1F, 64F, 1F, 0.01F);
  /** A mode that leaves all gains unchanged. */
  public static final RolloffMode NONE = new RolloffMode(Type.NONE, 1F, Float.POSITIVE_INFINITY, 0F, 0F);

  /**
   * Creates an inverse-distance mode.
   *
   * @param referenceDistance distance at which gain remains one
   * @param maxDistance distance at which gain reaches zero
   * @param factor inverse-curve strength
   * @param cutoffGain gain threshold for starting a source
   * @return inverse-distance rolloff mode
   */
  public static RolloffMode inverse(float referenceDistance, float maxDistance,
                                    float factor, float cutoffGain) {
    return new RolloffMode(Type.INVERSE, referenceDistance, maxDistance, factor, cutoffGain);
  }

  /**
   * Creates a linear mode.
   *
   * @param referenceDistance distance at which gain remains one
   * @param maxDistance distance at which gain reaches zero
   * @param factor linear-curve strength
   * @param cutoffGain gain threshold for starting a source
   * @return linear rolloff mode
   */
  public static RolloffMode linear(float referenceDistance, float maxDistance,
                                   float factor, float cutoffGain) {
    return new RolloffMode(Type.LINEAR, referenceDistance, maxDistance, factor, cutoffGain);
  }

  /**
   * Creates an exponential mode.
   *
   * @param referenceDistance distance at which gain remains one
   * @param maxDistance distance at which gain reaches zero
   * @param factor exponential-curve strength
   * @param cutoffGain gain threshold for starting a source
   * @return exponential rolloff mode
   */
  public static RolloffMode exponential(float referenceDistance, float maxDistance,
                                         float factor, float cutoffGain) {
    return new RolloffMode(Type.EXPONENTIAL, referenceDistance, maxDistance, factor, cutoffGain);
  }

  /**
   * Returns the attenuation gain at a world-space distance.
   *
   * @param distance world-space distance from the listener
   * @return attenuation gain in the range {@code [0, 1]}
   */
  public float gain(float distance) {
    if (type == Type.NONE || distance <= referenceDistance) {
      return 1F;
    }
    if (distance >= maxDistance) {
      return 0F;
    }
    float gain = switch (type) {
      case INVERSE -> referenceDistance
          / (referenceDistance + factor * (distance - referenceDistance));
      case LINEAR -> 1F - factor * (distance - referenceDistance)
          / (maxDistance - referenceDistance);
      case EXPONENTIAL -> (float) Math.pow(distance / referenceDistance, -factor);
      default -> throw new IllegalStateException("Unexpected value: " + type);
    };
    return Math.clamp(gain, 0F, 1F);
  }

  /**
   * Returns whether a newly requested source is worth starting.
   *
   * @param distance world-space distance from the listener
   * @return whether the attenuation gain exceeds {@link #cutoffGain()}
   */
  public boolean audible(float distance) {
    return gain(distance) > cutoffGain;
  }
}
