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
import net.fmhi.gfx.shader.ShaderLanguage;
import net.fmhi.gfx.shader.ShaderModule;
import net.fmhi.gfx.shader.ShaderModuleDesc;
import net.fmhi.util.Handle;
import net.fmhi.util.InternalApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.util.spvc.Spvc.*;

/**
 * Compiles a single GLSL shader stage.
 */
@InternalApi
public final class OpenGLShaderModule implements ShaderModule, Handle {
  private static final Logger LOGGER = LogManager.getLogger();

  private final OpenGLDevice ctx;
  private final ShaderModuleDesc desc;
  int handle = 0;
  private @Nullable String compilationError;

  OpenGLShaderModule(OpenGLDevice ctx, ShaderModuleDesc desc) {
    this.ctx = ctx;
    this.desc = desc;

    ctx.submit(() -> {
      String glsl;

      if (desc.language() == ShaderLanguage.GLSL) {
        glsl = desc.code();
      } else {
        ByteBuffer spirv = ctx.getShaderCompiler().compile(desc.code(), desc.type());
        glsl = spirvToGLSL(spirv);
        LOGGER.info("Compiling HLSL to GLSL:\n{}\n=============\n{}", desc.code(), glsl);
      }

      handle = glCreateShader(OpenGLUtils.shaderType(desc.type()));
      glShaderSource(handle, glsl);
      glCompileShader(handle);

      if (glGetShaderi(handle, GL_COMPILE_STATUS) == GL_FALSE) {
        compilationError = glGetShaderInfoLog(handle);
        glDeleteShader(handle);
        handle = 0;
      }
    });
  }

  /**
   * Translates SPIR-V to GLSL (SPIRV-Cross).
   *
   * @param spirv SPIR-V binary in host endianness
   * @return GLSL source text
   * @throws GraphicsException if translation fails
   */
  static String spirvToGLSL(ByteBuffer spirv) {
    try (MemoryStack stack = MemoryStack.stackPush()) {
      PointerBuffer pointer = stack.mallocPointer(1);

      spvc_context_create(pointer);
      long context = pointer.get(0);

      spvc_context_parse_spirv(context, spirv.asIntBuffer(), spirv.remaining() / 4, pointer);
      long parsedIr = pointer.get(0);

      spvc_context_create_compiler(context, SPVC_BACKEND_GLSL, parsedIr,
          SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer);
      long compiler = pointer.get(0);

      spvc_compiler_build_combined_image_samplers(compiler);

      spvc_compiler_create_compiler_options(compiler, pointer);
      long opts = pointer.get(0);
      spvc_compiler_options_set_uint(opts, SPVC_COMPILER_OPTION_GLSL_VERSION, 330);
      spvc_compiler_options_set_bool(opts, SPVC_COMPILER_OPTION_GLSL_ES, false);
      spvc_compiler_options_set_bool(opts,
          SPVC_COMPILER_OPTION_GLSL_ENABLE_420PACK_EXTENSION, true);
      spvc_compiler_options_set_bool(opts,
          SPVC_COMPILER_OPTION_GLSL_SEPARATE_SHADER_OBJECTS, true);
      spvc_compiler_options_set_bool(opts,
          SPVC_COMPILER_OPTION_GLSL_FORCE_FLATTENED_IO_BLOCKS, true);
      spvc_compiler_install_compiler_options(compiler, opts);

      spvc_compiler_compile(compiler, pointer);
      long resultPtr = pointer.get(0);
      if (resultPtr == 0) {
        spvc_context_destroy(context);
        throw new GraphicsException("SPIRV-Cross translation produced null");
      }
      String glsl = MemoryUtil.memUTF8(resultPtr);

      spvc_context_destroy(context);
      return glsl;
    }
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
