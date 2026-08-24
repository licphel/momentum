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

package net.fmhi.codec.nbt.fixer;

import net.fmhi.codec.nbt.NBT;

/**
 * Advances NBT trees of a given {@link TypeReference} through a chain of
 * versioned {@link DataFix}es.
 *
 * <p>Each update first runs every registered {@link NBTWalker} over the tree —
 * which recursively fixes subtrees of the requested type — and then applies
 * the registered fixes for that type whose start version falls inside the
 * requested range, in ascending order.
 *
 * <p>Implementations must be immutable and thread-safe.
 */
public interface DataFixer {
  /**
   * Advances a tree to the latest version registered for its type.
   *
   * @param type    the type of the tree
   * @param input   the tree to update
   * @param version the current data version of {@code input}
   * @return the updated tree
   */
  default NBT update(TypeReference type, NBT input, int version) {
    return update(type, input, version, Integer.MAX_VALUE);
  }

  /**
   * Advances a tree from one version to another.
   *
   * <p>All fixes registered for {@code type} whose start version is at least
   * {@code version} and below {@code newVersion} are applied in ascending
   * order; versions with no registered fix are skipped. If {@code newVersion}
   * is not above {@code version}, the tree is returned unchanged.
   *
   * @param type       the type of the tree
   * @param input      the tree to update
   * @param version    the current data version of {@code input}
   * @param newVersion the target data version
   * @return the updated tree
   */
  NBT update(TypeReference type, NBT input, int version, int newVersion);
}
