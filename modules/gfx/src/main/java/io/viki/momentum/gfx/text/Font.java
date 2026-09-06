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

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.text.freetype.FreetypeFont;
import io.viki.momentum.gfx.text.raster.Glyph;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * A scalable font resource providing glyph metrics, glyph lookup, and
 * on-demand rasterization.
 *
 * <p>Open a font via {@link #open(Device, ByteBuffer)}. Each instance must be
 * {@link #close() closed} when no longer needed to release native resources.
 *
 * <p>All metrics are computed at the font's current resolution. Adjust quality
 * with {@link #setResolution(float)} before rasterizing glyphs.
 *
 * @see TextFormat
 * @see FontMetrics
 */
public interface Font extends AutoCloseable {
  /** Default font size in pixels. */
  float DEFAULT_SIZE = 16.0F;
  /** Plain (regular) weight — the default style. */
  int REGULAR = 0;
  /** Bold weight flag. */
  int BOLD = 1;
  /** Italic slant flag. */
  int ITALIC = 2;
  /** Underline decoration flag. */
  int UNDERLINE = 4;
  /** Strikethrough decoration flag. */
  int STRIKETHROUGH = 8;

  /**
   * Opens a font from font file data.
   *
   * @param device the graphics device
   * @param buf    the font file contents
   * @return the opened font, or {@code null} if no font backend is available
   * or the data cannot be parsed
   */
  static @Nullable Font open(Device device, ByteBuffer buf) {
    try {
      return new FreetypeFont(device, buf, 0);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Returns the scaled metrics of this font at the current resolution.
   *
   * @return the font metrics
   */
  FontMetrics metrics();

  /**
   * Tests whether this font covers the given Unicode code point.
   *
   * @param codepoint the Unicode code point to look up
   * @return {@code true} if a glyph exists for the code point
   */
  boolean hasGlyph(int codepoint);

  /**
   * Returns the internal glyph index for a Unicode code point.
   *
   * @param codepoint the Unicode code point to look up
   * @return the glyph index, or {@code 0} if the code point is not covered
   */
  int getGlyphIndex(int codepoint);

  @Override
  void close();

  /**
   * Rasterizes a glyph into the texture atlas and returns its positioning
   * data.
   *
   * @param glyphIndex the internal glyph index
   * @param fontStyle  a bitmask of style flags defined on {@link Font}
   * @return the rasterized glyph, or {@code null} if the glyph has no visual
   * representation
   */
  @Nullable Glyph rasterizeGlyph(int glyphIndex, int fontStyle);

  /**
   * Sets the resolution for glyph rasterization.
   *
   * @param res the resolution in pixels
   */
  void setResolution(float res);

  /**
   * Returns the current rasterization resolution.
   *
   * @return the resolution in pixels
   */
  float resolution();

  /**
   * Returns the raw font file content.
   *
   * @return a read-only view of the font file data
   */
  ByteBuffer fileData();
}
