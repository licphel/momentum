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

import org.jspecify.annotations.Nullable;

/**
 * A compiled shader module representing a single programmable stage of the graphics pipeline.
 *
 * <p>A {@code ShaderModule} is the result of compiling GLSL (or other shading
 * language) source code for one stage — vertex, fragment, geometry, or compute. Modules are linked together into a
 * {@link ShaderProgram} before they can be used in a render pipeline.
 *
 * <p>Once compiled, a module is immutable. Compilation errors are reported
 * as exceptions at creation time.
 *
 * <p><b>Thread safety:</b> immutable after compilation — safe to read from
 * any thread.
 *
 * @see ShaderModuleDesc
 * @see ShaderProgram
 */
public interface ShaderModule extends AutoCloseable {
  /**
   * Returns the descriptor that was used to create this shader module.
   *
   * @return the immutable shader module descriptor
   */
  ShaderModuleDesc desc();

  /**
   * Returns the compilation error message, or {@code null} if compilation succeeded.
   *
   * @return the info log on failure, or {@code null}
   */
  @Nullable String checkCompilationError();

  @Override
  void close();
}
