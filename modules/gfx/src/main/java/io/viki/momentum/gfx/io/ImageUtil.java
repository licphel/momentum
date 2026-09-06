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

package io.viki.momentum.gfx.io;

import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.internal.InternalApi;

import java.nio.ByteBuffer;

/**
 * Image data helpers for GPU uploads.
 */
@InternalApi
public final class ImageUtil {
  private ImageUtil() {
  }

  /**
   * Flips the pixel rows of {@code data} into a pooled direct buffer, so that the
   * first row (top) maps to the last row (bottom).
   *
   * <p>This pre-flip compensates for OpenGL's bottom-left texture origin, resulting
   * in an upright texture in GPU memory. The copy happens on the calling thread, so
   * the source buffer may be reused or released immediately after this call; the
   * returned buffer must be released via {@link DirectBufferPool#release(ByteBuffer)}
   * once the upload that consumes it has executed.
   *
   * @param data   the pixel rows in top-origin order
   * @param width  the image width in pixels
   * @param height the image height in pixels
   * @return a pooled, flipped buffer ready for upload
   */
  public static ByteBuffer pooledFlip(ByteBuffer data, int width, int height) {
    int base = data.position();
    int bpp = data.remaining() / (width * height);
    int rowSize = width * bpp;
    ByteBuffer dst = DirectBufferPool.acquire(data.remaining());
    ByteBuffer src = data.duplicate();
    for (int row = 0; row < height; row++) {
      int start = base + (height - 1 - row) * rowSize;
      src.position(start).limit(start + rowSize);
      dst.put(src);
    }
    dst.flip();
    return dst;
  }
}
