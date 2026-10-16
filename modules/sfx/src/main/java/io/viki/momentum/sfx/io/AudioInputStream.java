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

package io.viki.momentum.sfx.io;

import io.viki.momentum.sfx.AudioFormat;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Abstract base for decoded PCM audio streams.
 *
 * <p>Subclasses wrap a specific audio container format and expose its
 * decoded samples as an {@link InputStream} of raw PCM bytes. The sample layout is described by {@link #format()},
 * which is valid as soon as the decoder is constructed.
 *
 * @see WaveInputStream
 * @see OggVorbisInputStream
 * @see AudioFormat
 */
public abstract class AudioInputStream extends InputStream {
  private float duration = -1F;

  /**
   * Returns the full PCM duration, not the unread portion.
   * @return duration in seconds, or {@code -1} for a source whose length is unknown
   */
  public float getDuration() {
    return duration;
  }

  /**
   * Opens a decoder with duration metadata obtained from a reopenable source.
   * The returned decoder owns the input, as with {@link #open(InputStream)}.
   *
   * @param input encoded audio stream
   * @param duration known duration in seconds, or {@code -1} if unknown
   * @return PCM decoder
   * @throws IOException if opening fails
   */
  public static AudioInputStream open(InputStream input, float duration) throws IOException {
    AudioInputStream result = open(input);
    result.duration = duration;
    return result;
  }

  /**
   * Reads finite WAVE/Vorbis duration metadata without decoding PCM. Consumes but does not close
   * the input. WAVE reads its header; Ogg walks compressed page headers and skips payloads.
   *
   * @param input encoded source positioned at its beginning
   * @return duration in seconds
   * @throws IOException if the container is unsupported, truncated or contains multiple Ogg streams
   */
  public static float getDuration(InputStream input) throws IOException {
    BufferedInputStream source = input instanceof BufferedInputStream buffered
        ? buffered : new BufferedInputStream(input);
    return switch (type(source)) {
      case "RIFF" -> WaveInputStream.getDuration(source);
      case "OggS" -> OggVorbisInputStream.getDuration(source);
      default -> throw new AudioFormatException("Unsupported audio duration container");
    };
  }

  /**
   * Wraps raw PCM data as an audio input stream without copying it.
   *
   * @param format format describing the PCM data
   * @param data   raw PCM bytes matching {@code format}
   * @return a new stream positioned at the beginning of {@code data}
   * @throws IllegalArgumentException if {@code data} does not contain complete PCM frames
   */
  public static AudioInputStream wrap(AudioFormat format, byte[] data) {
    if (data.length % format.frameSize() != 0) {
      throw new IllegalArgumentException("PCM data length must be aligned to the audio frame size");
    }
    return new WrappedAudioInputStream(data, format);
  }

  /**
   * Probes an input stream for a known audio container and returns a decoder for it.
   *
   * @param in source stream, buffered internally when necessary
   * @return a decoder whose {@link #format()} reflects the detected layout
   * @throws IOException          if an I/O error occurs
   * @throws AudioFormatException if the container is not recognized or the header is malformed
   */
  public static AudioInputStream open(InputStream in) throws IOException, AudioFormatException {
    BufferedInputStream buffered = in instanceof BufferedInputStream b ? b : new BufferedInputStream(in);
    return switch (type(buffered)) {
      case "RIFF" -> WaveInputStream.open(buffered);
      case "OggS" -> OggVorbisInputStream.open(buffered);
      default -> throw new AudioFormatException("Unsupported audio format");
    };
  }

  private static String type(BufferedInputStream source) throws IOException {
    source.mark(4);
    byte[] signature = source.readNBytes(4);
    source.reset();
    return new String(signature, StandardCharsets.US_ASCII);
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
