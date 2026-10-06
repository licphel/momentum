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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@InternalApi
final class SimpleTagMap<T, V> extends SimpleTag<T> implements TagMap<T, V> {
  final Map<Identifier, V> rawMappings;
  final Map<Identifier, V> tagRefMappings;
  final Map<T, V> resolvedMappings = new LinkedHashMap<>();

  SimpleTagMap(Identifier key,
               Map<Identifier, V> rawMappings,
               Map<Identifier, V> tagRefMappings) {
    super(key, Set.copyOf(rawMappings.keySet()), Set.copyOf(tagRefMappings.keySet()));
    this.rawMappings = rawMappings;
    this.tagRefMappings = tagRefMappings;
  }

  @Override
  public @Nullable V map(T value) {
    return resolvedMappings.get(value);
  }

  @Override
  public Map<T, V> mappings() {
    return Collections.unmodifiableMap(resolvedMappings);
  }
}
