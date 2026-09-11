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
 * A bidirectional mapping between canonical objects and dense integer identifiers.
 *
 * <p>IDs are assigned sequentially as objects are added. Lookup by object
 * returns the assigned ID; lookup by ID returns the object. The mapping is
 * append-only — entries cannot be removed once added.
 *
 * @param <K> the key type stored in this palette
 */
public class Palette<K extends PaletteCandidate> {
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

  /**
   * Returns the ID assigned to the given object.
   *
   * @param map the object to look up
   * @return the assigned ID, or {@code -1} if the object is unknown
   */
  public int searchIndex(K map) {
    Integer id = forward.get(map);
    return id != null ? id : -1;
  }

  /**
   * Returns the object for the given ID.
   *
   * @param index the ID to look up
   * @return the object at that ID
   * @throws IndexOutOfBoundsException if the index is out of range
   */
  public K get(int index) {
    return backward.get(index);
  }

  /**
   * Returns the number of entries in this palette.
   *
   * @return the entry count
   */
  public int size() {
    return backward.size();
  }

  /**
   * Returns all entries in ID order.
   *
   * @return an unmodifiable list of all registered objects
   */
  public List<K> values() {
    return List.copyOf(backward);
  }
}
