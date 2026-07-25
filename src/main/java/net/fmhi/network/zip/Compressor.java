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

package net.fmhi.network.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.*;

/**
 * Compression algorithms.
 *
 * <p>Each constant encapsulates a complete compression strategy — both the algorithm
 * and its implementation. Both algorithms are built on the JDK's {@link java.util.zip} package and require no extra
 * dependencies:
 *
 * <ul>
 *   <li>{@link #GZIP} — Gzip format (RFC 1952), DEFLATE with a header and CRC-32
 *       trailer. The most common compression format for files ({@code .gz}) and
 *       HTTP content encoding.</li>
 *   <li>{@link #DEFLATE} — raw DEFLATE (RFC 1951), no header or checksum. Smallest
 *       possible output for a given level. Use when the data format already provides
 *       integrity checks.</li>
 * </ul>
 *
 * @see CompressionLevel
 */
public enum Compressor {
  /**
   * Gzip compression (RFC 1952).
   *
   * <p>Wraps raw DEFLATE data with a 10-byte header and an 8-byte CRC-32 trailer.
   * This is the most common compression format for files ({@code .gz}) and HTTP content encoding
   * ({@code Content-Encoding: Gzip}). Use this when you need interoperability with external tools or when a checksum is
   * desirable.
   */
  GZIP {
    @Override
    byte[] compress(final byte[] input, final CompressionLevel level) {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      try {
        var Gzip = new GZIPOutputStream(stream) {{
          def.setLevel(convertLevel(level));
        }};
        Gzip.write(input);
        Gzip.finish();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return stream.toByteArray();
    }

    @Override
    byte[] decompress(final byte[] input) {
      try (GZIPInputStream Gzip = new GZIPInputStream(new ByteArrayInputStream(input))) {
        return Gzip.readAllBytes();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  },

  /**
   * Raw DEFLATE compression (RFC 1951).
   *
   * <p>Produces bare DEFLATE streams with no header, footer, or checksum. This is
   * the smallest possible output for a given compression level. Use this when the data format already provides
   * integrity checks (e.g., NBT with its own length prefixes) and the extra Gzip envelope would be redundant.
   */
  DEFLATE {
    @Override
    byte[] compress(final byte[] input, final CompressionLevel level) {
      Deflater deflater = new Deflater(convertLevel(level));
      deflater.setInput(input);
      deflater.finish();
      ByteArrayOutputStream stream = new ByteArrayOutputStream(input.length / 2);
      byte[] buf = new byte[8192];
      while (!deflater.finished()) {
        int n = deflater.deflate(buf);
        stream.write(buf, 0, n);
      }
      deflater.end();
      return stream.toByteArray();
    }

    @Override
    byte[] decompress(final byte[] input) {
      Inflater inflater = new Inflater();
      inflater.setInput(input);
      ByteArrayOutputStream stream = new ByteArrayOutputStream(input.length * 2);
      byte[] buf = new byte[8192];
      try {
        while (!inflater.finished()) {
          int n = inflater.inflate(buf);
          stream.write(buf, 0, n);
        }
      } catch (DataFormatException e) {
        throw new IllegalArgumentException("Invalid deflate data", e);
      } finally {
        inflater.end();
      }
      return stream.toByteArray();
    }
  };

  private static int convertLevel(CompressionLevel level) {
    return switch (level) {
      case NONE -> Deflater.NO_COMPRESSION;
      case FAST -> Deflater.BEST_SPEED;
      case DEFAULT -> Deflater.DEFAULT_COMPRESSION;
      case BEST -> Deflater.BEST_COMPRESSION;
    };
  }

  /**
   * Compresses a byte array with the given compression level.
   *
   * @param input the uncompressed data
   * @param level the compression level
   * @return the compressed data
   * @throws UncheckedIOException if an I/O error occurs during compression
   */
  abstract byte[] compress(byte[] input, CompressionLevel level);

  /**
   * Decompresses a byte array that was compressed with this algorithm.
   *
   * @param input the compressed data
   * @return the decompressed data
   * @throws IllegalArgumentException if the input is not valid for this algorithm
   */
  abstract byte[] decompress(byte[] input);
}
