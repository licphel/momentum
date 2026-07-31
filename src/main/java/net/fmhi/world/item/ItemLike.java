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

package net.fmhi.world.item;

/**
 * A type that can be stored in an {@link ItemStack}.
 *
 * <p>Implementations define the maximum stack size via {@link #maxStackSize()}.
 * The default maximum is {@value #DEFAULT_STACK_SIZE}.
 *
 * @see ItemStack
 * @see Slot
 */
public interface ItemLike {
  /** The default maximum stack size. */
  int DEFAULT_STACK_SIZE = 100;

  /**
   * Returns the maximum number of items of this type that can fit in a single stack.
   *
   * @return the maximum stack size; defaults to {@value #DEFAULT_STACK_SIZE}
   */
  default int maxStackSize() {
    return DEFAULT_STACK_SIZE;
  }
}
