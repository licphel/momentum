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

package io.viki.momentum.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.function.BiConsumer;

/**
 * A fixed-size two-dimensional grid that stores palette-encoded values with
 * optional per-cell metadata.
 *
 * <p>Values are stored indirectly through a {@link Palette}, reducing memory
 * overhead when many cells share the same value. Each cell may also carry
 * user-defined metadata bytes for auxiliary data like flags or timestamps.
 *
 * <p>Coordinates are automatically wrapped using a power-of-two mask,
 * eliminating bounds checks on every access.
 *
 * @param <T> the value type, which must provide integer identities via {@link PaletteCandidate}
 */
public final class Grid<T extends PaletteCandidate> {
  private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
  private static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
  private final int size;
  private final int mask;
  private final int metaBytes;
  private final int stride;
  private final byte[] storage;
  private final Palette<T> palette;

  /**
   * Creates a grid of size 2<sup>{@code power}</sup> with no per-cell metadata.
   *
   * @param palette the candidate palette
   * @param size    the width/height of the map (must be power of 2, positive)
   * @throws IllegalArgumentException if {@code power} is out of range
   */
  public Grid(Palette<T> palette, int size) {
    this(palette, size, 0);
  }

  /**
   * Creates a grid of size 2<sup>{@code power}</sup> with the given number of
   * metadata bytes per cell.
   *
   * @param palette   the candidate palette
   * @param size      the width/height of the map (must be power of 2, positive)
   * @param metaBytes the number of metadata bytes per cell (0–255)
   * @throws IllegalArgumentException if either parameter is out of range
   */
  public Grid(Palette<T> palette, int size, int metaBytes) {
    if ((size & (size - 1)) != 0 || size <= 0) {
      throw new IllegalArgumentException("size must be power of 2, positive");
    }
    if (metaBytes < 0 || metaBytes > 255) {
      throw new IllegalArgumentException("metaBytes must be 0-255");
    }

    this.size = size;
    this.mask = size - 1;
    this.metaBytes = metaBytes;
    this.stride = 4 + metaBytes;
    this.storage = new byte[size * size * stride];
    this.palette = palette;
  }

  private int offset(int x, int y) {
    return ((x & mask) * size + (y & mask)) * stride;
  }

  /**
   * Returns the palette mapping between values and their dense identifiers.
   *
   * @return the palette
   */
  public Palette<T> palette() {
    return palette;
  }

  /**
   * Returns the value at the given coordinates.
   *
   * <p>Coordinates are automatically wrapped by the grid mask.
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   * @return the value at the coordinates
   */
  public T get(int x, int y) {
    int off = offset(x, y);
    int id = (int) INT_HANDLE.get(storage, off);
    return palette.get(id);
  }

  /**
   * Returns the raw palette identifier at the given coordinates without
   * resolving it to a value.
   *
   * @param x the x-coordinate
   * @param y the y-coordinate
   * @return the palette identifier
   */
  public int getId(int x, int y) {
    int off = offset(x, y);
    return (int) INT_HANDLE.get(storage, off);
  }

  /**
   * Copies the metadata bytes for the cell at the given coordinates into the
   * destination array.
   *
   * <p>Does nothing if this grid has no metadata.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param dst       the destination array
   * @param dstOffset the starting offset in the destination array
   */
  public void getMeta(int x, int y, byte[] dst, int dstOffset) {
    if (metaBytes == 0) {
      return;
    }
    int off = offset(x, y) + 4;
    System.arraycopy(storage, off, dst, dstOffset, metaBytes);
  }

  /**
   * Returns a single metadata byte for the cell at the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the byte index within the metadata block
   * @return the metadata byte
   * @throws IndexOutOfBoundsException if {@code metaIndex} is out of range
   */
  public byte getMetaByte(int x, int y, int metaIndex) {
    if (metaIndex < 0 || metaIndex >= metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    return storage[offset(x, y) + 4 + metaIndex];
  }

  /**
   * Reads two metadata bytes as a little-endian {@code short} for the cell at
   * the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the starting byte index within the metadata block
   * @return the metadata value as a short
   * @throws IndexOutOfBoundsException if the range exceeds available metadata
   */
  public short getMetaShort(int x, int y, int metaIndex) {
    if (metaIndex < 0 || metaIndex + 2 > metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    int off = offset(x, y) + 4 + metaIndex;
    return (short) SHORT_HANDLE.get(storage, off);
  }

  /**
   * Reads four metadata bytes as a little-endian {@code int} for the cell at
   * the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the starting byte index within the metadata block
   * @return the metadata value as an int
   * @throws IndexOutOfBoundsException if the range exceeds available metadata
   */
  public int getMetaInt(int x, int y, int metaIndex) {
    if (metaIndex < 0 || metaIndex + 4 > metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    int off = offset(x, y) + 4 + metaIndex;
    return (int) INT_HANDLE.get(storage, off);
  }

  /**
   * Stores a value at the given coordinates by recording its palette identifier.
   *
   * <p>Coordinates are automatically wrapped by the grid mask.
   *
   * @param x     the x-coordinate
   * @param y     the y-coordinate
   * @param value the value to store
   */
  public void set(int x, int y, T value) {
    int id = value.identity();
    int off = offset(x, y);
    INT_HANDLE.set(storage, off, id);
  }

  /**
   * Stores a raw palette identifier at the given coordinates, bypassing the
   * palette lookup.
   *
   * @param x  the x-coordinate
   * @param y  the y-coordinate
   * @param id the palette identifier
   */
  public void setId(int x, int y, int id) {
    int off = offset(x, y);
    INT_HANDLE.set(storage, off, id);
  }

  /**
   * Writes metadata bytes for the cell at the given coordinates from the source
   * array.
   *
   * <p>Does nothing if this grid has no metadata.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param src       the source array
   * @param srcOffset the starting offset in the source array
   */
  public void setMeta(int x, int y, byte[] src, int srcOffset) {
    if (metaBytes == 0) {
      return;
    }
    int off = offset(x, y) + 4;
    System.arraycopy(src, srcOffset, storage, off, metaBytes);
  }

  /**
   * Writes a single metadata byte for the cell at the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the byte index within the metadata block
   * @param value     the byte to write
   * @throws IndexOutOfBoundsException if {@code metaIndex} is out of range
   */
  public void setMetaByte(int x, int y, int metaIndex, byte value) {
    if (metaIndex < 0 || metaIndex >= metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    storage[offset(x, y) + 4 + metaIndex] = value;
  }

  /**
   * Writes two bytes as a little-endian {@code short} into the metadata block
   * for the cell at the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the starting byte index within the metadata block
   * @param value     the short value to write
   * @throws IndexOutOfBoundsException if the range exceeds available metadata
   */
  public void setMetaShort(int x, int y, int metaIndex, short value) {
    if (metaIndex < 0 || metaIndex + 2 > metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    int off = offset(x, y) + 4 + metaIndex;
    SHORT_HANDLE.set(storage, off, value);
  }

  /**
   * Writes four bytes as a little-endian {@code int} into the metadata block
   * for the cell at the given coordinates.
   *
   * @param x         the x-coordinate
   * @param y         the y-coordinate
   * @param metaIndex the starting byte index within the metadata block
   * @param value     the int value to write
   * @throws IndexOutOfBoundsException if the range exceeds available metadata
   */
  public void setMetaInt(int x, int y, int metaIndex, int value) {
    if (metaIndex < 0 || metaIndex + 4 > metaBytes) {
      throw new IndexOutOfBoundsException("metaIndex: " + metaIndex);
    }
    int off = offset(x, y) + 4 + metaIndex;
    INT_HANDLE.set(storage, off, value);
  }

  /**
   * Invokes the given action for every cell coordinate in the grid.
   *
   * @param action the action to invoke, receiving {@code (x, y)} for each cell
   */
  public void forEach(BiConsumer<Integer, Integer> action) {
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        action.accept(x, y);
      }
    }
  }

  /**
   * Invokes the given action for each cell, passing the cell's value and a
   * coordinate callback.
   *
   * @param action the action to invoke for each cell
   */
  public void forEachValue(BiConsumer<T, BiConsumer<Integer, Integer>> action) {
    // 简化版，实际使用可以传递更多信息
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        action.accept(get(x, y), (dx, dy) -> {
        });
      }
    }
  }

  /**
   * Copies all cells with non-default values from this grid into the
   * destination grid.
   *
   * <p>The destination must have the same dimensions and metadata layout.
   * Palette entries are not transferred; the destination retains its own
   * palette.
   *
   * @param dest the destination grid
   * @throws IllegalArgumentException if the dimensions or metadata layout differ
   */
  public void copyTo(Grid<T> dest) {
    if (dest.size != this.size || dest.stride != this.stride) {
      throw new IllegalArgumentException("Size or stride mismatch");
    }

    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        int id = getId(x, y);
        if (id != 0) {
          dest.setId(x, y, id);
          if (metaBytes > 0) {
            int off = offset(x, y) + 4;
            int destOff = dest.offset(x, y) + 4;
            System.arraycopy(storage, off, dest.storage, destOff, metaBytes);
          }
        }
      }
    }
  }

  /**
   * Returns the raw storage buffer backing this grid.
   *
   * @return the byte array storing palette identifiers and metadata
   */
  public byte[] storage() {
    return storage;
  }

  /**
   * Returns the number of metadata bytes allocated per cell.
   *
   * @return the metadata byte count
   */
  public int metaBytes() {
    return metaBytes;
  }

  /**
   * Returns the grid dimension (2<sup>{@code power}</sup>).
   *
   * @return the number of cells per axis
   */
  public int size() {
    return size;
  }
}
