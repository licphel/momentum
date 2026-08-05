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

package net.fmhi.gfx.pass;

import net.fmhi.gfx.texture.FragileTexture;
import net.fmhi.gfx.texture.TextureFilter;

/**
 * A framebuffer target that receives rendered output.
 *
 * <p>A {@code RenderTarget} may represent either:
 * <ul>
 *   <li>The default swapchain view (the visible window), or
 *   <li>An off-screen framebuffer object (FBO) with gradient and depth/stencil
 *       attachments.
 * </ul>
 *
 * @see RenderPass
 */
public interface RenderTarget extends FragileTexture, AutoCloseable {
  /**
   * Blits (copies) a rectangular region of this render target into another.
   *
   * <p>Both the source and destination must be render targets. The filter
   * only applies when the regions differ in size; for 1:1 copies use {@link TextureFilter#NEAREST} to avoid unnecessary
   * sampling overhead.
   *
   * @param target the destination render target
   * @param srcX   left edge of the source region
   * @param srcY   top edge of the source region
   * @param srcW   width of the source region
   * @param srcH   height of the source region
   * @param dstX   left edge of the destination region
   * @param dstY   top edge of the destination region
   * @param dstW   width of the destination region
   * @param dstH   height of the destination region
   * @param filter filtering mode when scaling (ignored for 1:1 copies)
   */
  void blit(RenderTarget target, int srcX, int srcY, int srcW, int srcH, int dstX, int dstY, int dstW, int dstH,
            TextureFilter filter);

  /**
   * Returns the width of this render target in physical pixels, or {@code 0} if unknown.
   *
   * <p>The swapchain target returns the current framebuffer width; off-screen targets return the
   * width passed at creation time.
   *
   * @return the width of this render target
   */
  default int width() {
    return 0;
  }

  /**
   * Returns the height of this render target in physical pixels, or {@code 0} if unknown.
   *
   * <p>The swapchain target returns the current framebuffer height; off-screen targets return the
   * height passed at creation time.
   *
   * @return the height of this render target
   */
  default int height() {
    return 0;
  }

  @Override
  void close();
}
