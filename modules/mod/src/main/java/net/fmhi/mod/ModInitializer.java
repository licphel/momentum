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

package net.fmhi.mod;

/**
 * Optional lifecycle hooks for mod entrypoint classes.
 *
 * <p>Mod entrypoints may implement this interface to receive callbacks
 * during the loading process:
 *
 * <ul>
 *   <li>{@link #onPreLoad()} — called after mod.json is parsed and
 *   the entrypoint is instantiated, before dependency resolution</li>
 *   <li>{@link #onPostLoad()} — called during {@link ModLoader#freeze()} after
 *   the dependency graph has been topologically sorted</li>
 * </ul>
 *
 * @see Mod
 */
public interface ModInitializer {
  /**
   * Called after the mod is loaded but before dependency resolution.
   */
  default void onPreLoad() {
  }

  /**
   * Called after the dependency graph has been sorted.
   */
  default void onPostLoad() {
  }
}
