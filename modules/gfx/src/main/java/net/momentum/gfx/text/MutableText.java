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

package net.momentum.gfx.text;

import net.momentum.gfx.text.raster.Raster;
import net.momentum.gfx.text.raster.Rasterizer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A mutable sequence of text components sharing common layout parameters.
 *
 * <p>A {@code MutableText} collects child {@link Text} components and
 * rasterizes them as a unit. Layout parameters — maximum width, justification,
 * line spacing, line count, and Y-axis orientation — are configured through
 * fluent setter methods. The rasterized result is cached until the component
 * sequence or its layout parameters change.
 *
 * <p>When an appended child is itself a {@code MutableText}, its children are
 * flattened into this sequence rather than being nested.
 */
public final class MutableText implements Text {
  private final List<Text> children = new ArrayList<>();
  private final Rasterizer rasterizer = new Rasterizer();
  private volatile @Nullable Raster cached;
  private String text = "";

  private static void flatten(Text c, List<Literal> out) {
    if (c instanceof Literal lit) {
      out.add(lit);
    } else if (c instanceof MutableText seq) {
      for (Text child : seq.children) {
        flatten(child, out);
      }
    }
  }

  /**
   * Sets the maximum width for line breaking.
   *
   * @param v the maximum width in pixels
   * @return this sequence, for chaining
   */
  public MutableText maxWidth(float v) {
    rasterizer.maxWidth = v;
    cached = null;
    return this;
  }

  /**
   * Sets whether the Y-axis is flipped during rasterization.
   *
   * @param v {@code true} to flip the Y-axis
   * @return this sequence, for chaining
   */
  public MutableText flipY(boolean v) {
    rasterizer.flipY = v;
    cached = null;
    return this;
  }

  /**
   * Sets whether lines are justified to fill the full width.
   *
   * @param v {@code true} to enable justification
   * @return this sequence, for chaining
   */
  public MutableText justify(boolean v) {
    rasterizer.justify = v;
    cached = null;
    return this;
  }

  /**
   * Sets the line spacing multiplier.
   *
   * <p>A value of {@code 1.0} uses the font's default line height. Larger
   * values increase spacing; smaller values tighten it.
   *
   * @param multiplier the line spacing multiplier
   * @return this sequence, for chaining
   */
  public MutableText lineSpacing(float multiplier) {
    rasterizer.lineSpacing = multiplier;
    cached = null;
    return this;
  }

  /**
   * Sets the maximum number of visible lines. Text beyond this limit is
   * clipped.
   *
   * @param n the maximum line count
   * @return this sequence, for chaining
   */
  public MutableText maxLines(int n) {
    rasterizer.maxLines = n;
    cached = null;
    return this;
  }

  /**
   * Sets whether an ellipsis is appended when text is truncated.
   *
   * <p>Not yet implemented; provided for forward compatibility.
   *
   * @param v {@code true} to append an ellipsis on overflow
   * @return this sequence, for chaining
   */
  public MutableText ellipsis(boolean v) {
    cached = null;
    return this;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public MutableText append(Text component) {
    if (component instanceof MutableText sequence) {
      children.addAll(sequence.children);
    } else {
      children.add(component);
    }
    text += component.text();
    cached = null;
    return this;
  }

  @Override
  public Raster raster() {
    if (cached == null) {
      List<Literal> literals = new ArrayList<>();
      flatten(this, literals);
      cached = rasterizer.render(literals);
    }
    return Objects.requireNonNull(cached);
  }

  @Override
  public @Nullable TextFormat forwadingStyle() {
    return children.isEmpty() ? null : children.getLast().forwadingStyle();
  }

  /**
   * Appends a newline character to this sequence.
   *
   * <p>The newline inherits the style of the last child. If the sequence is
   * empty, a newline with the default style is appended.
   *
   * @return this sequence, for chaining
   */
  public MutableText newline() {
    TextFormat fmt = forwadingStyle();
    if (fmt != null) {
      append(Literal.of("\n").with(fmt));
    } else {
      append(Literal.of("\n"));
    }
    return this;
  }
}
