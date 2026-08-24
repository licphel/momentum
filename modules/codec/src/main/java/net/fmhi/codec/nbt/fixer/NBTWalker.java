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
 * Locates the sub-trees of a given {@link TypeReference} inside a larger tree
 * and advances them through a {@link DataFixer}.
 *
 * <p>A walker encodes the knowledge of where a type lives in its container —
 * for example, that item stacks sit in the {@code Items} list of a chest — and
 * recursively descends into nested containers. For every sub-tree it identifies
 * as belonging to the requested type, it calls
 * {@link DataFixer#update(TypeReference, NBT, int)} with the same version and
 * splices the result back into the tree.
 *
 * <p>Walkers do not apply fixes themselves; they only locate and recurse. When
 * the requested type is not the one they know about, they must return the input
 * unchanged. Implementations should not mutate the input tree; return a new
 * tree with the fixed subtrees spliced in instead.
 */
@FunctionalInterface
public interface NBTWalker {
  /**
   * Walks the tree, fixing every subtree that belongs to the given type.
   *
   * @param type    the type whose subtrees to fix
   * @param input   the container tree
   * @param fixer   the fixer to advance located sub-trees through
   * @param version the current data version of {@code input}
   * @return the walked tree
   */
  NBT walk(TypeReference type, NBT input, DataFixer fixer, int version);
}
