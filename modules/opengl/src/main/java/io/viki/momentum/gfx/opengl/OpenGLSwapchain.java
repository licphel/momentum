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
import io.viki.momentum.gfx.pipe.Scissor;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureDesc;
import io.viki.momentum.math.Cube;
import org.lwjgl.system.MemoryUtil;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.util.Handle;
import io.viki.momentum.util.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.ByteBuffer;

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
  private static final int RGBA_BYTES = 4;
  private final OpenGLDevice ctx;

  OpenGLSwapchain(OpenGLDevice ctx) {
    this.ctx = ctx;
  }

  @Override
  public void blit(RenderTarget target, int srcX, int srcY, int srcW, int srcH, int dstX, int dstY, int dstW,
                   int dstH, TextureFilter filter, Scissor scissor) {
    ctx.submit(() -> {
      OpenGLRenderTarget dst = (OpenGLRenderTarget) target;
      int glFilter = OpenGLUtils.textureFilter(filter);
      OpenGLCache cache = ctx.cache;
      int previousReadFramebuffer = cache.fboR;
      int previousDrawFramebuffer = cache.fboW;
      boolean previousScissorEnabled = cache.scissorTest;
      int previousScissorX = cache.scissorRect[0];
      int previousScissorY = cache.scissorRect[1];
      int previousScissorWidth = cache.scissorRect[2];
      int previousScissorHeight = cache.scissorRect[3];
      try {
        cache.setScissor(scissor.x(), target.height() - scissor.y() - scissor.height(),
            scissor.width(), scissor.height(), scissor.enable());
        cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, dst.fboHandle());
        glBlitFramebuffer(srcX, srcY, srcX + srcW, srcY + srcH, dstX, dstY, dstX + dstW, dstY + dstH, GL_COLOR_BUFFER_BIT, glFilter);
      } finally {
        cache.bindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        cache.setScissor(previousScissorX, previousScissorY, previousScissorWidth,
            previousScissorHeight, previousScissorEnabled);
      }
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

  /** Queues an owned RGBA8 snapshot of the back buffer; execute before reading and close after use. */
  @Override
  public @Nullable Texture pin() {
    int width = width();
    int height = height();
    if (width <= 0 || height <= 0) {
      return null;
    }
    OpenGLTexture snapshot = new OpenGLTexture(ctx, TextureDesc.of(width, height));
    ctx.submit(() -> capture(snapshot));
    return snapshot;
  }

  @SuppressWarnings("try")
  private void capture(OpenGLTexture snapshot) {
    int width = snapshot.width();
    int height = snapshot.height();
    int rowBytes = Math.multiplyExact(width, RGBA_BYTES);
    ByteBuffer pixels = MemoryUtil.memAlloc(Math.multiplyExact(rowBytes, height));
    int previousReadFramebuffer = ctx.cache.fboR;

    try (GLReadRaii ignored = new GLReadRaii()) {
      ctx.cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 0);
      int previousReadBuffer = glGetInteger(GL_READ_BUFFER);
      try {
        glReadBuffer(GL_BACK);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
      } finally {
        glReadBuffer(previousReadBuffer);
      }
      byte[] topDown = new byte[pixels.capacity()];
      for (int y = 0; y < height; y++) {
        pixels.get((height - y - 1) * rowBytes, topDown, y * rowBytes, rowBytes);
      }
      snapshot.submit(topDown, new Cube(0, 0, 0, width, height, 1));
    } finally {
      ctx.cache.bindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer);
      MemoryUtil.memFree(pixels);
    }
  }

  static class GLReadRaii implements AutoCloseable {
    private final int alignment = glGetInteger(GL_PACK_ALIGNMENT);
    private final int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
    private final int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
    private final int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
    private final int buffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);

    GLReadRaii() {
      glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
      glPixelStorei(GL_PACK_ALIGNMENT, 1);
      glPixelStorei(GL_PACK_ROW_LENGTH, 0);
      glPixelStorei(GL_PACK_SKIP_ROWS, 0);
      glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
    }

    @Override
    public void close() {
      glPixelStorei(GL_PACK_ALIGNMENT, alignment);
      glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
      glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
      glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
      glBindBuffer(GL_PIXEL_PACK_BUFFER, buffer);
    }
  }

  @Override
  public int handle(int slot) {
    return 0;
  }
}
