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

import net.momentum.gfx.shader.ShaderModule;
import net.momentum.gfx.shader.ShaderProgram;
import net.momentum.util.internal.Handle;
import net.momentum.util.internal.InternalApi;
import org.jspecify.annotations.Nullable;

import static org.lwjgl.opengl.GL33.*;

/**
 * Links one or more compiled shader modules into an OpenGL program.
 */
@InternalApi
public final class OpenGLShaderProgram implements ShaderProgram, Handle {
  private final OpenGLDevice ctx;
  private final ShaderModule[] modules;
  int handle = 0; // package-private for OpenGLPipeline
  private @Nullable String compilationError;

  OpenGLShaderProgram(OpenGLDevice ctx, ShaderModule[] modules) {
    this.ctx = ctx;
    this.modules = modules;

    ctx.submit(() -> {
      handle = glCreateProgram();
      for (ShaderModule m : modules) {
        glAttachShader(handle, ((OpenGLShaderModule) m).handle);
      }
      glLinkProgram(handle);
      if (glGetProgrami(handle, GL_LINK_STATUS) == GL_FALSE) {
        compilationError = glGetProgramInfoLog(handle);
        glDeleteProgram(handle);
        handle = 0;
        return;
      }
      // Detach after successful link
      for (ShaderModule m : modules) {
        glDetachShader(handle, ((OpenGLShaderModule) m).handle);
      }
    });
  }

  @Override
  public ShaderModule[] modules() {
    return modules;
  }

  @Override
  public @Nullable String checkCompilationError() {
    return compilationError;
  }

  @Override
  public void close() {
    ctx.submit(() -> {
      if (handle != 0) {
        glDeleteProgram(handle);
        handle = 0;
      }
    });
  }

  @Override
  public int handle(int slot) {
    return handle;
  }
}
