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

/**
 * Identifies a kind of business object embedded in an {@code NBT} tree — e.g.
 * an item stack inside a chest — that data fixes are registered against.
 *
 * <p>Two references with the same name are equal, so the same logical type can
 * be shared freely across a fixer's registration, walkers, and update calls.
 *
 * @param name the type name
 */
public record TypeReference(String name) {
  /**
   * Creates a type reference.
   *
   * @param name the type name
   * @throws IllegalArgumentException if {@code name} is {@code null} or empty
   */
  public TypeReference {
    if (name.isEmpty()) {
      throw new IllegalArgumentException("Type reference name must not be null or empty");
    }
  }
}
