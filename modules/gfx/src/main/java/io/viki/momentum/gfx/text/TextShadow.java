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

package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * Immutable text shadow offset and optional color gradient.
 *
 * <p>A {@code null} color derives the shadow from each text element's own gradient by scaling
 * its RGB channels to 75% while preserving alpha. Instances are immutable and safe to share.
 *
 * @param offset visual displacement from the text to its shadow
 * @param color  shadow color gradient, or {@code null} to shade each text element's gradient
 */
public record TextShadow(Vector2 offset, @Nullable Gradient color) {
  /** None shadow, plain text. */
  public static final TextShadow NONE = new TextShadow(Vector2.ZERO, Color.BLACK);
  /** Default one-unit shadow with a dark gray color. */
  public static final TextShadow DEFAULT = new TextShadow(Vector2.ONE, Color.grayscale(0.0F, 0.5F));

  /**
   * Creates a diagonal shadow with the same offset on both visual axes.
   *
   * @param offset displacement in world units on each visual axis
   * @param color  shadow color gradient, or {@code null} to shade each text element's gradient
   */
  public TextShadow(float offset, @Nullable Gradient color) {
    this(new Vector2(offset, offset), color);
  }
}
