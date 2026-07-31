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

package net.fmhi.fml.registry;

import net.fmhi.fml.Identifier;
import net.fmhi.fml.Namespace;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collects registry entries during mod construction and registers them
 * in bulk when the registration event fires.
 *
 * @param <T> the registry entry type
 */
public final class DeferredRegister<T> {
  private final Identifier registryKey;
  private final Namespace namespace;
  private final List<Entry<T>> entries = new ArrayList<>();

  private DeferredRegister(Namespace namespace, Identifier registryKey) {
    this.namespace = namespace;
    this.registryKey = registryKey;
  }

  /**
   * Creates a deferred register targeting the given registry.
   *
   * @param namespace   the mod namespace for entry identifiers
   * @param key the target registry key, resolved by the given namespace
   * @param <T>         the registry entry type
   * @return a new deferred register
   */
  public static <T> DeferredRegister<T> create(Namespace namespace, String key) {
    return new DeferredRegister<>(namespace, namespace.resolve(key));
  }

  /**
   * Queues an entry for registration.
   *
   * @param name     the entry name (namespace-qualified by this register's namespace)
   * @param supplier the factory that creates the entry value
   * @return a registry object that will hold the value after registration
   */
  public Holder<T> register(String name, Supplier<? extends T> supplier) {
    Identifier id = namespace.resolve(name);
    Holder<T> obj = new Holder<>(id);
    entries.add(new Entry<>(id, supplier, obj));
    return obj;
  }

  /**
   * Registers all queued entries into the target registry.
   *
   * <p>This method is idempotent: subsequent calls after the first are
   * no-ops.
   *
   * @param registry the registry submitted to
   * @throws IllegalStateException if the target registry is not found
   */
  public void submit(Registry<T> registry) {
    for (Entry<T> entry : List.copyOf(entries)) {
      T value = entry.supplier.get();
      registry.register(entry.id, value);
      entry.holder.resolve(value);
    }
    entries.clear();
  }

  /**
   * Returns the target registry key.
   *
   * @return the registry key
   */
  public Identifier registryKey() {
    return registryKey;
  }

  /**
   * Returns the mod namespace.
   *
   * @return the namespace
   */
  public Namespace namespace() {
    return namespace;
  }

  private record Entry<T>(Identifier id, Supplier<? extends T> supplier, Holder<T> holder) {
  }
}
