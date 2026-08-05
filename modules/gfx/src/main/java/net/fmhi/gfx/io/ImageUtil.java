package net.fmhi.gfx.io;

import net.fmhi.gfx.DirectBufferPool;
import net.fmhi.util.InternalApi;

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
