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

package io.viki.momentum.math.util;

import java.util.*;
import java.util.function.Function;

/**
 * General-purpose mathematical and graph utilities. This class is stateless and thread-safe.
 */
public final class Mutil {
  private Mutil() {
  }

  /**
   * Computes the cartesian product of keyed value domains.
   *
   * @param domains keyed value domains, in product order
   * @param <K>     key type
   * @param <V>     value type
   * @return every value combination as insertion-ordered maps
   */
  public static <K, V> List<Map<K, V>> cartesianDot(
      List<? extends Map.Entry<? extends K, ? extends List<? extends V>>> domains) {
    if (domains.isEmpty()) {
      return List.of(Map.of());
    }

    List<Map<K, V>> result = List.of(Map.of());
    for (Map.Entry<? extends K, ? extends List<? extends V>> domain : domains) {
      K key = domain.getKey();
      List<Map<K, V>> next = new ArrayList<>();
      for (V value : domain.getValue()) {
        for (Map<K, V> existing : result) {
          Map<K, V> row = new LinkedHashMap<>(existing);
          row.put(key, value);
          next.add(row);
        }
      }
      result = next;
    }
    return result;
  }

  /**
   * Sorts nodes after every dependency reported by {@code dependencies}.
   *
   * <p>The algorithm runs in {@code O(V + E)} time and preserves encounter order
   * for nodes whose dependencies are simultaneously satisfied.
   *
   * @param nodes        nodes to sort, in their preferred tie-break order
   * @param dependencies function returning direct prerequisites for each node
   * @param <T>          node type
   * @return an immutable dependency-respecting order
   * @throws IllegalArgumentException if nodes are duplicated or a dependency is absent from {@code nodes}
   * @throws IllegalStateException    if the graph contains a cycle
   */
  public static <T> List<T> topologicalSort(
      Collection<? extends T> nodes,
      Function<? super T, ? extends Collection<? extends T>> dependencies) {
    Set<T> uniqueNodes = new LinkedHashSet<>();
    for (T node : nodes) {
      if (!uniqueNodes.add(node)) {
        throw new IllegalArgumentException("Graph contains duplicate node: " + node);
      }
    }

    Map<T, Integer> inDegree = new LinkedHashMap<>();
    Map<T, List<T>> dependents = new LinkedHashMap<>();
    for (T node : uniqueNodes) {
      inDegree.put(node, 0);
      dependents.put(node, new ArrayList<>());
    }

    for (T node : uniqueNodes) {
      Set<T> directDependencies = new LinkedHashSet<>();
      for (T dependency : dependencies.apply(node)) {
        if (!uniqueNodes.contains(dependency)) {
          throw new IllegalArgumentException(
              "Graph dependency '" + dependency + "' is not a declared node");
        }
        if (directDependencies.add(dependency)) {
          inDegree.merge(node, 1, Integer::sum);
          dependents.get(dependency).add(node);
        }
      }
    }

    Deque<T> ready = new ArrayDeque<>();
    for (Map.Entry<T, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        ready.addLast(entry.getKey());
      }
    }

    List<T> sorted = new ArrayList<>(uniqueNodes.size());
    while (!ready.isEmpty()) {
      T node = ready.removeFirst();
      sorted.add(node);
      for (T dependent : dependents.get(node)) {
        if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
          ready.addLast(dependent);
        }
      }
    }

    if (sorted.size() != uniqueNodes.size()) {
      throw new IllegalStateException("Graph contains a dependency cycle");
    }
    return List.copyOf(sorted);
  }
}
