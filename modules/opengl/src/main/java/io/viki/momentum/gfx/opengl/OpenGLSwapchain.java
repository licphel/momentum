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

package io.viki.momentum.gfx.opengl;

import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.internal.Handle;
import io.viki.momentum.internal.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

import static org.lwjgl.opengl.GL33.*;

/**
 * Swapchain render target wrapping the default framebuffer ({@code FBO 0}).
 *
 * <p>Obtain the singleton via {@link OpenGLDevice#getSwapchain()}.
 *
 * <p><b>Thread safety:</b> all GL work is submitted via
 * {@link OpenGLDevice#submit(Runnable)}. The listener list is a {@link CopyOnWriteArrayList}.
 */
@InternalApi
public final class OpenGLSwapchain implements RenderTarget, Handle {
  private final OpenGLDevice ctx;

  OpenGLSwapchain(OpenGLDevice ctx) {
    this.ctx = ctx;
  }

  @Override
  public void blit(RenderTarget target, int srcX, int srcY, int srcW, int srcH, int dstX, int dstY, int dstW,
                   int dstH, TextureFilter filter) {
    ctx.submit(() -> {
      OpenGLRenderTarget dst = (OpenGLRenderTarget) target;
      int glFilter = OpenGLUtils.textureFilter(filter);
      ctx.cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 0);
      ctx.cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, dst.fboHandle());
      glBlitFramebuffer(srcX, srcY, srcX + srcW, srcY + srcH, dstX, dstY, dstX + dstW, dstY + dstH,
          GL_COLOR_BUFFER_BIT, glFilter);
      ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
      ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
    });
  }

  @Override
  public int width() {
    if (ctx.host == null) {
      return 0;
    }
    return ctx.host.getWidth();
  }

  @Override
  public int height() {
    if (ctx.host == null) {
      return 0;
    }
    return ctx.host.getHeight();
  }

  @Override
  public void close() {
    // Swapchain is owned by the device
  }

  @Override
  public @Nullable Texture pin() {
    return null;
  }

  @Override
  public int handle(int slot) {
    return 0;
  }
}
