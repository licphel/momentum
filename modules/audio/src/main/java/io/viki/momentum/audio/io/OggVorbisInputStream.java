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

import de.jarnbjo.ogg.*;
import de.jarnbjo.vorbis.VorbisFormatException;
import de.jarnbjo.vorbis.VorbisStream;
import io.viki.momentum.audio.AudioEncoding;
import io.viki.momentum.audio.AudioFormat;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Decodes one Ogg/Vorbis logical stream to signed little-endian PCM.
 */
public final class OggVorbisInputStream extends AudioInputStream {
  private static final int DECODE_BUFFER_BYTES = 64 * 1024;
  private static final int MAX_EMPTY_DECODER_READS = 8;

  private final PhysicalOggStream oggStream;
  private final VorbisStream decoder;
  private final AudioFormat format;
  private final byte[] decoded;
  private final byte[] singleByte = new byte[1];
  private int decodedOffset;
  private int decodedLength;
  private boolean endOfStream;
  private boolean closed;

  /**
   * Opens an Ogg/Vorbis stream and reads its identification headers.
   *
   * @param source source Ogg stream
   * @throws IOException          if the source cannot be read
   * @throws AudioFormatException if the stream is not a single Vorbis stream
   */
  public OggVorbisInputStream(InputStream source) throws IOException, AudioFormatException {
    PhysicalOggStream physical = null;
    try {
      physical = new BasicStream(source);
      Collection<?> logicalStreams = physical.getLogicalStreams();
      if (logicalStreams.size() != 1) {
        throw new AudioFormatException("Ogg audio must contain exactly one logical stream");
      }

      Object candidate = logicalStreams.iterator().next();
      if (!(candidate instanceof LogicalOggStream logical)
          || !LogicalOggStream.FORMAT_VORBIS.equals(logical.getFormat())) {
        throw new AudioFormatException("Ogg stream does not contain Vorbis audio");
      }

      VorbisStream vorbis = new VorbisStream(logical);
      int channels = vorbis.getIdentificationHeader().getChannels();
      int sampleRate = vorbis.getIdentificationHeader().getSampleRate();
      int frameSize = channels * Short.BYTES;
      format = new AudioFormat(AudioEncoding.PCM_SIGNED, sampleRate, Short.SIZE,
          channels, frameSize, false);
      decoded = new byte[decodeBufferSize(frameSize)];
      oggStream = physical;
      decoder = vorbis;
    } catch (OggFormatException | VorbisFormatException exception) {
      closeAfterFailure(physical, exception);
      throw new AudioFormatException("Invalid Ogg Vorbis stream", exception);
    } catch (IOException | RuntimeException exception) {
      closeAfterFailure(physical, exception);
      throw exception;
    }
  }

  private static int decodeBufferSize(int frameSize) {
    int remainder = DECODE_BUFFER_BYTES % frameSize;
    int alignedSize = remainder == 0 ? DECODE_BUFFER_BYTES : DECODE_BUFFER_BYTES - remainder;
    return Math.max(alignedSize, frameSize);
  }

  private static void swapToLittleEndian(byte[] buffer, int length) {
    for (int index = 0; index < length; index += Short.BYTES) {
      byte high = buffer[index];
      buffer[index] = buffer[index + 1];
      buffer[index + 1] = high;
    }
  }

  private static void closeAfterFailure(@Nullable PhysicalOggStream stream, Throwable failure) {
    if (stream == null) {
      return;
    }
    try {
      stream.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  @Override
  public AudioFormat format() {
    return format;
  }

  @Override
  public int read() throws IOException {
    int count = read(singleByte, 0, 1);
    return count < 0 ? -1 : singleByte[0] & 0xFF;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    checkOpen();
    if (length == 0) {
      return 0;
    }
    if (offset < 0 || length < 0 || offset > buffer.length - length) {
      throw new IndexOutOfBoundsException("Invalid PCM read range: offset=" + offset
          + ", length=" + length + ", bufferLength=" + buffer.length);
    }

    int total = 0;
    while (total < length) {
      if (decodedOffset >= decodedLength && !fillDecoded()) {
        return total == 0 ? -1 : total;
      }
      int available = decodedLength - decodedOffset;
      int copied = Math.min(length - total, available);
      System.arraycopy(decoded, decodedOffset, buffer, offset + total, copied);
      decodedOffset += copied;
      total += copied;
    }
    return total;
  }

  @Override
  public long skip(long count) throws IOException {
    checkOpen();
    if (count <= 0) {
      return 0;
    }

    long skipped = 0;
    while (skipped < count) {
      if (decodedOffset >= decodedLength && !fillDecoded()) {
        break;
      }
      int available = decodedLength - decodedOffset;
      int amount = (int) Math.min(count - skipped, available);
      decodedOffset += amount;
      skipped += amount;
    }
    return skipped;
  }

  @Override
  public int available() throws IOException {
    checkOpen();
    return decodedLength - decodedOffset;
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    try {
      decoder.close();
    } catch (IOException exception) {
      failure = exception;
    }
    try {
      oggStream.close();
    } catch (IOException exception) {
      if (failure == null) {
        failure = exception;
      } else {
        failure.addSuppressed(exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private boolean fillDecoded() throws IOException {
    if (endOfStream) {
      return false;
    }
    int emptyReads = 0;
    while (true) {
      int count;
      try {
        count = decoder.readPcm(decoded, 0, decoded.length);
      } catch (EndOfOggStreamException exception) {
        endOfStream = true;
        return false;
      }
      if (count < 0) {
        endOfStream = true;
        return false;
      }
      if (count == 0) {
        if (++emptyReads >= MAX_EMPTY_DECODER_READS) {
          throw new AudioFormatException("Ogg Vorbis decoder produced no PCM data");
        }
        continue;
      }
      if (count % format.frameSize() != 0) {
        throw new AudioFormatException("Ogg Vorbis decoder returned an incomplete PCM frame");
      }
      swapToLittleEndian(decoded, count);
      decodedOffset = 0;
      decodedLength = count;
      return true;
    }
  }

  private void checkOpen() throws IOException {
    if (closed) {
      throw new IOException("Ogg Vorbis stream is closed");
    }
  }
}
