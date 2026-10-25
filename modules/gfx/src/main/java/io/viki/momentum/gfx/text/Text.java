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

import java.util.List;

/**
 * Immutable styled text content and layout that can be shared between consumers.
 *
 * <p>Use {@link TextBuilder} to compose spans and produce a completed value. Rasterization is
 * cached; rendering must follow the owning-thread rules of the fonts, gradients, and inline
 * metadata referenced by the text.
 */
public interface Text {
  /**
   * Invalidates cached rasters after a referenced font resource changes or is released.
   */
  static void invalidateCache() {
    TextRasterCache.clear();
  }

  /**
   * Returns the plain text formed by concatenating this value's spans.
   *
   * @return the UTF-16 text content
   */
  String text();

  /**
   * Returns the immutable styled spans in source order.
   *
   * @return an immutable list of spans
   */
  List<Literal> spans();

  /**
   * Returns a slice of the source text while retaining applicable styles and inline attachments.
   * Indices count UTF-16 code units, as with {@link String#substring(int, int)}.
   *
   * @param begin the first included UTF-16 code-unit index
   * @param length the number of UTF-16 code units to include
   * @return the sliced text
   * @throws IndexOutOfBoundsException if the requested range is outside this text
   */
  Text cut(int begin, int length);

  /**
   * Returns metadata associated with a UTF-16 character index.
   *
   * @param index the UTF-16 code-unit index to inspect
   * @return a defensive copy of the metadata, or {@code null} if none is present
   */
  default Meta @Nullable [] getMeta(int index) {
    return null;
  }

  /**
   * Returns the layout policy associated with this text.
   *
   * @return the text layout policy
   */
  default TextLayout layout() {
    return TextLayout.DEFAULT;
  }

  /**
   * Returns the maximum width configured for each line.
   *
   * @return the maximum line width in text layout units
   */
  default float maxWidth() {
    return layout().maxWidth();
  }

  /**
   * Creates a builder initialized with this text's content and layout.
   *
   * @return an independent builder
   */
  default TextBuilder toBuilder() {
    return new TextBuilder(this);
  }

  /**
   * Creates a builder containing this text followed by the supplied component.
   *
   * @param component the text to append
   * @return a builder containing both text values
   */
  default TextBuilder append(Text component) {
    return toBuilder().append(component);
  }

  /**
   * Returns the rasterized layout used for drawing and hit testing.
   *
   * @return the rasterized text
   */
  Raster raster();

  /**
   * Returns the trailing style, for use when appending text or creating a newline.
   *
   * @return the final span's style, or {@code null} if no style is available
   */
  @Nullable TextFormat forwadingStyle();
}
