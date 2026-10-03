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

import io.viki.momentum.audio.io.AudioInputStream;

import java.util.function.Supplier;

/**
 * A clip that incrementally consumes decoded PCM input.
 *
 * <p>The clip takes ownership of each supplied input stream. Streaming clips support playback,
 * pause, stop, looping, pitch and volume controls, but cannot seek.
 * Implementations are not required to be thread-safe.
 */
public interface StreamingClip extends Clip {
  /**
   * Opens this clip with a factory for decoded PCM input.
   *
   * <p>The factory is invoked once per repetition. Every returned stream must use the same format.
   *
   * @param streamSupplier factory that creates a fresh decoded PCM input
   */
  void open(Supplier<? extends AudioInputStream> streamSupplier);

  /**
   * Wraps a loaded byte array into a stream.
   * No reason to use this. Just for compatibility.
   */
  @Override
  default void open(AudioFormat format, byte[] data) {
    open(() -> AudioInputStream.wrap(format, data));
  }

  @Override
  default void setPosition(float position) {
    throw new UnsupportedOperationException("While streaming, reset capability is unknown");
  }
}
