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

package net.fmhi.gfx.text.freetype;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.text.Font;
import net.fmhi.gfx.text.raster.Glyph;
import net.fmhi.gfx.texture.FragileTexture;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TextureDesc;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.Box2D;
import net.fmhi.math.Box3D;
import net.fmhi.util.InternalApi;
import org.jspecify.annotations.Nullable;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.util.freetype.FreeType.*;

/**
 * Caches rasterized glyphs in a dynamically-growing texture atlas.
 *
 * <p>Each glyph is rasterized on demand via FreeType and packed into the atlas.
 * Re-requesting the same glyph returns the cached entry without re-rasterization.
 *
 * <p>The atlas is backed by a {@link FragileTexture} so that all cached {@link TexturePart}s
 * remain valid when the atlas grows — no cache invalidation or rebinding required.
 *
 * <p>Must be {@linkplain #close() closed} to release the underlying texture atlas.
 */
@InternalApi
public final class FreetypeGlyphCache implements AutoCloseable {
  private final Device device;
  private final Map<GlyphKey, @Nullable Glyph> cache = new HashMap<>();
  private @Nullable GrowableAtlas atlas;
  private int cursorX;
  private int cursorY;
  private int rowHeight;
  private int size;
  private int resolution = 0;
  private boolean disposed;

  /**
   * Creates a new glyph cache for the given device.
   *
   * @param device the graphics device used for texture allocation
   */
  public FreetypeGlyphCache(Device device) {
    this.device = device;
  }

  /**
   * Sets the resolution used for glyph rasterization.
   *
   * @param r the resolution in pixels
   */
  public void setResolution(int r) {
    this.resolution = r;
  }

  /**
   * Returns the current rasterization resolution.
   *
   * @return the resolution in pixels
   */
  public int resolution() {
    return resolution;
  }

  /**
   * Returns the cached glyph for the given font, glyph index, and fmt, rasterizing if necessary.
   *
   * @param font       the font to rasterize from
   * @param glyphIndex the glyph index within the font
   * @param fontStyle  the fmt bitmask, combining flags
   * @return the cached glyph, or {@code null} if the glyph has no visual representation
   */
  public @Nullable Glyph get(FreetypeFont font, int glyphIndex, int fontStyle) {
    if (glyphIndex == 0) {
      return null; // glyph 0 is the .notdef sentinel — control chars, unmapped codepoints, etc.
    }
    GlyphKey key = new GlyphKey(font, resolution, glyphIndex, fontStyle);
    if (cache.containsKey(key)) {
      return cache.get(key);
    }
    Glyph c = rasterize(font.ftFaceRaw(), glyphIndex, fontStyle);
    cache.put(key, c);
    return c;
  }

  private @Nullable Glyph rasterize(FT_Face ftFace, int glyphIndex, int fontStyle) {
    FT_Set_Pixel_Sizes(ftFace, 0, resolution);
    FT_Load_Glyph(ftFace, glyphIndex, FT_LOAD_DEFAULT);
    FT_GlyphSlot slot = ftFace.glyph();

    assert slot != null;
    if ((fontStyle & Font.BOLD) != 0) {
      FT_GlyphSlot_Embolden(slot);
    }
    if ((fontStyle & Font.ITALIC) != 0) {
      FT_GlyphSlot_Oblique(slot);
    }

    FT_Render_Glyph(slot, FT_RENDER_MODE_NORMAL);
    FT_Bitmap bitmap = slot.bitmap();

    int w = bitmap.width();
    int h = bitmap.rows();

    // For oblique glyphs FreeType skews the bitmap without updating x_advance, so the rightmost
    // pixels spill past the advance box. Pad the advance to cover the actual bitmap right edge.
    float advance = slot.advance().x() / 64.0F;
    if ((fontStyle & Font.ITALIC) != 0) {
      float bitmapRight = slot.bitmap_left() + w;
      if (bitmapRight > advance) {
        advance = bitmapRight;
      }
    }
    TexturePart tp;

    if (w > 0 && h > 0) {
      int pitch = Math.abs(bitmap.pitch());
      ByteBuffer buf = bitmap.buffer(pitch * h);
      assert buf != null;
      byte[] rgba = new byte[w * h * 4];
      if (bitmap.pitch() < 0) {
        for (int row = 0; row < h; row++) {
          for (int col = 0; col < w; col++) {
            int src = (h - 1 - row) * pitch + col;
            int dst = (row * w + col) * 4;
            byte grey = buf.get(src);
            rgba[dst] = (byte) 255;
            rgba[dst + 1] = (byte) 255;
            rgba[dst + 2] = (byte) 255;
            rgba[dst + 3] = grey;
          }
        }
      } else {
        for (int i = 0; i < w * h; i++) {
          byte grey = buf.get(i);
          int off = i * 4;
          rgba[off] = (byte) 255;
          rgba[off + 1] = (byte) 255;
          rgba[off + 2] = (byte) 255;
          rgba[off + 3] = grey;
        }
      }

      ensureAtlas(w, h);

      assert atlas != null;
      atlas.pin().submit(rgba, Box3D.create(cursorX, cursorY, 0, w, h, 1));
      tp = new TexturePart(atlas, Box2D.create(cursorX, cursorY, w, h));
      cursorX += w;
      rowHeight = Math.max(rowHeight, h);

      return new Glyph(tp, slot.bitmap_left(), slot.bitmap_top(), advance);
    }

    return null;
  }

  /**
   * Ensures the atlas has space for a glyph of the given size, growing if needed.
   */
  private void ensureAtlas(int w, int h) {
    if (atlas == null) {
      size = 512;
      atlas = new GrowableAtlas(device.getTexture(new TextureDesc.Builder().width(size).height(size).build()));
    }
    if (cursorX + w > size) {
      cursorX = 0;
      cursorY += rowHeight;
      rowHeight = 0;
    }
    while (cursorY + h > size) {
      grow();
    }
  }

  /** Doubles the atlas — GrowableAtlas.current is updated in place, all TextureParts see new texture automatically. */
  private void grow() {
    int newSize = size * 2;
    Texture newTex = device.getTexture(new TextureDesc.Builder().width(newSize).height(newSize).build());
    assert atlas != null;
    atlas.current.blit(newTex, 0, 0, size, size, 0, 0, size, size);
    atlas.current.close();
    atlas.current = newTex;
    size = newSize;
  }

  @Override
  public void close() {
    if (disposed) {
      return;
    }
    disposed = true;
    if (atlas != null) {
      atlas.current.close();
    }
  }

  // Stable cache key: font + resolution + glyphIndex + fontStyle avoids identityHashCode collisions.
  private record GlyphKey(Font font, int resolution, int glyphIndex, int fontStyle) {
  }

  // FragileTexture impl: holds a mutable Texture pointer. TextureParts pin() this to get current atlas.
  private static final class GrowableAtlas implements FragileTexture {
    Texture current;

    GrowableAtlas(Texture initial) {
      this.current = initial;
    }

    @Override
    public Texture pin() {
      return current;
    }
  }
}
