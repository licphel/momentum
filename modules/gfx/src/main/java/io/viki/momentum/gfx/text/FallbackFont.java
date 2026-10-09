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
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.util.Objects;

/**
 * Configurable default font, with a lazily initialized built-in fallback.
 *
 * <p>Call {@link #init(Device)} once before use; subsequent calls are no-ops.
 *
 * <p>Font selection is safely published. Configure size before creating text styles;
 * existing styles keep their font and size. Caller-provided fonts remain caller-owned.
 */
@InternalApi
public final class FallbackFont {
  private static volatile @Nullable Font builtin;
  private static volatile @Nullable FontInsta font;

  private FallbackFont() {
  }

  /**
   * Initializes all built-in GPU resources. Idempotent.
   *
   * @param device the graphics device
   */
  public static synchronized void init(Device device) {
    if (builtin != null) {
      return;
    }

    // Font init
    try (InputStream input = FallbackFont.class.getResourceAsStream("/font.ttf")) {
      if (input == null) {
        throw new GraphicsException("Builtin font resource /font.ttf is missing");
      }
      builtin = Font.open(device, ByteBuffer.wrap(input.readAllBytes()));
      if (builtin == null) {
        throw new GraphicsException("Cannot decode builtin font /font.ttf");
      }
      if (font == null) {
        font = FontInsta.of(Objects.requireNonNull(builtin), Font.DEFAULT_SIZE[0]);
      }
    } catch (Exception e) {
      throw new GraphicsException("Cannot initialize builtin font", e);
    }
  }

  /**
   * Sets the default font of default {@link TextFormat}.
   *
   * @param value font to set
   */
  public static synchronized void set(FontInsta value) {
    font = value;
  }

  /**
   * Sets the logical font size used by subsequently created default {@link TextFormat}s.
   *
   * <p>This controls layout and glyph destination sizes in the drawing coordinate system.
   * It does not change {@link Font#resolution() the font's rasterization resolution}, existing
   * text formats, or cached glyph textures. The framebuffer size additionally depends on the
   * active camera, viewport, and drawing transform.
   *
   * <p>For example, a font rasterized at 16 pixels can be drawn at a logical size of 8. With a
   * two-times canvas scale, one glyph texel covers one framebuffer pixel. A smaller logical
   * size therefore does not by itself imply lost pixel detail; the combined scale determines
   * whether texels are preserved, enlarged, or downsampled.
   *
   * <p>Configure this on the UI thread before creating text formats.
   *
   * @param size the default font size in logical drawing units
   */
  public static synchronized void setDefaultSize(float size) {
    FontInsta current = font;
    if (current != null) {
      font = current.resized(size);
    }
    Font.DEFAULT_SIZE[0] = size;
  }

  static FontInsta acquire() {
    FontInsta current = font;
    if (current == null) {
      throw new GraphicsException("Font not initialized");
    }
    return current;
  }
}
