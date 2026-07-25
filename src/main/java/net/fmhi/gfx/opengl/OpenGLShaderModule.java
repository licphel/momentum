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

import net.fmhi.gfx.shader.ShaderModule;
import net.fmhi.gfx.shader.ShaderModuleDesc;
import net.fmhi.util.Handle;
import net.fmhi.util.InternalApi;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.opengl.GL33.*;

/**
 * Compiles a single GLSL shader stage.
 */
@InternalApi
public final class OpenGLShaderModule implements ShaderModule, Handle {
  private final OpenGLDevice ctx;
  private final ShaderModuleDesc desc;
  int handle = 0;
  private @Nullable String compilationError;

  OpenGLShaderModule(OpenGLDevice ctx, ShaderModuleDesc desc) {
    this.ctx = ctx;
    this.desc = desc;

    ctx.submit(() -> {
      handle = glCreateShader(OpenGLUtils.shaderType(desc.type()));
      glShaderSource(handle, desc.code());
      glCompileShader(handle);

      if (glGetShaderi(handle, GL_COMPILE_STATUS) == GL_FALSE) {
        compilationError = glGetShaderInfoLog(handle);
        glDeleteShader(handle);
        handle = 0;
      }
    });
  }

  @Override
  public ShaderModuleDesc desc() {
    return desc;
  }

  @Override
  public @Nullable String checkCompilationError() {
    return compilationError;
  }

  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        glDeleteShader(handle);
        handle = 0;
      }
    });
  }

  @Override
  public int handle(int slot) {
    return handle;
  }
}
