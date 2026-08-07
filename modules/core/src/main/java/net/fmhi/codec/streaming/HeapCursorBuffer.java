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

package net.fmhi.codec.streaming;

import net.fmhi.util.InternalApi;

import java.nio.ByteOrder;

@InternalApi
final class HeapCursorBuffer extends CursorBuffer {
  private byte[] data;
  private boolean bigEndian; // LE by default

  public HeapCursorBuffer(int capacity) {
    data = new byte[capacity];
  }

  public HeapCursorBuffer(byte[] initial) {
    data = initial;
    writerIndex = data.length;
  }

  @Override
  protected void grow(int minCapacity) {
    int newLen = (int) Math.max(data.length * 2L, minCapacity);
    byte[] newData = new byte[newLen];
    System.arraycopy(data, 0, newData, 0, writerIndex);
    data = newData;
  }

  @Override
  public int capacity() {
    return data.length;
  }

  @Override
  public ByteOrder order() {
    return bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
  }

  @Override
  public void order(ByteOrder order) {
    bigEndian = order == ByteOrder.BIG_ENDIAN;
  }

  @Override
  public void write(byte value) {
    ensureWritable(1);
    data[writerIndex++] = value;
  }

  @Override
  public void writeShort(short value) {
    ensureWritable(2);
    int wi = writerIndex;
    if (bigEndian) {
      data[wi] = (byte) (value >> 8);
      data[wi + 1] = (byte) value;
    } else {
      data[wi] = (byte) value;
      data[wi + 1] = (byte) (value >> 8);
    }
    writerIndex = wi + 2;
  }

  @Override
  public void writeInt(int value) {
    ensureWritable(4);
    int wi = writerIndex;
    if (bigEndian) {
      data[wi] = (byte) (value >> 24);
      data[wi + 1] = (byte) (value >> 16);
      data[wi + 2] = (byte) (value >> 8);
      data[wi + 3] = (byte) value;
    } else {
      data[wi] = (byte) value;
      data[wi + 1] = (byte) (value >> 8);
      data[wi + 2] = (byte) (value >> 16);
      data[wi + 3] = (byte) (value >> 24);
    }
    writerIndex = wi + 4;
  }

  @Override
  public void writeLong(long value) {
    ensureWritable(8);
    int wi = writerIndex;
    if (bigEndian) {
      data[wi] = (byte) (value >> 56);
      data[wi + 1] = (byte) (value >> 48);
      data[wi + 2] = (byte) (value >> 40);
      data[wi + 3] = (byte) (value >> 32);
      data[wi + 4] = (byte) (value >> 24);
      data[wi + 5] = (byte) (value >> 16);
      data[wi + 6] = (byte) (value >> 8);
      data[wi + 7] = (byte) value;
    } else {
      data[wi] = (byte) value;
      data[wi + 1] = (byte) (value >> 8);
      data[wi + 2] = (byte) (value >> 16);
      data[wi + 3] = (byte) (value >> 24);
      data[wi + 4] = (byte) (value >> 32);
      data[wi + 5] = (byte) (value >> 40);
      data[wi + 6] = (byte) (value >> 48);
      data[wi + 7] = (byte) (value >> 56);
    }
    writerIndex = wi + 8;
  }

  @Override
  public void writeBytes(byte[] src, int srcOffset, int length) {
    ensureWritable(length);
    System.arraycopy(src, srcOffset, data, writerIndex, length);
    writerIndex += length;
  }

  @Override
  public void writeBuf(CursorBuffer src, int length) {
    src.ensureReadable(length);
    ensureWritable(length);
    src.readBytes(data, writerIndex, length);
    writerIndex += length;
  }

  @Override
  public byte read() {
    ensureReadable(1);
    return data[readerIndex++];
  }

  @Override
  public short readShort() {
    ensureReadable(2);
    int ri = readerIndex;
    short v;
    if (bigEndian) {
      v = (short) ((data[ri] << 8)
          | (data[ri + 1] & 0xFF));
    } else {
      v = (short) ((data[ri] & 0xFF)
          | (data[ri + 1] << 8));
    }
    readerIndex = ri + 2;
    return v;
  }

  @Override
  public int readInt() {
    ensureReadable(4);
    int ri = readerIndex;
    int v;
    if (bigEndian) {
      v = (data[ri] << 24)
          | ((data[ri + 1] & 0xFF) << 16)
          | ((data[ri + 2] & 0xFF) << 8)
          | (data[ri + 3] & 0xFF);
    } else {
      v = (data[ri] & 0xFF)
          | ((data[ri + 1] & 0xFF) << 8)
          | ((data[ri + 2] & 0xFF) << 16)
          | (data[ri + 3] << 24);
    }
    readerIndex = ri + 4;
    return v;
  }

  @Override
  public long readLong() {
    ensureReadable(8);
    int ri = readerIndex;
    long v;
    if (bigEndian) {
      v = ((long) data[ri] << 56)
          | ((data[ri + 1] & 0xFFL) << 48)
          | ((data[ri + 2] & 0xFFL) << 40)
          | ((data[ri + 3] & 0xFFL) << 32)
          | ((data[ri + 4] & 0xFFL) << 24)
          | ((data[ri + 5] & 0xFFL) << 16)
          | ((data[ri + 6] & 0xFFL) << 8)
          | (data[ri + 7] & 0xFFL);
    } else {
      v = (data[ri] & 0xFFL)
          | ((data[ri + 1] & 0xFFL) << 8)
          | ((data[ri + 2] & 0xFFL) << 16)
          | ((data[ri + 3] & 0xFFL) << 24)
          | ((data[ri + 4] & 0xFFL) << 32)
          | ((data[ri + 5] & 0xFFL) << 40)
          | ((data[ri + 6] & 0xFFL) << 48)
          | ((long) data[ri + 7] << 56);
    }
    readerIndex = ri + 8;
    return v;
  }

  @Override
  public void readBytes(byte[] dst, int dstOffset, int length) {
    ensureReadable(length);
    System.arraycopy(data, readerIndex, dst, dstOffset, length);
    readerIndex += length;
  }

  @Override
  public void readBuf(CursorBuffer dst, int length) {
    ensureReadable(length);
    dst.writeBytes(data, readerIndex, length);
    readerIndex += length;
  }

  @Override
  public void compact() {
    int readable = readableBytes();
    if (readable == 0) {
      readerIndex = 0;
      writerIndex = 0;
      return;
    }
    if (readerIndex > 0) {
      System.arraycopy(data, readerIndex, data, 0, readable);
    }
    writerIndex = readable;
    readerIndex = 0;
  }

  @Override
  public byte[] copiedArray() {
    byte[] array = new byte[readableBytes()];
    System.arraycopy(data, readerIndex, array, 0, readableBytes());
    return array;
  }

  @Override
  public byte[] backingArray() {
    return data;
  }

  @Override
  public void close() {
    data = new byte[0];
  }
}
