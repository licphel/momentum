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

package net.momentum.mod;

import net.momentum.event.EventBus;
import net.momentum.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * Represents a loaded mod, backed by a single JAR file containing {@code mod.json} at its root.
 *
 * <p>Instances are created by {@link ModLoader} during loading. Each mod has
 * its own private {@link EventBus} and a reference to the class loader that loaded its entrypoint.
 *
 * @see ModInfo
 * @see ModLoader
 * @see Namespace
 */
public final class Mod {
  private final Namespace namespace;
  private final ModInfo info;
  private final Path jarPath;
  private final EventBus eventBus;
  private final @Nullable Object entrypoint;
  private final @Nullable ClassLoader classLoader;
  private boolean enabled;

  Mod(Namespace namespace, ModInfo info, Path jarPath, @Nullable Object entrypoint, @Nullable ClassLoader classLoader) {
    this.namespace = namespace;
    this.info = info;
    this.jarPath = jarPath;
    this.eventBus = new EventBus();
    this.enabled = true;
    this.entrypoint = entrypoint;
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
   * Returns the parsed mod.json metadata.
   *
   * @return the mod info
   */
  public ModInfo info() {
    return info;
  }

  /**
   * Returns the JAR file this mod was loaded from.
   *
   * @return the mod's jar path
   */
  public Path jarPath() {
    return jarPath;
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
   * Returns the entrypoint instance, or {@code null} if the mod has no program.
   *
   * @return the mod's entrypoint instance
   */
  public @Nullable Object entrypoint() {
    return entrypoint;
  }

  /**
   * Returns the class loader used to load this mod's classes, or {@code null}.
   *
   * @return the mod's class loader
   */
  public @Nullable ClassLoader classLoader() {
    return classLoader;
  }

  @Override
  public String toString() {
    return info.modId() + "@" + info.version();
  }
}
