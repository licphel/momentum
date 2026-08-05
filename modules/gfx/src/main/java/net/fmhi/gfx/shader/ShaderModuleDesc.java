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

package net.fmhi.gfx.shader;

/**
 * Describes a single shader stage to be compiled into a {@link ShaderModule}.
 *
 * @param type     the pipeline stage this module targets
 * @param code     the shader source code
 * @param language the source language of the shader code
 * @param targets  the names of render targets this stage outputs to
 * @see ShaderModule
 * @see ShaderProgram
 */
public record ShaderModuleDesc(ShaderType type,
                               String code,
                               ShaderLanguage language,
                               String[] targets) {
  /**
   * Creates a descriptor for a GLSL shader stage with no render targets.
   *
   * @param type the pipeline stage
   * @param code the shader source code
   */
  public ShaderModuleDesc(ShaderType type, String code) {
    this(type, code, ShaderLanguage.GLSL, new String[0]);
  }
}
