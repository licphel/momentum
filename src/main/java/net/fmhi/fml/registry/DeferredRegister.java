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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Collects registry entries during mod construction and registers them
 * in bulk when the registration event fires.
 *
 * <p>Usage mirrors MinecraftForge's {@code DeferredRegister}:
 *
 * <pre>{@code
 * // In your mod initializer:
 * static final DeferredRegister<Block> BLOCKS =
 *     DeferredRegister.create(Registries.BLOCKS, "mymod");
 *
 * static final Holder<Block> STONE =
 *     BLOCKS.register("stone", StoneBlock::new);
 *
 * // During the registration phase:
 * BLOCKS.registerAll();
 *
 * // Now usable:
 * Block stone = STONE.get();
 * }</pre>
 *
 * @param <T> the registry entry type
 */
public final class DeferredRegister<T> {
  private final Identifier registryKey;
  private final String namespace;
  private final List<Entry<T>> entries = new ArrayList<>();

  private DeferredRegister(Identifier registryKey, String namespace) {
    this.registryKey = Objects.requireNonNull(registryKey);
    this.namespace = Objects.requireNonNull(namespace);
  }

  /**
   * Creates a deferred register targeting the given registry.
   *
   * @param registryKey the target registry identifier
   * @param namespace   the mod namespace for entry identifiers
   * @param <T>         the registry entry type
   * @return a new deferred register
   */
  public static <T> DeferredRegister<T> create(Identifier registryKey, String namespace) {
    return new DeferredRegister<>(registryKey, namespace);
  }

  /**
   * Queues an entry for registration.
   *
   * @param name     the entry name (namespace-qualified by this register's namespace)
   * @param supplier the factory that creates the entry value
   * @return a registry object that will hold the value after registration
   */
  public Holder<T> register(String name, Supplier<? extends T> supplier) {
    Identifier id = Identifier.of(namespace, name);
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

  /** Returns the target registry key. */
  public Identifier registryKey() {
    return registryKey;
  }

  /** Returns the mod namespace. */
  public String namespace() {
    return namespace;
  }

  private record Entry<T>(Identifier id, Supplier<? extends T> supplier, Holder<T> holder) {}
}
