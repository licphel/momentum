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

import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.gfx.io.ImageUtil;
import io.viki.momentum.gfx.pass.RenderTarget;
import io.viki.momentum.gfx.pipe.Scissor;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureDesc;
import io.viki.momentum.gfx.texture.TextureFilter;
import io.viki.momentum.math.Cube;
import io.viki.momentum.util.Handle;
import io.viki.momentum.util.InternalApi;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Off-screen render target backed by an OpenGL FBO with an RGBA8 color attachment and a depth24/stencil8 render
 * buffer.
 *
 * <p>For the swapchain (screen), use {@link OpenGLSwapchain}.
 *
 * <p><b>Thread safety:</b> all GL work is submitted via
 * {@link OpenGLDevice#submit(Runnable)}.
 */
@InternalApi
public final class OpenGLRenderTarget implements RenderTarget {
  private final OpenGLDevice ctx;
  private final int fboWidth;
  private final int fboHeight;
  private int handle;
  private int colorTex;
  private int depthRbo;

  /**
   * Creates an off-screen FBO.
   *
   * @param ctx    the GL device
   * @param width  the FBO width in pixels
   * @param height the FBO height in pixels
   * @throws RuntimeException if the FBO is incomplete after assembly
   */
  OpenGLRenderTarget(OpenGLDevice ctx, int width, int height) {
    this.ctx = ctx;
    this.fboWidth = width;
    this.fboHeight = height;

    ctx.submit(() -> {
      colorTex = glGenTextures();
      ctx.cache.setTexture(0, GL_TEXTURE_2D, colorTex);
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
      ctx.cache.setTexture(0, GL_TEXTURE_2D, 0);

      depthRbo = glGenRenderbuffers();
      glBindRenderbuffer(GL_RENDERBUFFER, depthRbo);
      glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
      glBindRenderbuffer(GL_RENDERBUFFER, 0);

      handle = glGenFramebuffers();
      ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, handle);
      glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTex, 0);
      glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthRbo);

      int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
      ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
      if (status != GL_FRAMEBUFFER_COMPLETE) {
        throw new RuntimeException("OpenGLRenderTarget: FBO incomplete, status=0x" + Integer.toHexString(status));
      }
    });
  }

  int fboHandle() {
    return handle;
  }

  int colorTexHandle() {
    return colorTex;
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
        cache.bindFramebuffer(GL_READ_FRAMEBUFFER, handle);
        cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, dst.handle);
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
    return fboWidth;
  }

  @Override
  public int height() {
    return fboHeight;
  }

  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        glDeleteFramebuffers(handle);
        handle = 0;
      }
      if (colorTex != 0) {
        glDeleteTextures(colorTex);
        colorTex = 0;
      }
      if (depthRbo != 0) {
        glDeleteRenderbuffers(depthRbo);
        depthRbo = 0;
      }
    });
  }

  @Override
  public Texture pin() {
    return new FboTexture();
  }

  /**
   * Lightweight Texture adapter wrapping the FBO's color attachment.
   * Does NOT own the GL handle — close() is a no-op.
   */
  @InternalApi
  public final class FboTexture implements Texture, Handle {
    @Override
    public TextureDesc desc() {
      return new TextureDesc.Builder().width(fboWidth).height(fboHeight).build();
    }

    @Override
    public int width() {
      return fboWidth;
    }

    @Override
    public int height() {
      return fboHeight;
    }

    @Override
    public void submit(ByteBuffer data, Cube region) {
      int x = (int) region.minX();
      int y = (int) region.minY();
      int w = (int) region.width();
      int h = (int) region.height();
      // snapshot + flip on the calling thread (same convention as
      // OpenGLTexture.submit); the GL work runs later on the render thread
      ByteBuffer flipped = ImageUtil.pooledFlip(data, w, h);
      OpenGLRenderTarget.this.ctx.submit(() -> {
        OpenGLCache c = OpenGLRenderTarget.this.ctx.cache;
        try {
          c.setTexture(0, GL_TEXTURE_2D, colorTex);
          // Same convention as OpenGLTexture.submit: API data is top-origin,
          // flip to GL row order and flip the destination Y. A full-target
          // upload (y=0, h=height) is unaffected.
          glTexSubImage2D(GL_TEXTURE_2D, 0, x, fboHeight - y - h, w, h, GL_RGBA,
              GL_UNSIGNED_BYTE, flipped);
        } finally {
          DirectBufferPool.release(flipped);
        }
        c.setTexture(0, GL_TEXTURE_2D, 0);
      });
    }

    @Override
    public void blit(Texture target, int srcX, int srcY, int srcW, int srcH,
                     int dstX, int dstY, int dstW, int dstH) {
      OpenGLRenderTarget.this.ctx.submit(() -> {
        int srcHandle = colorTex;
        int dstHandle = target instanceof FboTexture ft ? ft.handle(0) : 0;
        if (dstHandle == 0 && target instanceof OpenGLTexture ot) {
          dstHandle = ot.handle;
        }
        if (dstHandle == 0) {
          return;
        }
        OpenGLCache cache = OpenGLRenderTarget.this.ctx.cache;
        int previousReadFramebuffer = cache.fboR;
        int previousDrawFramebuffer = cache.fboW;
        boolean previousScissorEnabled = cache.scissorTest;
        int readFbo = 0;
        int drawFbo = 0;
        try {
          // Use a temporary FBO pair to blit textures. The scissor test is
          // disabled because it clips framebuffer blits as well as draws.
          cache.setScissorEnabled(false);
          readFbo = glGenFramebuffers();
          drawFbo = glGenFramebuffers();
          cache.bindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
          glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, srcHandle, 0);
          cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
          glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, dstHandle, 0);
          glBlitFramebuffer(srcX, fboHeight - srcY - srcH, srcX + srcW, fboHeight - srcY,
              dstX, target.height() - dstY - dstH, dstX + dstW, target.height() - dstY,
              GL_COLOR_BUFFER_BIT, GL_LINEAR);
        } finally {
          if (readFbo != 0) {
            glDeleteFramebuffers(readFbo);
          }
          if (drawFbo != 0) {
            glDeleteFramebuffers(drawFbo);
          }
          cache.bindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer);
          cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
          cache.setScissorEnabled(previousScissorEnabled);
        }
      });
    }

    @Override
    public void close() {
    }

    @Override
    public int handle(int slot) {
      return slot == 0 ? colorTex : GL_TEXTURE_2D;
    }
  }
}
