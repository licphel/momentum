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
 * Creates a new {@code ShaderModuleDesc} describing a single shader stage to be compiled.
 *
 * <p>Holds the GLSL source code and the pipeline stage it targets. Passed to
 * the backend to create a {@link ShaderModule}, which is then linked into a {@link ShaderProgram}.
 *
 * @param type    the pipeline stage this module targets
 * @param code    GLSL source code for this stage
 * @param targets render targets
 * @see ShaderModule
 * @see ShaderProgram
 */
public record ShaderModuleDesc(ShaderType type, String code, String[] targets) {
  /**
   * Creates a new {@code ShaderModuleDesc} describing a single shader stage to be compiled.
   *
   * @param type the pipeline stage this module targets
   * @param code GLSL source code for this stage
   */
  public ShaderModuleDesc(ShaderType type, String code) {
    this(type, code, new String[0]);
  }
}
