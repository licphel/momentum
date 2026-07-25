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
 * A short audio clip loaded into memory for low-latency playback.
 *
 * <p>Clips are optimized for sounds that must start instantly and may be
 * triggered many times. The raw PCM data is uploaded once via {@link #open(AudioFormat, byte[])} and can be replayed
 * any number of times without re-decoding.
 *
 * <p>Multiple simultaneous playbacks of the same clip are supported: each
 * call to a playback method starts an independent voice.
 *
 * @see ClipManager
 */
public interface Clip extends AutoCloseable {
  /**
   * Value passed to {@link #loop(int)} to request infinite looping.
   */
  int LOOP_CONTINUOUSLY = Integer.MAX_VALUE;

  /**
   * Uploads PCM data to the audio backend and prepares the clip for playback.
   *
   * <p>Must be called once before any playback method.
   *
   * @param format the PCM format of the data
   * @param data   raw PCM bytes in the layout described by the format
   * @throws IllegalStateException    if the clip has already been opened
   * @throws IllegalArgumentException if the format is not supported by the backend
   */
  void open(AudioFormat format, byte[] data);

  /**
   * Returns whether this clip has been opened.
   *
   * @return {@code true} if {@link #open} has been called and the clip is ready for playback
   */
  boolean isOpen();

  /**
   * Pauses all active playback voices of this clip.
   *
   * <p>Has no effect if the clip is already paused. Does not reset
   * playback position.
   */
  void pause();

  /**
   * Stops all active playback voices of this clip.
   *
   * <p>Has no effect if the clip is already stopped.
   */
  void stop();

  /**
   * Starts playback, repeating the clip a given number of times.
   *
   * <p>Each call starts a new playback from the beginning.
   *
   * @param count total number of times to play, or {@link #LOOP_CONTINUOUSLY} for infinite looping
   * @throws IllegalArgumentException if {@code count} is negative
   */
  void loop(int count);

  /**
   * Starts playback of the clip once.
   *
   * <p>If the clip is already playing, this starts another independent
   * instance.
   */
  default void play() {
    loop(1);
  }

  /**
   * Returns whether this clip is currently playing.
   *
   * @return {@code true} if at least one playback voice is active
   */
  boolean isPlaying();

  /**
   * Returns whether this clip is ready to be closed by a {@link ClipManager}.
   *
   * @return {@code true} if the clip may safely be closed
   */
  boolean shouldClose();

  /**
   * Returns the current value of the given controller.
   *
   * @param ctr the controller to read
   * @return current value of the controller
   */
  float get(Controller ctr);

  /**
   * Updates the value of the given controller.
   *
   * @param ctr   the controller to update
   * @param value the new value
   */
  void set(Controller ctr, float value);

  /**
   * Returns the PCM format this clip was opened with.
   *
   * @return the audio format, or {@code null} if not yet opened
   */
  @Nullable AudioFormat format();

  @Override
  void close();
}
