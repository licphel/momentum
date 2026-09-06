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

package io.viki.momentum.asset;

import io.viki.momentum.util.Identifier;
import io.viki.momentum.resource.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An immutable, reusable collection of path-based asset dispatchers.
 *
 * <p>Every rule matching an asset path is invoked in registration order. A
 * failure in one dispatcher is collected and does not prevent later matching
 * dispatchers from running.
 */
public final class LoadingDispatcher {
  public static final LoadingDispatcher EMPTY = new LoadingDispatcher(List.of());

  private final List<Entry> entries;

  /**
   * Creates a {@code DispatcherGroup}.
   *
   * @param entries the dispatcher list
   */
  public LoadingDispatcher(List<Entry> entries) {
    this.entries = List.copyOf(entries);
  }

  /**
   * Returns a group containing the rules from this group followed by those
   * from {@code other}.
   *
   * @param other the group to append
   * @return a new combined group
   */
  public LoadingDispatcher then(LoadingDispatcher other) {
    List<Entry> combined = new ArrayList<>(entries.size() + other.entries.size());
    combined.addAll(entries);
    combined.addAll(other.entries);
    return new LoadingDispatcher(combined);
  }

  /**
   * Returns whether at least one dispatcher matches the given normalized path.
   *
   * @param path the normalized resource path
   * @return true if a dispatcher matches
   */
  public boolean matches(String path) {
    for (Entry entry : entries) {
      if (entry.filter.test(path)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Runs every dispatcher matching the asset and returns failures in execution
   * order.
   *
   * @param id        the resolved asset identifier
   * @param resources the provider-local resource source
   * @return failures raised by individual dispatchers
   */
  public List<Throwable> process(Identifier id, Resource resources) {
    List<Throwable> failures = new ArrayList<>();
    for (Entry entry : entries) {
      if (!entry.filter.test(id.path())) {
        continue;
      }
      try {
        entry.processor.process(id, resources);
      } catch (IOException | RuntimeException e) {
        failures.add(e);
      }
    }
    return List.copyOf(failures);
  }

  /**
   * A dispatcher that handles how a resource will be loaded with a relative path filter.
   * 
   * @param filter    the filter
   * @param processor the processor
   */
  public record Entry(Predicate<String> filter, AssetProcessor processor) {
  }
}
