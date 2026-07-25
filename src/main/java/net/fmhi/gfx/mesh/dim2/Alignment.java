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

package net.fmhi.gfx.mesh.dim2;

/**
 * Alignment for positioning content relative to an anchor point.
 *
 * <p>Negative values anchor toward the minimum edge, positive toward the maximum,
 * and zero centers. For example, horizontal {@code -1} aligns left, {@code 0} centers, and
 * {@code 1} aligns right.
 *
 * @param horizontal the horizontal alignment; {@code -1} for left, {@code 0} for center, {@code 1} for right
 * @param vertical   the vertical alignment; {@code -1} for up, {@code 0} for center, {@code 1} for down
 */
public record Alignment(int horizontal, int vertical) {
  /** Aligned left and up. */
  public static final Alignment LEFT_UP = new Alignment(-1, -1);
  /** Aligned right and up. */
  public static final Alignment RIGHT_UP = new Alignment(1, -1);
  /** Centered horizontally, aligned up. */
  public static final Alignment CENTRAL_UP = new Alignment(0, -1);
  /** Centered both horizontally and vertically. */
  public static final Alignment CENTRAL = new Alignment(0, 0);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Alignment(int horizontal1, int vertical1))) {
      return false;
    }
    return horizontal == horizontal1 && vertical == vertical1;
  }
}
