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

import io.viki.momentum.util.Identifier;
import io.viki.momentum.util.InternalApi;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@InternalApi
class SimpleTag<T> implements Tag<T> {
  final Identifier key;
  final Set<Identifier> idsWithoutRefs;
  final Set<Identifier> tagRefs;
  final Set<T> resolvedEntries = new HashSet<>();

  SimpleTag(Identifier key, Set<Identifier> idsWithoutRefs, Set<Identifier> tagRefs) {
    this.key = key;
    this.idsWithoutRefs = idsWithoutRefs;
    this.tagRefs = tagRefs;
  }

  @Override
  public Identifier key() {
    return key;
  }

  @SuppressWarnings("all")
  @Override
  public boolean contains(Object value) {
    return resolvedEntries.contains(value);
  }

  @Override
  public Collection<T> values() {
    return resolvedEntries;
  }

  @Override
  public Stream<T> stream() {
    return resolvedEntries.stream();
  }

  @Override
  public String toString() {
    return "Tag[" + key + ", entries=" + resolvedEntries.size() + "]";
  }
}
