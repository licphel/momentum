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

/**
 * Represents an audio output device.
 *
 * <p>A mixer owns the connection to the underlying audio backend and is the
 * factory for creating {@link Clip} instances. Mixers are long-lived resources; close them only when shutting down the
 * audio subsystem.
 *
 * @see MixerInfo
 * @see Clip
 */
public interface Mixer extends AutoCloseable {
  /**
   * Returns static information about this mixer.
   *
   * @return mixer information
   */
  MixerInfo info();

  /**
   * Processes pending mixer events.
   *
   * <p>Must be called regularly to update playback state and trigger
   * loop callbacks.
   */
  void pollEvents();

  /**
   * Creates a new, unopened {@link Clip} backed by this mixer.
   *
   * @return a new clip in unopened state
   */
  Clip getClip();

  /**
   * Enqueues a block of work to run on the audio thread.
   *
   * @param work the commands to execute
   */
  void submit(Runnable work);

  @Override
  void close();
}
