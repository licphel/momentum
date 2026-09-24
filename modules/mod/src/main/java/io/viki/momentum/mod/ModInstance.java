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

package io.viki.momentum.mod;

import io.viki.momentum.event.EventBus;
import io.viki.momentum.resource.Resource;
import io.viki.momentum.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Represents one loaded built-in or external mod.
 *
 * <p>Instances are created by {@link ModLoader} during loading. Each mod has
 * its own private {@link EventBus} and a reference to the class loader that loaded its entrypoint.
 *
 * @see Mod
 * @see ModLoader
 * @see Namespace
 */
public final class ModInstance {
  private final Namespace namespace;
  private final Mod mod;
  private final @Nullable Path sourcePath;
  private final Resource resource;
  private final EventBus eventBus;
  private final ClassLoader classLoader;
  private boolean enabled;

  ModInstance(Namespace namespace, Mod mod, @Nullable Path sourcePath, ClassLoader classLoader) {
    this.namespace = namespace;
    this.mod = mod;
    this.sourcePath = sourcePath;
    this.resource = mod.createResource();
    this.eventBus = new EventBus();
    this.enabled = true;
    this.classLoader = classLoader;
  }

  /**
   * Returns the mod's namespace (derived from its mod ID).
   *
   * @return the mod's namespace
   */
  public Namespace namespace() {
    return namespace;
  }

  /**
   * Returns the mod main class that supplies this instance's metadata.
   *
   * @return the mod main class
   */
  public Mod info() {
    return mod;
  }

  /**
   * Returns the mod main class.
   *
   * @return the mod main class
   */
  public Mod mod() {
    return mod;
  }

  /**
   * Returns the external source path, or empty for a built-in mod.
   *
   * @return the external source path
   */
  public Optional<Path> sourcePath() {
    return Optional.ofNullable(sourcePath);
  }

  /**
   * Returns the resource provider created by the mod entrypoint.
   *
   * @return this mod's resource provider
   */
  public Resource resource() {
    return resource;
  }

  /**
   * Returns the mod's private event bus.
   *
   * @return the mod's private event bus
   */
  public EventBus eventBus() {
    return eventBus;
  }

  /**
   * Returns whether this mod is currently enabled.
   *
   * @return whether this mod is currently enabled
   */
  public boolean enabled() {
    return enabled;
  }

  /**
   * Enables or disables this mod.
   *
   * @param enabled true for enabling, false for disabling
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the mod main class instance.
   *
   * @return the mod's entrypoint instance
   */
  public Mod entrypoint() {
    return mod;
  }

  /**
   * Returns the class loader used to load this mod's classes.
   *
   * @return the mod's class loader
   */
  public ClassLoader classLoader() {
    return classLoader;
  }

  @Override
  public String toString() {
    return mod.modId() + "@" + mod.version();
  }
}
