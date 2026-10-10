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

import io.viki.momentum.util.perf.Analysis;

import io.viki.momentum.gfx.DirectBufferPool;
import io.viki.momentum.gfx.io.ImageUtil;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TextureDesc;
import io.viki.momentum.gfx.texture.TextureFormat;
import io.viki.momentum.gfx.texture.TextureType;
import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.math.Cube;
import io.viki.momentum.util.Handle;
import io.viki.momentum.util.InternalApi;
import io.viki.momentum.util.Pool;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

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
  private static final ByteBuffer EMPTY_UPLOAD = ByteBuffer.allocate(0);
  /**
   * The GL texture target ({@code GL_TEXTURE_1D/2D/3D}).
   */
  final int target;
  private final OpenGLDevice ctx;
  private final TextureDesc desc;
  private final int[] format;
  private final Pool<UploadCommand> uploads = new Pool<>();
  private final byte[] pixels;
  private final int pixelStride;
  /**
   * The GL texture handle (0 until created on the render thread).
   */
  int handle = 0;
  private volatile boolean pixelsValid = true;

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
    format = OpenGLUtils.textureFormat(desc.format());
    pixelStride = readablePixelStride(desc.format());
    pixels = createPixelMirror(desc, pixelStride);
    target = OpenGLUtils.textureTarget(desc.type());

    ctx.submit(() -> {
      handle = glGenTextures();
      ctx.cache.setTexture(0, target, handle);

      int[] fmt = format;
      int internal = fmt[0];
      int pixFmt = fmt[1];
      int pixType = fmt[2];

      ByteBuffer data = null;
      if (desc.initialBytes() != null) {
        data = ImageUtil.pooledFlip(ByteBuffer.wrap(desc.initialBytes()), desc.width(), desc.height(),
            DirectBufferPool.MEDIUM);
      }

      try {
        switch (desc.type()) {
          case TextureType.TEXTURE_1D -> glTexImage1D(target, 0, internal, desc.width(), 0, pixFmt, pixType, data);
          case TextureType.TEXTURE_2D ->
              glTexImage2D(target, 0, internal, desc.width(), desc.height(), 0, pixFmt, pixType, data);
          case TextureType.TEXTURE_3D ->
              glTexImage3D(target, 0, internal, desc.width(), desc.height(), desc.depth(), 0, pixFmt, pixType, data);
        }
      } finally {
        if (data != null) {
          DirectBufferPool.MEDIUM.release(data);
        }
      }

      glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0);
      glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, desc.mipLevels() - 1);

      if (desc.mipLevels() > 1) {
        glGenerateMipmap(target);
      }

      ctx.cache.setTexture(0, target, 0);
    });
  }

  private static int readablePixelStride(TextureFormat format) {
    return switch (format) {
      case RED8 -> 1;
      case RG8 -> 2;
      case RGB8 -> 3;
      case RGBA8 -> 4;
      default -> 0;
    };
  }

  private static byte[] createPixelMirror(TextureDesc desc, int stride) {
    if (stride == 0) {
      return new byte[0];
    }
    int size = Math.multiplyExact(Math.multiplyExact(
        Math.multiplyExact(desc.width(), desc.height()), desc.depth()), stride);
    byte[] result = new byte[size];
    byte[] initial = desc.initialBytes();
    if (initial != null) {
      if (initial.length != size) {
        throw new IllegalArgumentException("Initial texture byte count " + initial.length
            + " does not match " + desc.width() + "x" + desc.height() + "x"
            + desc.depth() + " " + desc.format() + " texture: " + size);
      }
      System.arraycopy(initial, 0, result, 0, size);
    }
    return result;
  }

  @Override
  public TextureDesc desc() {
    return desc;
  }

  @Override
  public int pixel(int x, int y, int z) {
    if (x < 0 || x >= width() || y < 0 || y >= height() || z < 0 || z >= depth()) {
      throw new IndexOutOfBoundsException("Texture pixel is outside " + width() + "x" + height() + "x" + depth() + ": " + x + ", " + y + ", " + z);
    }
    if (pixelStride == 0 || !pixelsValid) {
      throw new UnsupportedOperationException("Texture pixels are unavailable for format " + desc.format());
    }
    int index = ((z * height() + y) * width() + x) * pixelStride;
    synchronized (pixels) {
      int red = pixels[index] & 0xFF;
      int green = pixelStride >= 2 ? pixels[index + 1] & 0xFF : 0;
      int blue = pixelStride >= 3 ? pixels[index + 2] & 0xFF : 0;
      int alpha = pixelStride >= 4 ? pixels[index + 3] & 0xFF : 0xFF;
      return Color.packRgba8(red, green, blue, alpha);
    }
  }

  @Override
  public void submit(ByteBuffer bytes, Cube region) {
    queueUpload(bytes, region, false);
  }

  @Override
  public void replace(ByteBuffer bytes) {
    queueUpload(bytes, Cube.of(0, 0, 0, width(), height(), depth()), true);
  }

  private void queueUpload(ByteBuffer bytes, Cube region, boolean replacement) {
    Analysis.start("texture.copyUploadDataAndQueue");
    try {
      int x = (int) region.minX();
      int y = (int) region.minY();
      int z = (int) region.minZ();
      int w = (int) region.width();
      int h = (int) region.height();
      int d = (int) region.depth();

      updatePixelMirror(bytes, x, y, z, w, h, d);

      // snapshot + flip on the calling thread: the caller may reuse or release its
      // buffer immediately; the GL work runs later on the render thread
      ByteBuffer bb = ImageUtil.pooledFlip(bytes, w, h, DirectBufferPool.MEDIUM);
      UploadCommand command = uploads.poll();
      if (command == null) {
        command = new UploadCommand(this);
      }
      command.bytes = bb;
      command.x = x;
      command.y = y;
      command.z = z;
      command.width = w;
      command.height = h;
      command.depth = d;
      command.replacement = replacement;
      ctx.submit(command);
    } finally {
      Analysis.end("texture.copyUploadDataAndQueue");
    }
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
      OpenGLCache cache = ctx.cache;
      int previousReadFramebuffer = cache.fboR;
      int previousDrawFramebuffer = cache.fboW;
      boolean previousScissorEnabled = cache.scissorTest;
      int[] fbos = new int[2];
      fbos[0] = glGenFramebuffers();
      fbos[1] = glGenFramebuffers();
      try {
        // Framebuffer blits are affected by GL_SCISSOR_TEST. UI rendering may
        // leave a control-sized scissor active, which would otherwise copy only
        // part of a growing texture atlas.
        cache.setScissorEnabled(false);
        cache.bindFramebuffer(GL_READ_FRAMEBUFFER, fbos[0]);
        glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, handle, 0);

        // If it's not a OpenGLTexture, just let it crash!
        OpenGLTexture dstTex = (OpenGLTexture) target2;
        cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, fbos[1]);
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
        if (previousReadFramebuffer == previousDrawFramebuffer) {
          cache.bindFramebuffer(GL_FRAMEBUFFER, previousReadFramebuffer);
        } else {
          cache.bindFramebuffer(GL_READ_FRAMEBUFFER, previousReadFramebuffer);
          cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
        cache.setScissorEnabled(previousScissorEnabled);
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

  private void updatePixelMirror(ByteBuffer source, int x, int y, int z,
                                 int width, int height, int depth) {
    if (pixelStride == 0 || !pixelsValid) {
      return;
    }
    int rowBytes = width * pixelStride;
    int requiredBytes = rowBytes * height * depth;
    ByteBuffer copy = source.duplicate();
    if (copy.remaining() < requiredBytes) {
      pixelsValid = false;
      return;
    }
    synchronized (pixels) {
      for (int layer = 0; layer < depth; layer++) {
        for (int row = 0; row < height; row++) {
          int destination = (((z + layer) * desc.height() + y + row) * desc.width() + x)
              * pixelStride;
          copy.get(pixels, destination, rowBytes);
        }
      }
    }
  }

  private static final class UploadCommand implements Runnable {
    private final OpenGLTexture texture;
    private ByteBuffer bytes = EMPTY_UPLOAD;
    private int x, y, z, width, height, depth;
    private boolean replacement;

    private UploadCommand(OpenGLTexture texture) {
      this.texture = texture;
    }

    @Override
    public void run() {
      try {
        texture.ctx.cache.setTexture(0, texture.target, texture.handle);
        int[] fmt = texture.format;
        int glY = texture.desc.height() - y - height;
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        String timing = replacement ? "gl.textureReplace" : "gl.textureSubImage";
        Analysis.start(timing);
        try {
          if (replacement) {
            switch (texture.desc.type()) {
              case TEXTURE_1D -> glTexImage1D(texture.target, 0, fmt[0], width, 0, fmt[1], fmt[2], bytes);
              case TEXTURE_2D -> glTexImage2D(texture.target, 0, fmt[0], width, height, 0, fmt[1], fmt[2], bytes);
              case TEXTURE_3D -> glTexImage3D(texture.target, 0, fmt[0], width, height, depth, 0, fmt[1], fmt[2], bytes);
            }
          } else {
            switch (texture.desc.type()) {
              case TextureType.TEXTURE_1D -> glTexSubImage1D(texture.target, 0, x, width, fmt[1], fmt[2], bytes);
              case TextureType.TEXTURE_2D ->
                  glTexSubImage2D(texture.target, 0, x, glY, width, height, fmt[1], fmt[2], bytes);
              case TextureType.TEXTURE_3D ->
                  glTexSubImage3D(texture.target, 0, x, glY, z, width, height, depth, fmt[1], fmt[2], bytes);
            }
          }
        } finally {
          Analysis.end(timing);
        }
        if (texture.desc.mipLevels() > 1) {
          glGenerateMipmap(texture.target);
        }
      } finally {
        try {
          texture.ctx.cache.setTexture(0, texture.target, 0);
        } finally {
          DirectBufferPool.MEDIUM.release(bytes);
          bytes = EMPTY_UPLOAD;
          x = y = z = width = height = depth = 0;
          replacement = false;
          texture.uploads.release(this);
        }
      }
    }
  }
}
