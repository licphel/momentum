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

package io.viki.momentum.audio;

import org.jspecify.annotations.Nullable;

/**
 * Represents a decoded audio clip prepared for low-latency playback.
 *
 * <p>A clip may be played repeatedly, and each play request may create an independent voice. This
 * interface does not require implementations to be thread-safe.
 *
 * @see ClipManager
 */
public interface Clip extends AutoCloseable {
  /** Value accepted by {@link #loop(int)} to request continuous playback. */
  int LOOP_CONTINUOUSLY = Integer.MAX_VALUE;

  /**
   * Opens this clip with raw PCM data.
   *
   * @param format format describing the PCM data
   * @param data raw PCM bytes matching {@code format}
   * @throws IllegalStateException if this clip is already open
   * @throws IllegalArgumentException if the format or data is unsupported
   */
  void open(AudioFormat format, byte[] data);

  /**
   * Returns whether this clip is open for playback.
   *
   * @return {@code true} when {@link #open} has completed
   */
  boolean isOpen();

  /**
   * Pauses every active voice belonging to this clip.
   *
   * <p>Pausing preserves each voice's playback position.
   */
  void pause();

  /**
   * Stops every active voice belonging to this clip.
   */
  void stop();

  /**
   * Starts a new voice with the requested repetition count.
   *
   * @param count number of repetitions, or {@link #LOOP_CONTINUOUSLY} for continuous playback
   * @throws IllegalArgumentException if {@code count} is negative
   */
  void loop(int count);

  /**
   * Starts a new voice that plays once.
   */
  default void play() {
    loop(1);
  }

  /**
   * Returns whether at least one voice for this clip is active.
   *
   * @return is clip active
   */
  boolean isPlaying();

  /**
   * Returns whether the clip manager may release this clip.
   *
   * @return whether this clip should be released
   */
  boolean shouldClose();

  /**
   * Returns the current gain multiplier.
   *
   * @return gain multiplier
   */
  float getVolume();

  /**
   * Sets the gain multiplier.
   *
   * @param volume new gain multiplier; negative values are clamped to zero
   */
  void setVolume(float volume);

  /**
   * Returns the current playback speed multiplier.
   *
   * @return playback speed multiplier
   */
  float getPitch();

  /**
   * Sets the playback speed multiplier.
   *
   * @param pitch new playback speed multiplier; negative values are clamped to zero
   */
  void setPitch(float pitch);

  /**
   * Returns the current playback offset.
   *
   * @return playback offset in seconds
   */
  float getPosition();

  /**
   * Sets the playback offset.
   *
   * @param position new offset in seconds; negative values are clamped to zero
   */
  void setPosition(float position);

  /**
   * Returns the PCM format used to open this clip.
   *
   * @return audio format, or {@code null} before the clip is opened
   */
  @Nullable AudioFormat format();

  @Override
  void close();
}
