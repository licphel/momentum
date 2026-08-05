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
 * Immutable descriptor for a PCM audio stream.
 *
 * <p>Captures all properties needed to interpret raw PCM data: encoding,
 * sample rate, bit depth, channel count, frame size, and byte order. Provides conversion helpers between bytes, frames,
 * and seconds.
 *
 * @param encoding         sample encoding (e.g. {@link Encoding#PCM_SIGNED})
 * @param sampleRate       samples per second per channel
 * @param sampleSizeInBits bits per sample
 * @param channels         number of interleaved audio channels
 * @param frameSize        bytes per frame
 * @param bigEndian        {@code true} for big-endian sample byte order
 * @param signed           {@code true} if samples use a signed representation
 */
public record AudioFormat(Encoding encoding, int sampleRate, int sampleSizeInBits, int channels, int frameSize,
                          boolean bigEndian, boolean signed) {
  /**
   * Creates a new {@code AudioFormat} instance.
   *
   * @param encoding         sample encoding (e.g. {@link Encoding#PCM_SIGNED})
   * @param sampleRate       samples per second per channel, must be positive
   * @param sampleSizeInBits bits per sample, must be a positive multiple of 8
   * @param channels         number of interleaved audio channels, must be positive
   * @param frameSize        bytes per frame, must equal {@code channels * (sampleSizeInBits / 8)}
   * @param bigEndian        {@code true} for big-endian sample byte order
   * @param signed           {@code true} if samples use a signed representation
   * @throws IllegalArgumentException if any parameter is outside its valid range, or if {@code frameSize} does not
   *                                  equal {@code channels * (sampleSizeInBits / 8)}
   */
  public AudioFormat {
    if (sampleRate <= 0) {
      throw new IllegalArgumentException("sampleRate must be a positive integer");
    }
    if (sampleSizeInBits <= 0 || sampleSizeInBits % 8 != 0) {
      throw new IllegalArgumentException("sampleSizeInBits must be a positive multiple of 8");
    }
    if (channels <= 0) {
      throw new IllegalArgumentException("channels must be a positive integer");
    }
    if (frameSize <= 0) {
      throw new IllegalArgumentException("frameSize must be a positive integer");
    }
    if (channels * (sampleSizeInBits / 8) != frameSize) {
      throw new IllegalArgumentException("frameSize does not match with other args");
    }
  }

  /**
   * Creates an {@code AudioFormat} with an explicit encoding, deriving the signed flag from the encoding.
   *
   * @param encoding         sample encoding
   * @param sampleRate       samples per second per channel
   * @param sampleSizeInBits bits per sample
   * @param channels         number of interleaved audio channels
   * @param frameSize        bytes per frame
   * @param bigEndian        {@code true} for big-endian byte order
   */
  public AudioFormat(Encoding encoding, int sampleRate, int sampleSizeInBits, int channels, int frameSize,
                     boolean bigEndian) {
    this(encoding, sampleRate, sampleSizeInBits, channels, frameSize, bigEndian, encoding != Encoding.PCM_UNSIGNED);
  }

  /**
   * Creates an {@code AudioFormat}, deriving the encoding and frame size from the other parameters.
   *
   * @param sampleRate       samples per second per channel
   * @param sampleSizeInBits bits per sample
   * @param channels         number of interleaved audio channels
   * @param signed           {@code true} for signed samples
   * @param bigEndian        {@code true} for big-endian byte order
   */
  public AudioFormat(int sampleRate, int sampleSizeInBits, int channels, boolean signed, boolean bigEndian) {
    this(signed ? Encoding.PCM_SIGNED : Encoding.PCM_UNSIGNED, sampleRate, sampleSizeInBits, channels,
        (sampleSizeInBits + 7) / 8 * channels, bigEndian, signed);
  }

  /**
   * Returns a signed, little-endian, mono PCM format.
   *
   * @param sampleRate       samples per second
   * @param sampleSizeInBits bits per sample
   * @return a mono {@link Encoding#PCM_SIGNED} format
   */
  public static AudioFormat mono(int sampleRate, int sampleSizeInBits) {
    return new AudioFormat(sampleRate, sampleSizeInBits, 1, true, false);
  }

  /**
   * Returns a signed, little-endian, stereo PCM format.
   *
   * @param sampleRate       samples per second
   * @param sampleSizeInBits bits per sample
   * @return a stereo {@link Encoding#PCM_SIGNED} format
   */
  public static AudioFormat stereo(int sampleRate, int sampleSizeInBits) {
    return new AudioFormat(sampleRate, sampleSizeInBits, 2, true, false);
  }

  /**
   * Returns the byte rate of this format.
   *
   * @return byte rate, equal to {@code sampleRate * frameSize}
   */
  public int getByteRate() {
    return sampleRate * frameSize;
  }

  /**
   * Converts a byte count to a complete frame count.
   *
   * @param bytes number of PCM bytes
   * @return number of complete frames, truncating any partial frame
   */
  public int bytesToFrames(int bytes) {
    return frameSize == 0 ? 0 : bytes / frameSize;
  }

  /**
   * Converts a frame count to the corresponding byte count.
   *
   * @param frames number of audio frames
   * @return number of bytes required for the given number of frames
   */
  public int framesToBytes(int frames) {
    return frames * frameSize;
  }

  /**
   * Converts a duration in seconds to the corresponding byte offset.
   *
   * @param seconds duration in seconds
   * @return byte offset corresponding to the given duration, truncated toward zero
   */
  public int secondsToBytes(double seconds) {
    return (int) (seconds * getByteRate());
  }

  /**
   * Converts a byte offset to the corresponding duration in seconds.
   *
   * @param bytes byte offset in the PCM data
   * @return duration in seconds corresponding to the given byte offset
   */
  public double bytesToSeconds(int bytes) {
    return bytes / (double) getByteRate();
  }

  /**
   * Converts a duration in seconds to a frame count.
   *
   * @param seconds duration in seconds
   * @return number of frames corresponding to the given duration, truncated toward zero
   */
  public int secondsToFrames(double seconds) {
    return (int) (seconds * sampleRate);
  }

  /**
   * Converts a frame count to the corresponding duration in seconds.
   *
   * @param frames number of audio frames
   * @return duration in seconds corresponding to the given frame count
   */
  public double framesToSeconds(int frames) {
    return frames / (double) sampleRate;
  }

  @Override
  public String toString() {
    return String.format("%s %.1f kHz, %d-bit, %dch, %s", encoding, sampleRate / 1000.0, sampleSizeInBits, channels,
        bigEndian ? "BE" : "LE");
  }
}