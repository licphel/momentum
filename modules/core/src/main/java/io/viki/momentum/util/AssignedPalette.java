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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A default assignable implementation of {@link Palette}.
 *
 * @param <K> the key type stored in this palette
 */
public class AssignedPalette<K extends PaletteCandidate> implements Palette<K> {
  private final Map<K, Integer> forward = new HashMap<>();
  private final List<K> backward = new ArrayList<>();

  /**
   * Assigns the next sequential ID to the given object.
   *
   * @param map the object to register
   */
  public void assign(K map) {
    int id = backward.size();
    forward.put(map, id);
    backward.add(map);
  }

  @Override
  public int searchIndex(K map) {
    Integer id = forward.get(map);
    return id != null ? id : -1;
  }

  @Override
  public K get(int index) {
    return backward.get(index);
  }

  @Override
  public int size() {
    return backward.size();
  }

  @Override
  public List<K> values() {
    return List.copyOf(backward);
  }
}
