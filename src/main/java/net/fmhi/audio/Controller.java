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

package net.fmhi.audio;

import org.jspecify.annotations.Nullable;

/**
 * Identifies the real-time controls available on a {@link Clip}.
 *
 * <p>Each constant represents a parameter that can be read or modified
 * during playback via {@link Clip#get(Controller)} and {@link Clip#set(Controller, float)}.
 *
 * @see Clip
 */
public enum Controller {
  /**
   * Gain multiplier for the clip.
   *
   * <p>A value of {@code 1.0} is full volume; {@code 0.0} is silent.
   * Values above {@code 1.0} amplify the signal. Negative values are clamped to {@code 0.0}.
   */
  VOLUME(float.class),

  /**
   * Playback speed relative to the original rate.
   *
   * <p>A value of {@code 1.0} is normal speed; {@code 2.0} doubles the
   * speed and raises pitch by one octave; {@code 0.5} halves it. Negative values are clamped to {@code 0.0}.
   */
  PITCH(float.class),

  /**
   * Playback position within the current repetition, in seconds.
   *
   * <p>The value is updated asynchronously by the audio backend during
   * playback.
   */
  POSITION(float.class);

  /** The expected Java type for values of this controller. */
  private final Class<?> clazz;

  Controller(Class<?> clazz) {
    this.clazz = clazz;
  }

  /**
   * Validates that a value is of the correct type for this controller.
   *
   * @param value the value to validate, may be {@code null}
   * @throws IllegalArgumentException if the value is not of the expected type
   */
  public void validate(@Nullable Object value) {
    if (!clazz.isInstance(value)) {
      throw new IllegalArgumentException("Invalid value for controller " + name() + ": " + value);
    }
  }
}
