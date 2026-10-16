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

package io.viki.momentum.registry;

import io.viki.momentum.util.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Identifier-indexed collection of registries.
 *
 * <p>The lookup is separate from individual registries so temporary and test
 * registries do not become global accidentally. Register each shared registry
 * during the bootstrap phase, then use its identifier to resolve the registry.
 * The lookup is thread-safe; registry operations retain the thread-safety
 * guarantees of the returned {@link Registry}.
 */
public final class RegisterLookup {
  private static final RegisterLookup GLOBAL = new RegisterLookup();
  private final Map<Identifier, Registry<?>> registries = new LinkedHashMap<>();

  /** Creates an empty lookup collection for one runtime or registry view. */
  public RegisterLookup() {
  }

  /**
   * Returns the process-wide lookup used by built-in registries.
   *
   * @return the shared lookup instance
   */
  public static RegisterLookup global() {
    return GLOBAL;
  }

  /**
   * Adds a registry under its own registry key.
   *
   * @param registry the registry to add
   * @throws NullPointerException if {@code registry} is null
   * @throws IllegalArgumentException if another registry uses the same key
   */
  public synchronized void register(Registry<?> registry) {
    Objects.requireNonNull(registry, "registry");
    Identifier key = registry.key();
    Registry<?> previous = registries.putIfAbsent(key, registry);
    if (previous != null && previous != registry) {
      throw new IllegalArgumentException("Duplicate registry key: " + key);
    }
  }

  /**
   * Returns a registry by identifier.
   *
   * <p>The identifier alone cannot carry the Java entry type. Callers should
   * prefer {@link #get(Identifier, Class)} when the type is available; this
   * overload is the unchecked boundary used by code that obtains the type
   * from another protocol or registry key.
   *
   * @param key the registry identifier
   * @param <T> the entry type expected by the caller
   * @return the registry, or {@code null} when the key is unknown
   */
  @SuppressWarnings("unchecked")
  public synchronized <T extends RegistryEntry> @Nullable Registry<T> get(Identifier key) {
    return (Registry<T>) registries.get(key);
  }

  /**
   * Returns a registry without assigning an entry type.
   *
   * @param key the registry identifier
   * @return the registry, or {@code null} when the key is unknown
   */
  public synchronized @Nullable Registry<?> getRaw(Identifier key) {
    return registries.get(key);
  }

  /**
   * Returns a registry with the requested entry type.
   *
   * <p>The identifier identifies the registry; the type token validates the
   * entries already present in it and makes the unchecked generic boundary
   * explicit to callers.
   *
   * @param key the registry identifier
   * @param entryType the expected entry type
   * @param <T> the entry type
   * @return the registry, or {@code null} when the key is unknown
   * @throws ClassCastException if an existing entry has another type
   */
  public synchronized <T extends RegistryEntry> @Nullable Registry<T> get(Identifier key, Class<T> entryType) {
    Registry<?> raw = registries.get(key);
    if (raw == null) {
      return null;
    }
    for (RegistryEntry entry : raw) {
      if (!entryType.isInstance(entry)) {
        throw new ClassCastException("Registry '" + key + "' contains "
            + entry.getClass().getName() + ", expected " + entryType.getName());
      }
    }
    @SuppressWarnings("unchecked")
    Registry<T> typed = (Registry<T>) raw;
    return typed;
  }

  /**
   * Returns a registry or fails with a contextual error.
   *
   * @param key the registry identifier
   * @param <T> the entry type expected by the caller
   * @return the registered registry
   * @throws IllegalArgumentException if the key is unknown
   */
  @SuppressWarnings("unchecked")
  public synchronized <T extends RegistryEntry> Registry<T> require(Identifier key) {
    Registry<T> registry = (Registry<T>) registries.get(key);
    if (registry == null) {
      throw new IllegalArgumentException("Unknown registry: " + key);
    }
    return registry;
  }

  /**
   * Returns a type-checked registry or fails with a contextual error.
   *
   * @param key the registry identifier
   * @param entryType the expected entry type
   * @param <T> the entry type
   * @return the registered registry
   * @throws IllegalArgumentException if the key is unknown
   * @throws ClassCastException if an existing entry has another type
   */
  public <T extends RegistryEntry> Registry<T> require(Identifier key, Class<T> entryType) {
    Registry<T> registry = get(key, entryType);
    if (registry == null) {
      throw new IllegalArgumentException("Unknown registry: " + key);
    }
    return registry;
  }

  /**
   * Returns a snapshot of all registered registry keys.
   *
   * @return an unmodifiable set of registry identifiers
   */
  public synchronized Set<Identifier> keys() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(registries.keySet()));
  }

  /**
   * Returns a stable snapshot of all registered registries.
   *
   * @return an immutable list of registries in registration order
   */
  public synchronized List<Registry<?>> values() {
    return List.copyOf(new ArrayList<>(registries.values()));
  }

  /**
   * Streams a stable snapshot of all registered registries.
   *
   * @return a stream over the registered registries
   */
  public synchronized Stream<Registry<?>> stream() {
    return values().stream();
  }
}
