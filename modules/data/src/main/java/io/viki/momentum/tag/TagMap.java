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

package io.viki.momentum.tag;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A tag that maps each entry to an associated value.
 *
 * <p>Unlike a plain {@link Tag}, which simply groups entries, a
 * {@code TagMap} associates a value of type {@code V} with each member.
 *
 * @param <T> the registry entry type
 * @param <V> the mapped value type
 */
public interface TagMap<T, V> extends Tag<T> {
  /**
   * Returns the value mapped to the given entry.
   *
   * @param value the entry to look up
   * @return the mapped value, or {@code null} if the entry is not in this mapping
   */
  @Nullable V map(T value);

  /**
   * Returns all resolved mappings from entries to values.
   *
   * @return an unmodifiable map of entries to values
   */
  Map<T, V> mappings();
}
