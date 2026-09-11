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

package io.viki.momentum.gfx.tint;

/**
 * Gradient that assigns a color to each of the four quad corners, in the order in which
 * vertices are written.
 *
 * <p>Mutable; not thread-safe. Each instance must be confined to a single thread.
 */
public class QuadGradient implements Gradient {
  private final long c0;
  private final long c1;
  private final long c2;
  private final long c3;

  /**
   * Creates a white gradient.
   */
  public QuadGradient() {
    this(Color.WHITE);
  }

  /**
   * Creates a uniform gradient from a packed color.
   *
   * @param color the packed color applied to every corner
   */
  public QuadGradient(long color) {
    this.c0 = color;
    this.c1 = color;
    this.c2 = color;
    this.c3 = color;
  }

  /**
   * Creates a uniform gradient from a color.
   *
   * @param color the color applied to every corner
   */
  public QuadGradient(Color color) {
    this(color.packF16LE());
  }

  /**
   * Creates a gradient with a distinct packed color per corner.
   *
   * @param c0 the packed color of the first corner
   * @param c1 the packed color of the second corner
   * @param c2 the packed color of the third corner
   * @param c3 the packed color of the fourth corner
   */
  public QuadGradient(long c0, long c1, long c2, long c3) {
    this.c0 = c0;
    this.c1 = c1;
    this.c2 = c2;
    this.c3 = c3;
  }

  /**
   * Creates a gradient with a distinct color per corner.
   *
   * @param c0 the color of the first corner
   * @param c1 the color of the second corner
   * @param c2 the color of the third corner
   * @param c3 the color of the fourth corner
   */
  public QuadGradient(Color c0, Color c1, Color c2, Color c3) {
    this(c0.packF16LE(), c1.packF16LE(), c2.packF16LE(), c3.packF16LE());
  }

  /**
   * Returns a gradient whose left corners use the left color and whose right corners use
   * the right color.
   *
   * @param leftColor  the packed color of the left corners
   * @param rightColor the packed color of the right corners
   * @return a new gradient
   */
  public static QuadGradient horizontal(long leftColor, long rightColor) {
    return new QuadGradient(
        leftColor,
        rightColor,
        rightColor,
        leftColor
    );
  }

  /**
   * Returns a gradient whose left corners use the left color and whose right corners use
   * the right color.
   *
   * @param leftColor  the color of the left corners
   * @param rightColor the color of the right corners
   * @return a new gradient
   */
  public static QuadGradient horizontal(Color leftColor, Color rightColor) {
    return horizontal(leftColor.packF16LE(), rightColor.packF16LE());
  }

  /**
   * Returns a gradient whose lower corners use the bottom color and whose upper corners
   * use the top color.
   *
   * @param bottomColor the packed color of the lower corners
   * @param topColor    the packed color of the upper corners
   * @return a new gradient
   */
  public static QuadGradient vertical(long bottomColor, long topColor) {
    return new QuadGradient(
        bottomColor,
        bottomColor,
        topColor,
        topColor
    );
  }

  /**
   * Returns a gradient whose lower corners use the bottom color and whose upper corners
   * use the top color.
   *
   * @param bottomColor the color of the lower corners
   * @param topColor    the color of the upper corners
   * @return a new gradient
   */
  public static QuadGradient vertical(Color bottomColor, Color topColor) {
    return vertical(bottomColor.packF16LE(), topColor.packF16LE());
  }

  /**
   * Returns a gradient whose first two corners use the start color and whose last two
   * corners use the end color.
   *
   * @param startColor the packed color of the first two corners
   * @param endColor   the packed color of the last two corners
   * @return a new gradient
   */
  public static QuadGradient diagonal(long startColor, long endColor) {
    return new QuadGradient(
        startColor,
        startColor,
        endColor,
        endColor
    );
  }

  /**
   * Returns a gradient whose first two corners use the start color and whose last two
   * corners use the end color.
   *
   * @param startColor the color of the first two corners
   * @param endColor   the color of the last two corners
   * @return a new gradient
   */
  public static QuadGradient diagonal(Color startColor, Color endColor) {
    return diagonal(startColor.packF16LE(), endColor.packF16LE());
  }

  /**
   * Returns a uniform gradient.
   *
   * @param color the packed color to apply to every corner
   * @return a new gradient
   */
  public static QuadGradient solid(long color) {
    return new QuadGradient(color);
  }

  /**
   * Returns a uniform gradient.
   *
   * @param color the color to apply to every corner
   * @return a new gradient
   */
  public static QuadGradient solid(Color color) {
    return new QuadGradient(color);
  }

  @Override
  public long getColor(int vertexIndex) {
    return switch (vertexIndex) {
      case 1 -> c1;
      case 2 -> c2;
      case 3 -> c3;
      default -> c0;
    };
  }
}
