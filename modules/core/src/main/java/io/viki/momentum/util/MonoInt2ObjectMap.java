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

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * A map keyed by dense, monotonically increasing integer ids in the range
 * {@code 0 <= id <= N}, backed by an array for O(1) direct indexing instead of
 * hashing.
 *
 * <p>New ids are allocated by {@link #add(Object)}; the id space grows as needed
 * and is never shrunk. Removed slots are set to {@code null} and their ids are
 * not reused.
 *
 * @param <T> the value type
 */
public final class MonoInt2ObjectMap<T> {
  private @Nullable Object[] elements;
  private int size;

  /**
   * Creates an empty map.
   *
   * @param initialCapacity the initial backing array capacity
   */
  public MonoInt2ObjectMap(int initialCapacity) {
    this.elements = new Object[Math.max(initialCapacity, 1)];
  }


  /**
   * Creates an empty map with default capacity.
   */
  public MonoInt2ObjectMap() {
    this(16);
  }

  /**
   * Allocates the next id and stores the value under it.
   *
   * @param value the value to store
   * @return the allocated id
   */
  public int add(T value) {
    int id = size;
    set(id, value);
    return id;
  }

  /**
   * Returns the value at the given id.
   *
   * @param id the id
   * @return the value, or {@code null} if absent or out of range
   */
  @SuppressWarnings("unchecked")
  public @Nullable T get(int id) {
    if (id < 0 || id >= elements.length) {
      return null;
    }
    return (T) elements[id];
  }

  /**
   * Stores the value at the given id, growing the backing array if needed.
   *
   * @param id the id, must be non-negative
   * @param value the value to store
   * @throws IndexOutOfBoundsException if {@code id < 0}
   */
  public void set(int id, T value) {
    if (id < 0) {
      throw new IndexOutOfBoundsException("Negative id: " + id);
    }
    if (id >= elements.length) {
      grow(id + 1);
    }
    if (elements[id] == null) {
      size++;
    }
    elements[id] = value;
  }

  /**
   * Removes the value at the given id. The id is not reused.
   *
   * @param id the id
   * @return the previous value, or {@code null} if absent
   */
  @SuppressWarnings("unchecked")
  public @Nullable T remove(int id) {
    if (id < 0 || id >= elements.length) {
      return null;
    }
    T old = (T) elements[id];
    if (old != null) {
      elements[id] = null;
      size--;
    }
    return old;
  }

  /**
   * Returns whether a non-null value is stored at the given id.
   *
   * @param id the id
   * @return whether present
   */
  public boolean contains(int id) {
    return id >= 0 && id < elements.length && elements[id] != null;
  }

  /**
   * Returns the number of non-null entries.
   *
   * @return the size
   */
  public int size() {
    return size;
  }

  /**
   * Returns the current exclusive upper bound of the id space, i.e. the next id
   * that {@link #add(Object)} would allocate if no slots were removed.
   *
   * @return the id bound
   */
  public int bound() {
    return elements.length;
  }

  private void grow(int minCapacity) {
    int oldCapacity = elements.length;
    int newCapacity = Math.max(minCapacity, oldCapacity + (oldCapacity >> 1));
    elements = Arrays.copyOf(elements, newCapacity);
  }
}