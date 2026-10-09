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
import org.jspecify.annotations.Nullable;

/**
 * A node in a text component tree that can be rasterized for rendering.
 *
 * <p>Implementations are either a single styled span ({@link Literal}) or
 * a composite sequence ({@link MutableText}). Each node supplies plain-text
 * content via {@link #text()} and can produce a {@link Raster} for drawing
 * via {@link #raster()}.
 *
 * @see Literal
 * @see MutableText
 * @see TextFormat
 */
public interface Text {
  /**
   * Returns the content and style revision used to detect in-place changes.
   *
   * @return the current revision, or {@code 0} for an immutable component
   */
  default long version() {
    return 0;
  }

  /**
   * Returns the concatenated plain text of this component and all its
   * descendants.
   *
   * @return the full text content
   */
  String text();

  /**
   * Returns a styled slice without changing this component.
   * Indices count UTF-16 code units, like {@link String#substring(int, int)}.
   *
   * @param begin  the first included character index
   * @param length the number of code units to retain
   * @return the sliced text, preserving span styles and metadata
   * @throws IndexOutOfBoundsException if the range is outside this text
   */
  Text cut(int begin, int length);

  /**
   * Returns inline metadata at a UTF-16 character index obtained by hit-testing.
   * The returned array is borrowed and must not be modified.
   *
   * @param index the UTF-16 code-unit index to inspect
   * @return the metadata at that index, or {@code null} if absent or outside the content
   */
  default Meta @Nullable [] getMeta(int index) {
    return null;
  }

  /**
   * Appends a child component, returning a new {@link MutableText} that
   * starts with this component followed by the given one.
   *
   * @param component the text component to append
   * @return a new sequence containing this component and {@code component}
   */
  MutableText append(Text component);

  /**
   * Rasterizes this text component into a {@link Raster} ready for drawing.
   *
   * @return the rasterized output
   */
  Raster raster();

  /**
   * Returns the trailing style of this component, used when chaining
   * additional text or appending newlines.
   *
   * @return the last style, or {@code null} if this component is empty
   */
  @Nullable TextFormat forwadingStyle();
}
