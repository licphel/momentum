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

package net.fmhi.gfx.opengl;

import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TextureDesc;
import net.fmhi.gfx.texture.TextureType;
import net.fmhi.math.Box3D;
import net.fmhi.util.Handle;
import net.fmhi.util.InternalApi;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * OpenGL 1D/2D/3D texture implementation.
 *
 * <p>The texture object is created and configured on the render thread during
 * construction. Pixel data uploads ({@link #submit}) and blits ({@link #blit}) are also enqueued to the render thread.
 *
 * <p>Mipmaps are generated automatically after upload if
 * {@link TextureDesc#mipLevels()} &gt; 1.
 *
 * <p><b>Thread safety:</b> all GL work is submitted via
 * {@link OpenGLDevice#submit(Runnable)}. Reads of {@link #desc()} and the convenience dimension methods are safe from
 * any thread.
 */
@InternalApi
public final class OpenGLTexture implements Texture, Handle {
  /**
   * The GL texture target ({@code GL_TEXTURE_1D/2D/3D}).
   */
  final int target;
  private final OpenGLDevice ctx;
  private final TextureDesc desc;
  /**
   * The GL texture handle (0 until created on the render thread).
   */
  int handle = 0;

  /**
   * Creates a new OpenGL texture from the given descriptor.
   *
   * <p>Texture creation and initial data upload are enqueued to the render
   * thread. If the descriptor includes {@link TextureDesc#initialBytes()}, they are uploaded immediately after
   * creation.
   *
   * @param ctx  the GL context
   * @param desc the texture dimensions, format, type, and optional initial data
   */
  OpenGLTexture(OpenGLDevice ctx, TextureDesc desc) {
    this.ctx = ctx;
    this.desc = desc;
    target = OpenGLUtils.textureTarget(desc.type());

    ctx.submit(() -> {
      handle = glGenTextures();
      ctx.cache.setTexture(0, target, handle);

      int[] fmt = OpenGLUtils.textureFormat(desc.format());
      int internal = fmt[0];
      int pixFmt = fmt[1];
      int pixType = fmt[2];

      ByteBuffer data = null;
      if (desc.initialBytes() != null) {
        byte[] flipped = flipVertically(desc.initialBytes(), desc.width(), desc.height());
        data = memAlloc(flipped.length);
        data.put(flipped).flip();
      }

      switch (desc.type()) {
        case TextureType.TEXTURE_1D -> glTexImage1D(target, 0, internal, desc.width(), 0, pixFmt, pixType, data);
        case TextureType.TEXTURE_2D ->
            glTexImage2D(target, 0, internal, desc.width(), desc.height(), 0, pixFmt, pixType, data);
        case TextureType.TEXTURE_3D ->
            glTexImage3D(target, 0, internal, desc.width(), desc.height(), desc.depth(), 0, pixFmt, pixType, data);
      }

      if (data != null) {
        memFree(data);
      }

      glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0);
      glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, desc.mipLevels() - 1);

      if (desc.mipLevels() > 1) {
        glGenerateMipmap(target);
      }

      ctx.cache.setTexture(0, target, 0);
    });
  }

  @Override
  public TextureDesc desc() {
    return desc;
  }

  @Override
  public void submit(byte[] bytes, Box3D region) {
    ctx.submit(() -> {
      ctx.cache.setTexture(0, target, handle);
      int[] fmt = OpenGLUtils.textureFormat(desc.format());
      int pixFmt = fmt[1];
      int pixType = fmt[2];
      int x = (int) region.minX();
      int y = (int) region.minY();
      int z = (int) region.minZ();
      int w = (int) region.width();
      int h = (int) region.height();
      int d = (int) region.depth();

      byte[] flipped = flipVertically(bytes, w, h);
      ByteBuffer bb = memAlloc(flipped.length);
      try {
        bb.put(flipped).flip();
        switch (desc.type()) {
          case TextureType.TEXTURE_1D -> glTexSubImage1D(target, 0, x, w, pixFmt, pixType, bb);
          case TextureType.TEXTURE_2D -> glTexSubImage2D(target, 0, x, y, w, h, pixFmt, pixType, bb);
          case TextureType.TEXTURE_3D -> glTexSubImage3D(target, 0, x, y, z, w, h, d, pixFmt, pixType, bb);
        }
      } finally {
        memFree(bb);
      }

      if (desc.mipLevels() > 1) {
        glGenerateMipmap(target);
      }
      ctx.cache.setTexture(0, target, 0);
    });
  }

  /**
   * Blits a region of this texture into another 2D texture using FBO blitting.
   *
   * <p>Creates temporary FBOs for the source and destination, attaches the
   * textures, performs the blit, and then cleans up the FBOs. Uses nearest-neighbor filtering.
   */
  @Override
  public void blit(Texture target2, int sx, int sy, int sw, int sh, int dx, int dy, int dw, int dh) {
    ctx.submit(() -> {
      int[] fbos = new int[2];
      fbos[0] = glGenFramebuffers();
      fbos[1] = glGenFramebuffers();
      try {
        ctx.cache.bindFramebuffer(GL_READ_FRAMEBUFFER, fbos[0]);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, handle, 0);

        OpenGLTexture dstTex = (OpenGLTexture) target2;
        ctx.cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, fbos[1]);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, dstTex.handle, 0);

        int srcH = OpenGLTexture.this.height();
        int dstH = dstTex.height();
        glBlitFramebuffer(sx, srcH - sy - sh, sx + sw, srcH - sy,
                          dx, dstH - dy - dh, dx + dw, dstH - dy,
                          GL_COLOR_BUFFER_BIT, GL_NEAREST);

        if (dstTex.desc.mipLevels() > 1) {
          ctx.cache.setTexture(0, dstTex.target, dstTex.handle);
          glGenerateMipmap(dstTex.target);
          ctx.cache.setTexture(0, dstTex.target, 0);
        }
      } finally {
        glDeleteFramebuffers(fbos[0]);
        glDeleteFramebuffers(fbos[1]);
        ctx.cache.bindFramebuffer(GL_FRAMEBUFFER, 0);
      }
    });
  }

  /**
   * Deletes the GL texture handle on the render thread.
   *
   * <p>Idempotent: safe to call multiple times.
   */
  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        glDeleteTextures(handle);
        handle = 0;
      }
    });
  }

  @Override
  public int handle(int slot) {
    return slot == 0 ? handle : target;
  }

  /**
   * Reverses row order so that the first row (top) maps to the last row (bottom) in the output.
   *
   * <p>This pre-flip compensates for OpenGL's bottom-left texture origin,
   * resulting in an upright texture in GPU memory.
   */
  private static byte[] flipVertically(byte[] data, int width, int height) {
    int bpp = data.length / (width * height);
    int rowSize = width * bpp;
    byte[] out = new byte[data.length];
    for (int row = 0; row < height; row++) {
      System.arraycopy(data, row * rowSize, out, (height - 1 - row) * rowSize, rowSize);
    }
    return out;
  }
}
