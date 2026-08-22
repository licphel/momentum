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

package net.fmhi.tag;

import net.fmhi.util.Identifier;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * A named, immutable grouping of registry entries.
 *
 * <p>Tags are loaded from data files and allow categorizing registry
 * entries. Tags can reference other tags; the resolved set is the union of direct
 * entries and all referenced tags.
 *
 * @param <T> the registry entry type
 */
public interface Tag<T> {
  /**
   * Returns the key identifying this tag.
   *
   * @return the tag key
   */
  Identifier key();

  /**
   * Returns whether this tag contains the given entry.
   *
   * <p>Takes transitive tag references into account.
   *
   * @param value the entry to test
   * @return {@code true} if the entry is in this tag
   */
  boolean contains(T value);

  /**
   * Returns the values of this tag.
   *
   * <p>Note that implementation might be quite slow.
   *
   * @return values of this tag
   */
  Collection<T> values();

  /**
   * Returns a stream of all entries in this tag.
   *
   * @return a stream of resolved entries
   */
  Stream<T> stream();
}
