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

package net.momentum.gfx.opengl;

import net.momentum.gfx.texture.Sampler;
import net.momentum.gfx.texture.SamplerDesc;
import net.momentum.util.internal.Handle;
import net.momentum.util.internal.InternalApi;

import static org.lwjgl.opengl.GL33.*;

/**
 * OpenGL sampler object that controls texture filtering, wrapping, and LOD parameters.
 *
 * <p>The sampler is created and configured on the render thread during
 * construction. All parameters from the {@link SamplerDesc} are applied immediately via {@code glSamplerParameteri/f}.
 *
 * <p>Anisotropic filtering is enabled when {@link SamplerDesc#anisotropyLevel()}
 * &gt; 1, using the {@code GL_TEXTURE_MAX_ANISOTROPY} extension parameter.
 *
 * <p><b>Thread safety:</b> immutable after construction. The descriptor
 * can be read from any thread. {@link #close()} submits work to the render thread.
 */
@InternalApi
public final class OpenGLSampler implements Sampler, Handle {
  /**
   * {@code GL_TEXTURE_MAX_ANISOTROPY} from the {@code GL_EXT_texture_filter_anisotropic} extension.
   */
  private static final int GL_TEXTURE_MAX_ANISOTROPY = 0x84FE;

  private final OpenGLDevice ctx;
  private final SamplerDesc desc;
  /**
   * The GL sampler handle (0 until created on the render thread).
   */
  int handle = 0;

  /**
   * Creates a new OpenGL sampler from the given descriptor.
   *
   * <p>GL object creation and parameter setup are enqueued to the render thread.
   *
   * @param ctx  the GL context
   * @param desc the sampler parameters (filtering, wrapping, LOD, anisotropy)
   */
  OpenGLSampler(OpenGLDevice ctx, SamplerDesc desc) {
    this.ctx = ctx;
    this.desc = desc;

    ctx.submit(() -> {
      handle = glGenSamplers();
      glSamplerParameteri(handle, GL_TEXTURE_WRAP_S, OpenGLUtils.textureWrap(desc.wrapX()));
      glSamplerParameteri(handle, GL_TEXTURE_WRAP_T, OpenGLUtils.textureWrap(desc.wrapY()));
      glSamplerParameteri(handle, GL_TEXTURE_WRAP_R, OpenGLUtils.textureWrap(desc.wrapZ()));
      glSamplerParameteri(handle, GL_TEXTURE_MAG_FILTER, OpenGLUtils.textureFilter(desc.magFilter()));
      glSamplerParameteri(handle, GL_TEXTURE_MIN_FILTER, OpenGLUtils.textureFilter(desc.minFilter()));
      glSamplerParameterf(handle, GL_TEXTURE_LOD_BIAS, desc.lodBias());
      glSamplerParameterf(handle, GL_TEXTURE_MIN_LOD, desc.minLod());
      glSamplerParameterf(handle, GL_TEXTURE_MAX_LOD, desc.maxLod());
      if (desc.anisotropyLevel() > 1.0F) {
        glSamplerParameterf(handle, GL_TEXTURE_MAX_ANISOTROPY, desc.anisotropyLevel());
      }
    });
  }

  @Override
  public SamplerDesc desc() {
    return desc;
  }

  /**
   * Deletes the GL sampler handle on the render thread.
   *
   * <p>Idempotent: safe to call multiple times.
   */
  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        glDeleteSamplers(handle);
        handle = 0;
      }
    });
  }

  @Override
  public int handle(int slot) {
    return handle;
  }
}
