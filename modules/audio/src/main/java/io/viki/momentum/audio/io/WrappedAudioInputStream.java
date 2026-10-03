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

package io.viki.momentum.audio.io;

import io.viki.momentum.audio.AudioFormat;
import io.viki.momentum.util.InternalApi;

import java.io.ByteArrayInputStream;

/**
 * Reads raw bytes as an audio stream for compatibility.
 */
@InternalApi
class WrappedAudioInputStream extends AudioInputStream {
  private final ByteArrayInputStream input;
  private final AudioFormat format;

  public WrappedAudioInputStream(byte[] data, AudioFormat format) {
    this.format = format;
    input = new ByteArrayInputStream(data);
  }

  @Override
  public AudioFormat format() {
    return format;
  }

  @Override
  public int read() {
    return input.read();
  }

  @Override
  public int read(byte[] buffer, int offset, int length) {
    return input.read(buffer, offset, length);
  }

  @Override
  public long skip(long count) {
    return input.skip(count);
  }

  @Override
  public int available() {
    return input.available();
  }
}
