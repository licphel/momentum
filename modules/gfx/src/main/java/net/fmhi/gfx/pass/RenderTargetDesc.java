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

import net.fmhi.gfx.texture.TextureFormat;
import org.jspecify.annotations.Nullable;

/**
 * Creates a new {@code RenderTargetDesc} describing a render target to be created.
 *
 * @param width                  width in pixels (0 for swapchain)
 * @param height                 height in pixels (0 for swapchain)
 * @param colorAttachments       array of color attachment formats
 * @param depthStencilAttachment depth/stencil format, or {@code null}
 * @param samples                MSAA sample count (1 = no multisampling)
 * @param isSwapchain            {@code true} if this targets the swapchain
 */
public record RenderTargetDesc(int width, int height, TextureFormat[] colorAttachments,
                               @Nullable TextureFormat depthStencilAttachment, int samples, boolean isSwapchain) {
  /**
   * A swapchain target — dimensions come from the view.
   *
   * @return a swapchain target descriptor
   */
  public static RenderTargetDesc swapchain() {
    return new RenderTargetDesc(0, 0, new TextureFormat[] {TextureFormat.RGBA8}, TextureFormat.DEPTH24_STENCIL8, 1,
        true);
  }

  /**
   * An off-screen target with one RGBA8 color attachment and D24S8 depth/stencil.
   *
   * @param width  the width of offscreen target
   * @param height the height of offscreen target
   * @return an offscreen target descriptor
   */
  public static RenderTargetDesc offscreen(int width, int height) {
    return new RenderTargetDesc(width, height, new TextureFormat[] {TextureFormat.RGBA8},
        TextureFormat.DEPTH24_STENCIL8, 1, false);
  }
}
