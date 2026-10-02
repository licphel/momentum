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

import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.text.raster.Rasterizer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a mutable rich-text sequence with reusable layout settings.
 *
 * <p>The sequence owns the text order and rasterization policy used by a text renderer. Changes
 * invalidate the rendered representation and advance the observable {@link #version()} so views
 * can refresh layouts after in-place edits. Instances are mutable and not thread-safe; mutation
 * and rendering must be coordinated by one owning thread.
 */
public final class MutableText implements Text {
  private final List<Text> children = new ArrayList<>();
  private final Rasterizer rasterizer = new Rasterizer();
  private volatile @Nullable Raster cached;
  private String text = "";
  private long version;

  /**
   * Returns the content revision used to detect in-place changes.
   *
   * @return monotonically increasing revision for this sequence
   */
  public long version() {
    return version;
  }

  /**
   * Returns the current maximum line width.
   *
   * @return maximum width in text-layout units
   */
  public float maxWidth() {
    return rasterizer.maxWidth;
  }

  /**
   * Creates a mutable copy with the same content and layout policy.
   *
   * <p>The returned sequence has independent layout state, while its immutable or caller-owned
   * text components remain shared.
   *
   * @return copied text sequence
   */
  public MutableText copy() {
    MutableText result = new MutableText();
    result.children.addAll(children);
    result.text = text;
    result.rasterizer.maxWidth = rasterizer.maxWidth;
    result.rasterizer.flipY = rasterizer.flipY;
    result.rasterizer.justify = rasterizer.justify;
    result.rasterizer.lineSpacing = rasterizer.lineSpacing;
    result.rasterizer.maxLines = rasterizer.maxLines;
    return result;
  }

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
   * Sets the maximum width available to each laid-out line.
   *
   * @param v maximum width in text-layout units
   * @return this sequence for fluent configuration
   */
  public MutableText maxWidth(float v) {
    rasterizer.maxWidth = v;
    version++;
    cached = null;
    return this;
  }

  /**
   * Selects the vertical orientation used by rasterization.
   *
   * @param v {@code true} when the raster should use the flipped vertical orientation
   * @return this sequence for fluent configuration
   */
  public MutableText flipY(boolean v) {
    rasterizer.flipY = v;
    version++;
    cached = null;
    return this;
  }

  /**
   * Selects whether lines should expand to the configured width.
   *
   * @param v {@code true} to justify eligible lines
   * @return this sequence for fluent configuration
   */
  public MutableText justify(boolean v) {
    rasterizer.justify = v;
    version++;
    cached = null;
    return this;
  }

  /**
   * Sets the distance multiplier between adjacent text lines.
   *
   * <p>A value of {@code 1.0} preserves the font's normal line height.
   *
   * @param multiplier line-height multiplier
   * @return this sequence for fluent configuration
   */
  public MutableText lineSpacing(float multiplier) {
    rasterizer.lineSpacing = multiplier;
    version++;
    cached = null;
    return this;
  }

  /**
   * Limits the number of lines included in the rendered result.
   *
   * @param n maximum visible line count
   * @return this sequence for fluent configuration
   */
  public MutableText maxLines(int n) {
    rasterizer.maxLines = n;
    version++;
    cached = null;
    return this;
  }

  /**
   * Records whether truncated text should request an ellipsis.
   *
   * <p>The current rasterizer does not draw the marker, but the setting remains part of the
   * fluent text-layout contract for future renderers.
   *
   * @param v {@code true} to request an ellipsis on overflow
   * @return this sequence for fluent configuration
   */
  public MutableText ellipsis(boolean v) {
    version++;
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
    version++;
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
