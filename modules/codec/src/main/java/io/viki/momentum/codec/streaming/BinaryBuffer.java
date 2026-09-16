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

package io.viki.momentum.codec.streaming;

import io.viki.momentum.codec.Codec;
import io.viki.momentum.codec.nbt.DataType;
import io.viki.momentum.codec.nbt.NBT;
import org.jspecify.annotations.Nullable;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A cursor-based byte buffer for binary serialization and deserialization.
 *
 * <p>A {@code Buf} maintains independent read and write cursors over a backing
 * byte store. Data is written at the write cursor and read from the read cursor;
 * the region between them holds bytes that have been written but not yet consumed.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create a buffer via a static factory such as {@link #heap(int)}
 *       or {@link #wrap(byte[])}
 *   <li>Write data with {@code writeXxx} methods — the write cursor advances automatically
 *   <li>Read data with {@code readXxx} methods — the read cursor advances automatically
 *   <li>Call {@link #clear()} to reset both cursors and reuse the buffer
 *   <li>Call {@link #close()} to release resources when done
 * </ol>
 *
 * <h3>Byte order</h3>
 * <p>Multibyte reads and writes use the buffer's configured {@link ByteOrder}.
 *
 * <p>This class is <strong>not</strong> thread-safe.
 *
 * @see HeapBinaryBuffer
 */
public abstract class BinaryBuffer implements AutoCloseable {
  /** Default initial capacity: {@value} bytes. */
  public static final int DEFAULT_CAPACITY = 128;

  /** Current read-cursor position. */
  protected int readerIndex;
  /** Current write-cursor position. */
  protected int writerIndex;

  /**
   * Creates a buffer with both cursors at zero.
   */
  protected BinaryBuffer() {
    this.readerIndex = 0;
    this.writerIndex = 0;
  }

  /**
   * Creates a new heap-backed buffer with the given initial capacity.
   *
   * <p>Byte order is little endian by default.
   *
   * @param capacity initial capacity in bytes
   * @return a new heap-backed buffer
   */
  public static BinaryBuffer heap(int capacity) {
    return new HeapBinaryBuffer(capacity);
  }

  /**
   * Creates a new heap-backed buffer with {@link #DEFAULT_CAPACITY default capacity}.
   *
   * <p>Byte order is little endian by default.
   *
   * @return a new heap-backed buffer
   */
  public static BinaryBuffer heap() {
    return new HeapBinaryBuffer(DEFAULT_CAPACITY);
  }

  /**
   * Wraps an existing byte array into a buffer with all data pre-written.
   *
   * <p>The resulting buffer has its write cursor at the end of the array,
   * making all bytes immediately readable.
   *
   * <p>Byte order is little endian by default.
   *
   * @param data the byte array to wrap
   * @return a buffer backed by the given data
   */
  public static BinaryBuffer wrap(byte[] data) {
    return new HeapBinaryBuffer(data);
  }

  /**
   * Ensures the backing store has at least {@code minCapacity} bytes of total capacity.
   * Called automatically when an expandable buffer would overflow.
   *
   * @param minCapacity the required minimum capacity in bytes
   */
  protected abstract void grow(int minCapacity);

  /**
   * Returns the total capacity of this buffer in bytes.
   *
   * @return the current byte capacity
   */
  public abstract int capacity();

  /**
   * Returns the current write-cursor position.
   *
   * @return the writer index
   */
  public int writerIndex() {
    return writerIndex;
  }

  /**
   * Sets the write cursor to the given position.
   *
   * @param index the new write-cursor position, must be in {@code [readerIndex, capacity]}
   * @throws IndexOutOfBoundsException if {@code index} is outside the valid range
   */
  public void writerIndex(int index) {
    if (index < readerIndex || index > capacity()) {
      throw new IndexOutOfBoundsException(String.format("writerIndex=%d out of range [readerIndex=%d, capacity=%d]",
          index, readerIndex, capacity()));
    }
    writerIndex = index;
  }

  /**
   * Returns the current read-cursor position.
   *
   * @return the reader index
   */
  public int readerIndex() {
    return readerIndex;
  }

  /**
   * Sets the read cursor to the given position.
   *
   * @param index the new read-cursor position, must be in {@code [0, writerIndex]}
   * @throws IndexOutOfBoundsException if {@code index} is outside the valid range
   */
  public void readerIndex(int index) {
    if (index < 0 || index > writerIndex) {
      throw new IndexOutOfBoundsException(String.format("readerIndex=%d out of range [0, writerIndex=%d]", index,
          writerIndex));
    }
    readerIndex = index;
  }

  /**
   * Returns the number of bytes available for reading.
   *
   * @return the readable byte count ({@code writerIndex - readerIndex})
   */
  public int readableBytes() {
    return writerIndex - readerIndex;
  }

  /**
   * Returns the number of bytes that can be written without expanding the buffer.
   *
   * @return the writable byte count ({@code capacity - writerIndex})
   */
  public int writableBytes() {
    return capacity() - writerIndex;
  }

  /**
   * Returns the byte order used for multibyte reads and writes.
   *
   * @return the current byte order
   */
  public abstract ByteOrder order();

  /**
   * Sets the byte order for multibyte reads and writes.
   *
   * @param order the byte order to use
   */
  public abstract void order(ByteOrder order);

  /**
   * Resets both cursors to zero, effectively clearing the buffer without zeroing memory.
   */
  public void clear() {
    readerIndex = 0;
    writerIndex = 0;
  }

  /**
   * Writes a single byte at the current write-cursor position and advances the cursor by one.
   *
   * @param value the byte to write
   * @throws IndexOutOfBoundsException if the buffer is full and not expandable
   */
  public abstract void write(byte value);

  /**
   * Writes a {@code boolean} as a single byte ({@code 1} for {@code true}, {@code 0} for {@code false}).
   *
   * @param value the boolean to write
   * @throws IndexOutOfBoundsException if the buffer is full and not expandable
   */
  public void writeBoolean(boolean value) {
    write((byte) (value ? 1 : 0));
  }

  /**
   * Writes a {@code short} as two bytes in the buffer's byte order, advancing the cursor by two.
   *
   * @param value the short to write
   * @throws IndexOutOfBoundsException if fewer than 2 bytes are writable
   */
  public abstract void writeShort(short value);

  /**
   * Writes an {@code int} as four bytes in the buffer's byte order, advancing the cursor by four.
   *
   * @param value the int to write
   * @throws IndexOutOfBoundsException if fewer than 4 bytes are writable
   */
  public abstract void writeInt(int value);

  /**
   * Writes a {@code long} as eight bytes in the buffer's byte order, advancing the cursor by eight.
   *
   * @param value the long to write
   * @throws IndexOutOfBoundsException if fewer than 8 bytes are writable
   */
  public abstract void writeLong(long value);

  /**
   * Writes a {@code float} as four bytes in the buffer's byte order, advancing the cursor by four.
   *
   * @param value the float to write
   * @throws IndexOutOfBoundsException if fewer than 4 bytes are writable
   */
  public void writeFloat(float value) {
    writeInt(Float.floatToRawIntBits(value));
  }

  /**
   * Writes a {@code double} as eight bytes in the buffer's byte order, advancing the cursor by eight.
   *
   * @param value the double to write
   * @throws IndexOutOfBoundsException if fewer than 8 bytes are writable
   */
  public void writeDouble(double value) {
    writeLong(Double.doubleToRawLongBits(value));
  }

  /**
   * Writes a 32-bit integer in variable-length format.
   *
   * <p>Each byte uses 7 bits for data with the most significant bit set as a continuation
   * flag. Small values use fewer bytes, up to a maximum of 5.
   *
   * @param value the 32-bit integer to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeVarInt(int value) {
    ensureWritable(5);
    int v = value;
    while ((v & ~0x7F) != 0) {
      write((byte) ((v & 0x7F) | 0x80));
      v >>>= 7;
    }
    write((byte) v);
  }

  /**
   * Writes a signed 32-bit integer in ZigZag-encoded variable-length format,
   * optimal for small-magnitude signed values.
   *
   * <p>ZigZag encoding maps negative values to positive integers so that small
   * negative numbers also encode compactly.
   *
   * @param value the signed 32-bit integer to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeZigZagInt(int value) {
    writeVarInt((value << 1) ^ (value >> 31));
  }

  /**
   * Writes a 64-bit integer in variable-length format.
   *
   * <p>Small values use fewer bytes, up to a maximum of 10.
   *
   * @param value the 64-bit integer to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeVarLong(long value) {
    ensureWritable(10);
    long v = value;
    while ((v & ~0x7FL) != 0) {
      write((byte) ((v & 0x7F) | 0x80));
      v >>>= 7;
    }
    write((byte) v);
  }

  /**
   * Writes a signed 64-bit integer in ZigZag-encoded variable-length format,
   * optimal for small-magnitude signed values such as timestamps and deltas.
   *
   * @param value the signed 64-bit integer to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeZigZagLong(long value) {
    writeVarLong((value << 1) ^ (value >> 63));
  }

  /**
   * Writes all bytes from the given array at the current write-cursor position
   * and advances the cursor by the array's length.
   *
   * @param src the bytes to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeBytes(byte[] src) {
    writeBytes(src, 0, src.length);
  }

  /**
   * Writes {@code length} bytes from {@code src} starting at {@code srcOffset}
   * and advances the write cursor by {@code length}.
   *
   * @param src       the source byte array
   * @param srcOffset starting offset within {@code src}
   * @param length    number of bytes to write
   * @throws IndexOutOfBoundsException if the source range is invalid
   *                                   or insufficient writable space remains
   */
  public abstract void writeBytes(byte[] src, int srcOffset, int length);

  /**
   * Transfers {@code length} bytes from {@code src}'s readable region into this buffer,
   * advancing both buffers' respective cursors.
   *
   * @param src    the source buffer to read from
   * @param length number of bytes to transfer
   * @throws IndexOutOfBoundsException if {@code src} has fewer than {@code length}
   *                                   readable bytes or this buffer has insufficient
   *                                   writable space
   */
  public abstract void writeBuf(BinaryBuffer src, int length);

  /**
   * Encodes a string using the given charset and writes it with a 4-byte
   * length prefix in the buffer's byte order.
   *
   * @param s       the string to write
   * @param charset the charset to use for encoding
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeString(String s, Charset charset) {
    byte[] encoded = s.getBytes(charset);
    writeInt(encoded.length);
    writeBytes(encoded);
  }

  /**
   * Encodes a string as UTF-8 and writes it with a 4-byte length prefix.
   *
   * @param s the string to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeUTF8(String s) {
    writeString(s, StandardCharsets.UTF_8);
  }

  /**
   * Encodes a string as ASCII and writes it with a 4-byte length prefix.
   *
   * @param s the string to write
   * @throws IndexOutOfBoundsException if insufficient writable space remains
   */
  public void writeASCII(String s) {
    writeString(s, StandardCharsets.US_ASCII);
  }

  /**
   * Serializes a {@link NBT} into this buffer.
   *
   * @param nbt the nbt to write
   */
  @SuppressWarnings("unchecked")
  public void writeNBT(NBT nbt) {
    ((Codec<NBT>) nbt.codec()).serialize(nbt, this);
  }

  /**
   * Writes a {@link UUID} as two {@code long} values (most significant bits first)
   * in the buffer's byte order.
   *
   * @param uuid the UUID to write
   * @throws IndexOutOfBoundsException if fewer than 16 bytes are writable
   */
  public void writeUUID(UUID uuid) {
    writeLong(uuid.getMostSignificantBits());
    writeLong(uuid.getLeastSignificantBits());
  }

  /**
   * Reads a single byte at the current read-cursor position and advances the cursor by one.
   *
   * @return the byte value
   * @throws IndexOutOfBoundsException if no bytes are available for reading
   */
  public abstract byte read();

  /**
   * Reads a {@code boolean} as a single byte and advances the cursor by one.
   *
   * @return {@code true} if the byte value is {@code 1}, {@code false} otherwise
   * @throws IndexOutOfBoundsException if no bytes are available for reading
   */
  public boolean readBoolean() {
    return read() == 1;
  }

  /**
   * Reads a {@code short} as two bytes in the buffer's byte order and advances the cursor by two.
   *
   * @return the short value
   * @throws IndexOutOfBoundsException if fewer than 2 bytes are available
   */
  public abstract short readShort();

  /**
   * Reads an {@code int} as four bytes in the buffer's byte order and advances the cursor by four.
   *
   * @return the int value
   * @throws IndexOutOfBoundsException if fewer than 4 bytes are available
   */
  public abstract int readInt();

  /**
   * Reads a {@code long} as eight bytes in the buffer's byte order and advances the cursor by eight.
   *
   * @return the long value
   * @throws IndexOutOfBoundsException if fewer than 8 bytes are available
   */
  public abstract long readLong();

  /**
   * Reads a {@code float} as four bytes in the buffer's byte order and advances the cursor by four.
   *
   * @return the float value
   * @throws IndexOutOfBoundsException if fewer than 4 bytes are available
   */
  public float readFloat() {
    return Float.intBitsToFloat(readInt());
  }

  /**
   * Reads a {@code double} as eight bytes in the buffer's byte order and advances the cursor by eight.
   *
   * @return the double value
   * @throws IndexOutOfBoundsException if fewer than 8 bytes are available
   */
  public double readDouble() {
    return Double.longBitsToDouble(readLong());
  }

  /**
   * Reads a 32-bit integer in variable-length format.
   *
   * @return the decoded 32-bit value
   * @throws IndexOutOfBoundsException if insufficient readable bytes remain
   * @throws RuntimeException          if the encoding exceeds the maximum length
   */
  public int readVarInt() {
    int result = 0;
    int shift = 0;
    byte b;
    do {
      ensureReadable(1);
      b = read();
      result |= (b & 0x7F) << shift;
      shift += 7;
      if (shift > 35) {
        throw new RuntimeException("VarInt too long, exceeds 5 bytes");
      }
    } while ((b & 0x80) != 0);
    return result;
  }

  /**
   * Reads a ZigZag-encoded 32-bit integer in variable-length format.
   *
   * @return the decoded signed 32-bit value
   * @throws IndexOutOfBoundsException if insufficient readable bytes remain
   */
  public int readZigZagInt() {
    int encoded = readVarInt();
    return (encoded >>> 1) ^ -(encoded & 1);
  }

  /**
   * Reads a 64-bit integer in variable-length format.
   *
   * @return the decoded 64-bit value
   * @throws IndexOutOfBoundsException if insufficient readable bytes remain
   * @throws RuntimeException          if the encoding exceeds the maximum length
   */
  public long readVarLong() {
    long result = 0;
    int shift = 0;
    byte b;
    do {
      ensureReadable(1);
      b = read();
      result |= ((long) (b & 0x7F)) << shift;
      shift += 7;
      if (shift > 70) {
        throw new RuntimeException("VarLong too long, exceeds 10 bytes");
      }
    } while ((b & 0x80) != 0);
    return result;
  }

  /**
   * Reads a ZigZag-encoded 64-bit integer in variable-length format.
   *
   * @return the decoded signed 64-bit value
   * @throws IndexOutOfBoundsException if insufficient readable bytes remain
   */
  public long readZigZagLong() {
    long encoded = readVarLong();
    return (encoded >>> 1) ^ -(encoded & 1);
  }

  /**
   * Reads {@code length} bytes from the current read-cursor position into a new
   * byte array and advances the cursor by {@code length}.
   *
   * @param length the number of bytes to read
   * @return a newly allocated byte array containing the read bytes
   * @throws IndexOutOfBoundsException if fewer than {@code length} bytes are available
   */
  public byte[] readBytes(int length) {
    byte[] dst = new byte[length];
    readBytes(dst, 0, length);
    return dst;
  }

  /**
   * Reads {@code length} bytes from the current read-cursor position into
   * {@code dst} starting at {@code dstOffset}, and advances the cursor by
   * {@code length}.
   *
   * @param dst       the destination byte array
   * @param dstOffset starting offset within {@code dst}
   * @param length    number of bytes to read
   * @throws IndexOutOfBoundsException if fewer than {@code length} bytes are available
   *                                   or the destination range is invalid
   */
  public abstract void readBytes(byte[] dst, int dstOffset, int length);

  /**
   * Reads {@code length} bytes from this buffer into {@code dst} at the current
   * write-cursor position, advancing both buffers' cursors.
   *
   * @param dst    the destination buffer to write into
   * @param length number of bytes to transfer
   * @throws IndexOutOfBoundsException if this buffer has fewer than {@code length}
   *                                   readable bytes or {@code dst} has insufficient
   *                                   writable space
   */
  public abstract void readBuf(BinaryBuffer dst, int length);

  /**
   * Reads a length-prefixed string from the current read-cursor position.
   *
   * <p>The 4-byte int prefix (in the buffer's byte order) encodes the byte length
   * of the encoded string.
   *
   * @param charset the charset to use for decoding
   * @return the decoded string
   * @throws IndexOutOfBoundsException if insufficient bytes are available
   */
  public String readString(Charset charset) {
    int byteLen = readInt();
    byte[] encoded = readBytes(byteLen);
    return new String(encoded, charset);
  }

  /**
   * Reads a length-prefixed UTF-8 string from the current read-cursor position.
   *
   * @return the decoded string
   * @throws IndexOutOfBoundsException if insufficient bytes are available
   */
  public String readUTF8() {
    return readString(StandardCharsets.UTF_8);
  }

  /**
   * Reads a length-prefixed ASCII string from the current read-cursor position.
   *
   * @return the decoded string
   * @throws IndexOutOfBoundsException if insufficient bytes are available
   */
  public String readASCII() {
    return readString(StandardCharsets.US_ASCII);
  }

  /**
   * Deserializes a {@link NBT} from this buffer.
   *
   * @param type the data type of the NBT
   * @return the deserialized nbt
   */
  public NBT readNBT(DataType type) {
    return type.codec().deserialize(this);
  }

  /**
   * Reads a {@link UUID} as two {@code long} values in the buffer's byte order.
   *
   * @return the deserialized UUID
   * @throws IndexOutOfBoundsException if fewer than 16 bytes are available
   */
  public UUID readUUID() {
    long msb = readLong();
    long lsb = readLong();
    return new UUID(msb, lsb);
  }

  /**
   * Discards already-read bytes by compacting the readable region to the front.
   *
   * <p>After compaction, the read cursor is at zero and the write cursor is at
   * {@code readableBytes()}, freeing space at the end for more writes.
   */
  public abstract void compact();

  /**
   * Copies all readable bytes into a new byte array.
   *
   * @return a snapshot of the readable bytes
   */
  public abstract byte[] copiedArray();

  /**
   * Returns the raw backing array.
   *
   * <p>The returned array may be longer than the logical data. Modifying it
   * directly is discouraged. May return {@code null} for non-heap buffers.
   *
   * @return the raw backing array, or {@code null} if the buffer is not heap-backed
   */
  public abstract byte @Nullable [] backingArray();

  /**
   * Ensures at least {@code needed} bytes are writable, expanding the backing
   * store if necessary.
   *
   * @param needed the number of bytes required
   * @throws IndexOutOfBoundsException if the buffer is not expandable and space
   *                                   is insufficient
   */
  protected void ensureWritable(int needed) {
    if (writableBytes() < needed) {
      grow(writerIndex + needed);
    }
  }

  /**
   * Ensures at least {@code needed} bytes are readable.
   *
   * @param needed the number of bytes required
   * @throws IndexOutOfBoundsException if fewer than {@code needed} bytes are available
   */
  protected void ensureReadable(int needed) {
    if (readableBytes() < needed) {
      throw new IndexOutOfBoundsException(needed + " bytes required");
    }
  }

  @Override
  public abstract void close();
}
