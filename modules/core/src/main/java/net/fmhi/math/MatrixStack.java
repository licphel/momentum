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

package net.fmhi.math;

/**
 * A fixed-depth matrix stack (max 256 entries).
 *
 * <p>The top of the stack is the "current transform". Each {@link #push()} duplicates
 * the top; each {@link #pop()} discards it.
 *
 * @see Matrix4x4
 */
public final class MatrixStack {
  private static final int MAX_DEPTH = 256;

  private final Matrix4x4[] stack = new Matrix4x4[MAX_DEPTH];
  private int top = 0;

  /**
   * Creates an empty matrix stack.
   */
  public MatrixStack() {
    stack[0] = Matrix4x4.IDENTITY;
  }

  /**
   * Duplicates the current top, creating a new level.
   *
   * @throws IllegalStateException if the stack is full
   */
  public void push() {
    if (top >= MAX_DEPTH - 1) {
      throw new IllegalStateException("Matrix stack overflow (max depth: " + MAX_DEPTH + ")");
    }
    stack[top + 1] = stack[top];
    top++;
  }

  /**
   * Pushes a new level whose matrix is {@code current × m}.
   *
   * @param m matrix to concatenate with the current top
   */
  public void push(Matrix4x4 m) {
    push();
    stack[top] = stack[top].multiply(m);
  }

  /**
   * Discards the top entry, restoring the previous level.
   *
   * @throws IllegalStateException if the stack is already at the bottom
   */
  public void pop() {
    if (top == 0) {
      throw new IllegalStateException("Matrix stack underflow");
    }
    top--;
  }

  /**
   * Replaces the current top with the given matrix (without changing depth).
   *
   * @param m the matrix to load
   */
  public void load(Matrix4x4 m) {
    stack[top] = m;
  }

  /**
   * Resets the entire stack to a single identity level.
   */
  public void clear() {
    top = 0;
    stack[0] = Matrix4x4.IDENTITY;
  }

  /**
   * Returns the current (top) matrix.
   *
   * @return the top matrix
   */
  public Matrix4x4 top() {
    return stack[top];
  }

  /**
   * Returns the current stack depth (0 = bottom).
   *
   * @return the current depth
   */
  public int depth() {
    return top;
  }
}
