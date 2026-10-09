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

package io.viki.momentum.sfx;

import io.viki.momentum.util.FloatSupplier;
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
   * @param data   raw PCM bytes matching {@code format}
   * @throws IllegalStateException    if this clip is already open
   * @throws IllegalArgumentException if the format or data is unsupported
   */
  void open(AudioFormat format, byte[] data);

  /**
   * Opens reusable decoded samples without taking responsibility for playback or release.
   *
   * <p>The caller owns this clip, directly or through {@link ClipManager}. This method
   * neither starts playback nor registers the clip with a manager.
   *
   * @param sound the decoded samples to open
   * @throws IllegalStateException    if this clip is already open
   * @throws IllegalArgumentException if the sample format is unsupported
   */
  default void open(Sound sound) {
    open(sound.format(), sound.data());
  }

  /**
   * Polls events for this clip, such as streaming read, state/volume read, etc.
   */
  default void poll() {
  }

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
   * Starts or continues playback without resetting its position or repetition count.
   *
   * @throws IllegalStateException if this clip is not open or has completed playback
   */
  void resume();

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
   * Sets a fixed gain multiplier, replacing any dynamic gain source.
   *
   * @param volume new gain multiplier; negative values are clamped to zero
   */
  void setVolume(float volume);

  /**
   * Obtains gain dynamically during playback rather than capturing its initial value.
   *
   * <p>The source is sampled when playback events are processed. A {@link VolumeControl}
   * may be supplied directly; the source never takes ownership of this clip. The supplier
   * must support invocation from the backend's audio thread and from gain observation.
   *
   * @param volume supplies a nonnegative gain multiplier
   */
  void setVolume(FloatSupplier volume);

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

  /**
   * Stops playback and releases backend resources owned by this clip.
   *
   * <p>Playback completion does not close a clip automatically. The caller or a lifecycle
   * manager must invoke this method when its resources are no longer needed.
   */
  @Override
  void close();

  /**
   * Tries to enable native auto closure extension.
   */
  default void __enableNativeAutoClosure() {
    throw new UnsupportedOperationException();
  }
}
