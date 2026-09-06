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

package io.viki.momentum.gfx.text.freetype;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.text.Font;
import io.viki.momentum.gfx.text.FontMetrics;
import io.viki.momentum.gfx.text.raster.Glyph;
import io.viki.momentum.internal.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Size;

import java.nio.ByteBuffer;

import static org.lwjgl.util.freetype.FreeType.*;

/**
 * FreeType-backed font implementation, providing glyph rasterization via the FreeType library.
 *
 * <p>Loaded from a font file path on construction. The font must be {@link #close() closed}
 * to release native FreeType resources.
 */
@InternalApi
public final class FreetypeFont implements Font {
  /** The default rasterization resolution, in pixels. */
  private static final int DEFAULT_RESOLUTION = 64;
  private static final float BASIC_LINE_HEIGHT = 16.0F;

  private final FT_Face ftFace;
  private final long ftLib;
  private final FontMetrics metrics;
  private final ByteBuffer fileData;
  private final Device device;
  private boolean disposed;
  private @Nullable FreetypeGlyphCache glyphCache;
  private float resolution = DEFAULT_RESOLUTION;

  /**
   * Constructs a FreeType-based font with metrics computed from the specified font data.
   *
   * @param device    the graphics device (used for context management)
   * @param buffer    the raw font data (TrueType, OpenType, etc.) as a ByteBuffer
   * @param faceIndex the index of the face within the font file (typically 0 for the first face)
   * @throws NullPointerException if {@code device} or {@code buffer} is null
   * @throws RuntimeException     if FreeType initialization, face loading, or charmap selection fails
   * @throws AssertionError       if the FreeType size object is null
   */
  public FreetypeFont(Device device, ByteBuffer buffer, int faceIndex) {
    this.device = device;

    // Buf the font data first — FT_New_Memory_Face reads from this buffer.
    fileData = ByteBuffer.allocateDirect(buffer.capacity()).put(buffer).flip();

    try (MemoryStack stack = MemoryStack.stackPush()) {
      PointerBuffer libOut = stack.callocPointer(1);
      check(FT_Init_FreeType(libOut));
      ftLib = libOut.get(0);
      PointerBuffer faceOut = stack.callocPointer(1);
      check(FT_New_Memory_Face(ftLib, fileData, faceIndex, faceOut));
      ftFace = FT_Face.create(faceOut.get(0));
      FT_Select_Charmap(ftFace, FT_ENCODING_UNICODE);
      FT_Set_Pixel_Sizes(ftFace, 0, (int) BASIC_LINE_HEIGHT);
      // sz.metrics() values are 26.6 fixed-point pixels at BASIC_LINE_HEIGHT.
      // Divide by 64 → pixels, then by BASIC_LINE_HEIGHT → normalized ratio.
      float s = 1.0F / (BASIC_LINE_HEIGHT * 64.0F);
      FT_Size sz = ftFace.size();
      assert sz != null;
      float ascender = sz.metrics().ascender() * s;
      float descender = sz.metrics().descender() * s;
      float lineHeight = sz.metrics().height() * s;
      // underline metrics are in design units; convert via units_per_EM → normalized ratio.
      float upm = ftFace.units_per_EM();
      float ulPos = upm > 0 ? (ftFace.underline_position() / upm) : -0.1F;
      float ulThickness = upm > 0 ? (ftFace.underline_thickness() / upm) : 0.07F;
      metrics = new FontMetrics(ascender, descender, lineHeight, ulPos, ulThickness);
    }
  }

  private static void check(int err) {
    if (err != 0) {
      throw new RuntimeException("FT err " + err);
    }
  }

  private static void check(int err, String m) {
    if (err != 0) {
      throw new RuntimeException("FT: " + m);
    }
  }

  @Override
  public FontMetrics metrics() {
    return metrics;
  }

  @Override
  public boolean hasGlyph(int cp) {
    return FT_Get_Char_Index(ftFace, cp) != 0;
  }

  @Override
  public int getGlyphIndex(int cp) {
    return FT_Get_Char_Index(ftFace, cp);
  }

  @Override
  public void close() {
    if (disposed) {
      return;
    }
    disposed = true;
    FT_Done_Face(ftFace);
    FT_Done_Library(ftLib);
  }

  @Override
  public @Nullable Glyph rasterizeGlyph(int glyphIndex, int fontStyle) {
    if (glyphCache == null) {
      glyphCache = new FreetypeGlyphCache(device);
      glyphCache.setResolution((int) resolution);
    }
    return glyphCache.get(this, glyphIndex, fontStyle);
  }

  @Override
  public void setResolution(float res) {
    resolution = res;
  }

  @Override
  public float resolution() {
    return resolution;
  }

  @Override
  public ByteBuffer fileData() {
    return fileData;
  }

  /**
   * Returns the underlying FreeType face handle for low-level operations.
   *
   * @return the FreeType face handle
   */
  public FT_Face ftFaceRaw() {
    return ftFace;
  }
}
