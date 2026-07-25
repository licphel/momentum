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
import net.fmhi.audio.Encoding;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@link AudioInputStream} implementation for the RIFF/WAVE container.
 *
 * <p>Parses the WAVE header on construction and exposes the raw PCM bytes
 * from the data chunk. Supported format tags include WAVE_FORMAT_PCM, WAVE_FORMAT_IEEE_FLOAT, and
 * WAVE_FORMAT_EXTENSIBLE.
 *
 * <p>Instances are not thread-safe.
 *
 * @see AudioInputStream
 */
public final class WaveInputStream extends AudioInputStream {
  private static final int WAVE_FORMAT_PCM = 0x0001;
  private static final int WAVE_FORMAT_IEEE_FLOAT = 0x0003;
  private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

  private static final byte[] SUBTYPE_PCM = {0x01, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xAA
      , 0x00, 0x38, (byte) 0x9B, 0x71};
  private static final byte[] SUBTYPE_FLOAT = {0x03, 0x00, 0x00, 0x00, 0x10, 0x00, (byte) 0x80, 0x00, 0x00,
      (byte) 0xAA, 0x00, 0x38, (byte) 0x9B, 0x71};

  private final AudioFormat format;
  private final InputStream in;
  private long remaining;

  /**
   * Creates a decoder for a WAVE audio stream.
   *
   * @param source the input stream to decode
   * @throws IOException if header parsing fails
   */
  public WaveInputStream(InputStream source) throws IOException {
    BufferedInputStream buffered = source instanceof BufferedInputStream b ? b : new BufferedInputStream(source);

    Header header = parseHeader(buffered);
    in = buffered;
    format = header.format;
    remaining = header.dataSize;
  }

  /**
   * Parses the RIFF/WAVE container header and returns the format and data chunk information.
   */
  private static Header parseHeader(InputStream in) throws IOException {
    expectAscii(in, "RIFF");
    skipFully(in, 4);
    expectAscii(in, "WAVE");

    AudioFormat format = null;
    long dataSize;

    while (true) {
      String chunkId = readAscii(in, 4);
      int chunkSize = readIntLE(in);

      if ("fmt ".equals(chunkId)) {
        format = readFmtChunk(in, chunkSize);
      } else if ("data".equals(chunkId)) {
        if (format == null) {
          throw new IOException("fmt chunk missing before data");
        }
        dataSize = Integer.toUnsignedLong(chunkSize);
        break;
      } else {
        skipFully(in, chunkSize);
      }

      if ((chunkSize & 1) != 0) {
        skipFully(in, 1);
      }
    }

    return new Header(format, dataSize);
  }

  /**
   * Parses the WAVE {@code fmt} chunk and returns the corresponding {@link AudioFormat}.
   */
  private static AudioFormat readFmtChunk(InputStream in, int chunkSize) throws IOException {
    int formatTag = readShortLE(in);
    int channels = readShortLE(in);
    int sampleRate = readIntLE(in);
    skipFully(in, 4); // byteRate
    int blockAlign = readShortLE(in);
    int bitsPerSample = readShortLE(in);

    Encoding encoding;

    if (formatTag == WAVE_FORMAT_PCM) {
      encoding = bitsPerSample == 8 ? Encoding.PCM_UNSIGNED : Encoding.PCM_SIGNED;
    } else if (formatTag == WAVE_FORMAT_IEEE_FLOAT) {
      encoding = Encoding.PCM_FLOAT;
    } else if (formatTag == WAVE_FORMAT_EXTENSIBLE) {
      int cbSize = readShortLE(in);

      if (cbSize < 22) {
        throw new IOException("Invalid extensible fmt chunk");
      }

      skipFully(in, 2); // validBits
      skipFully(in, 4); // channelMask

      byte[] guid = in.readNBytes(16);
      if (guid.length != 16) {
        throw new EOFException();
      }

      if (matches(guid, SUBTYPE_PCM)) {
        encoding = bitsPerSample == 8 ? Encoding.PCM_UNSIGNED : Encoding.PCM_SIGNED;
      } else if (matches(guid, SUBTYPE_FLOAT)) {
        encoding = Encoding.PCM_FLOAT;
      } else {
        throw new IOException("Unsupported extensible subtype");
      }

      // fmt base (16) + cbSize (2) + validBits (2) + channelMask (4) + GUID (16)
      int consumed = 16 + 2 + 2 + 4 + 16;
      int remaining = chunkSize - consumed;
      if (remaining < 0) {
        throw new IOException("fmt chunk too small for WAVE_FORMAT_EXTENSIBLE");
      }
      skipFully(in, remaining);
      return new AudioFormat(encoding, sampleRate, bitsPerSample, channels, blockAlign, false);
    } else {
      throw new IOException("Unsupported WAVE format: " + formatTag);
    }

    if (chunkSize < 16) {
      throw new IOException("fmt chunk too small");
    }
    skipFully(in, chunkSize - 16);

    return new AudioFormat(encoding, sampleRate, bitsPerSample, channels, blockAlign, false);
  }

  private static boolean matches(byte[] a, byte[] b) {
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  private static String readAscii(InputStream in, int len) throws IOException {
    byte[] bytes = in.readNBytes(len);
    if (bytes.length != len) {
      throw new EOFException();
    }
    return new String(bytes, StandardCharsets.US_ASCII);
  }

  private static void expectAscii(InputStream in, String expected) throws IOException {
    String actual = readAscii(in, expected.length());
    if (!expected.equals(actual)) {
      throw new IOException("Expected " + expected + ", got " + actual);
    }
  }

  private static int readShortLE(InputStream in) throws IOException {
    int b0 = in.read();
    int b1 = in.read();
    if ((b0 | b1) < 0) {
      throw new EOFException();
    }
    return b0 | (b1 << 8);
  }

  private static int readIntLE(InputStream in) throws IOException {
    int b0 = in.read();
    int b1 = in.read();
    int b2 = in.read();
    int b3 = in.read();

    if ((b0 | b1 | b2 | b3) < 0) {
      throw new EOFException();
    }

    return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
  }

  /**
   * Skips exactly {@code n} bytes from the input stream, throwing {@link EOFException} if the stream ends before the
   * requested number of bytes has been skipped.
   */
  private static void skipFully(InputStream in, long n) throws IOException {
    while (n > 0) {
      long skipped = in.skip(n);
      if (skipped <= 0) {
        if (in.read() < 0) {
          throw new EOFException();
        }
        skipped = 1;
      }
      n -= skipped;
    }
  }

  @Override
  public AudioFormat format() {
    return format;
  }

  @Override
  public int read() throws IOException {
    if (remaining <= 0) {
      return -1;
    }
    int value = in.read();
    if (value >= 0) {
      remaining--;
    }
    return value;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (remaining <= 0) {
      return -1;
    }

    int n = in.read(b, off, (int) Math.min(len, remaining));
    if (n > 0) {
      remaining -= n;
    }
    return n;
  }

  private record Header(AudioFormat format, long dataSize) {
  }
}
