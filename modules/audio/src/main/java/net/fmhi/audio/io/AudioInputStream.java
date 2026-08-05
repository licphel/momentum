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

package net.fmhi.audio.io;

import net.fmhi.audio.AudioFormat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base for decoded PCM audio streams.
 *
 * <p>Subclasses wrap a specific audio container format and expose its
 * decoded samples as an {@link InputStream} of raw PCM bytes. The sample layout is described by {@link #format()},
 * which is valid as soon as the decoder is constructed.
 *
 * @see WaveInputStream
 * @see AudioFormat
 */
public abstract class AudioInputStream extends InputStream {
  /**
   * Probes an input stream for a known audio container and returns a decoder for it.
   *
   * @param in source stream, must support mark and reset
   * @return a decoder whose {@link #format()} reflects the detected layout
   * @throws IOException          if an I/O error occurs
   * @throws AudioFormatException if the container is not recognized or the header is malformed
   */
  public static AudioInputStream open(InputStream in) throws IOException, AudioFormatException {
    BufferedInputStream buffered = in instanceof BufferedInputStream b ? b : new BufferedInputStream(in);
    buffered.mark(12);
    byte[] header = buffered.readNBytes(12);
    buffered.reset();

    if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E') {
      return new WaveInputStream(buffered);
    }

    throw new AudioFormatException("Unsupported audio format");
  }

  /**
   * Returns the PCM format of the decoded audio data.
   *
   * @return audio format describing sample rate, bit depth, channel count, and byte order
   */
  public abstract AudioFormat format();

  /**
   * Throws {@link IOException} unconditionally.
   *
   * <p>Audio decoders do not support mark and reset.
   *
   * @throws IOException always
   */
  @Override
  public void reset() throws IOException {
    throw new IOException("Audio decoding cannot be reset");
  }
}
