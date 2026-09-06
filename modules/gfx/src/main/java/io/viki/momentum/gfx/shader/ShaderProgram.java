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

package io.viki.momentum.gfx.shader;

import io.viki.momentum.gfx.Device;
import io.viki.momentum.gfx.GraphicsException;
import io.viki.momentum.gfx.pipe.PipelineDesc;
import org.jspecify.annotations.Nullable;

/**
 * A linked, immutable shader program ready for use in a graphics or compute pipeline.
 *
 * <p>A program is created by linking one or more compiled {@link ShaderModule} instances
 * together. Graphics pipelines require at minimum a vertex and a fragment module; compute
 * pipelines use a single compute module. Linking resolves cross-stage references and
 * validates interface compatibility — any failure is reported as an exception at creation time.
 *
 * <p>Once linked, the program is immutable and safe for concurrent use.
 *
 * @see ShaderModule
 * @see PipelineDesc.Builder#shaderProgram
 */
public interface ShaderProgram extends AutoCloseable {
  /**
   * Compiles and links a vertex–fragment shader pair using the default {@link ShaderLanguage#HLSL} language.
   *
   * @param device the graphics device to create the program on
   * @param vert   the vertex shader source code
   * @param frag   the fragment shader source code
   * @return a linked shader program
   * @throws GraphicsException if compilation or linking fails
   */
  static ShaderProgram load(Device device, String vert, String frag) {
    return load(device, vert, frag, ShaderLanguage.HLSL);
  }

  /**
   * Compiles and links a vertex–fragment shader pair in the specified source language.
   *
   * @param device the graphics device to create the program on
   * @param vert   the vertex shader source code
   * @param frag   the fragment shader source code
   * @param lang   the source language of both shaders
   * @return a linked shader program
   * @throws GraphicsException if compilation or linking fails
   */
  static ShaderProgram load(Device device, String vert, String frag, ShaderLanguage lang) {
    ShaderModule vertModule = device.getShaderModule(
        new ShaderModuleDesc(ShaderType.VERTEX, vert, lang, new String[0]));
    ShaderModule fragModule = device.getShaderModule(
        new ShaderModuleDesc(ShaderType.FRAGMENT, frag, lang, new String[0]));
    return device.getShaderProgram(vertModule, fragModule);
  }

  /**
   * Returns the shader modules that were linked into this program, in link order.
   *
   * @return the constituent shader modules
   */
  ShaderModule[] modules();

  /**
   * Returns the link error message, or {@code null} if linking succeeded.
   *
   * @return the linker info log on failure, or {@code null} on success
   */
  @Nullable String checkCompilationError();

  @Override
  void close();
}
