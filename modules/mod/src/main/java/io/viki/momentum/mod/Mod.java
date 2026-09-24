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

import io.viki.momentum.resource.Resource;
import io.viki.momentum.util.SemanticVersion;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Describes and identifies a mod's main class.
 *
 * <p>A mod main class must implement this interface and be annotated with
 * {@link Entrypoint}. The loader obtains all mod metadata from the instance;
 * no external metadata file is required.
 */
public interface Mod {
  /**
   * Creates this mod's resource provider.
   *
   * <p>The default follows the entrypoint's class loader, which works for
   * built-in mods, exploded development classes, and ordinary mod JARs.
   * Implementations may return a combined or remote-backed provider instead.
   *
   * @return a resource provider owned by the loaded mod instance
   */
  default Resource createResource() {
    return Resource.classpath(getClass());
  }

  /**
   * Returns the unique identifier of this mod.
   *
   * @return the mod identifier
   */
  String modId();

  /**
   * Returns this mod's version.
   *
   * @return the mod version
   */
  SemanticVersion version();

  /**
   * Returns the authors of this mod.
   *
   * @return the author string, or an empty string when no authors are declared
   */
  default String authors() {
    return "";
  }

  /**
   * Returns the display name of this mod.
   *
   * @return the display name, derived from the underscore-separated {@link #modId()}
   */
  default String displayName() {
    try {
      return Arrays.stream(modId().split("_"))
          .filter(part -> !part.isEmpty())
          .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
          .collect(Collectors.joining(" "));
    } catch (Exception _) {
      return modId();
    }
  }

  /**
   * Returns the description of this mod.
   *
   * @return the description, or an empty string when none is declared
   */
  default String description() {
    return "";
  }

  /**
   * Returns the mods that this mod requires.
   *
   * @return the declared dependencies, or an empty array when none are declared
   */
  default Dependency[] dependencies() {
    return new Dependency[0];
  }

  /**
   * Returns whether this mod supplies core application functionality.
   *
   * @return {@code true} for a core mod; otherwise {@code false}
   */
  default boolean isCoreMod() {
    return false;
  }

  /**
   * Returns the fully qualified name of this mod's entrypoint class.
   *
   * <p>The value is derived from the implementing class and is not used to
   * discover the entrypoint.
   *
   * @return this mod's entrypoint class name
   */
  default String entrypoint() {
    return getClass().getName();
  }
}
