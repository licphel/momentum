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

import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.shader.ShaderCompiler;
import net.fmhi.gfx.shader.ShaderType;
import net.fmhi.util.internal.InternalApi;

import java.nio.ByteBuffer;

import static org.lwjgl.util.shaderc.Shaderc.*;

/**
 * Compiles HLSL to SPIR-V via Google shaderc (glslang).
 */
@InternalApi
public final class ShadercCompiler implements ShaderCompiler {
  private final long handle;
  private boolean closed;

  ShadercCompiler() {
    handle = shaderc_compiler_initialize();
    if (handle == 0) {
      throw new GraphicsException("shaderc_compiler_initialize failed");
    }
  }

  private static int toShadercKind(ShaderType type) {
    return switch (type) {
      case VERTEX -> shaderc_vertex_shader;
      case FRAGMENT -> shaderc_fragment_shader;
      case GEOMETRY -> shaderc_geometry_shader;
      case COMPUTE -> shaderc_compute_shader;
    };
  }

  @Override
  public ByteBuffer compile(String source, ShaderType type) {
    if (closed) {
      throw new IllegalStateException("Compiler is closed");
    }
    long options = shaderc_compile_options_initialize();
    try {
      shaderc_compile_options_set_source_language(options, shaderc_source_language_hlsl);
      shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan,
          shaderc_env_version_vulkan_1_0);
      shaderc_compile_options_set_optimization_level(options,
          shaderc_optimization_level_performance);

      long result = shaderc_compile_into_spv(handle, source, toShadercKind(type),
          "shader.hlsl", "main", options);

      int status = shaderc_result_get_compilation_status(result);
      if (status != shaderc_compilation_status_success) {
        String err = shaderc_result_get_error_message(result);
        shaderc_result_release(result);
        throw new GraphicsException("HLSL compilation failed:\n" + err);
      }

      ByteBuffer buf = shaderc_result_get_bytes(result);
      if (buf == null) {
        throw new GraphicsException("HLSL compilation failed:\n" + result);
      }
      return buf;
    } finally {
      shaderc_compile_options_release(options);
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      shaderc_compiler_release(handle);
    }
  }
}
