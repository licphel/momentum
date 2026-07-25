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

import net.fmhi.gfx.pipe.PipelineDesc;
import org.jspecify.annotations.Nullable;

/**
 * A linked shader program composed of one or more compiled {@link ShaderModule} instances.
 *
 * <p>A graphics pipeline requires a linked program (vertex + fragment at
 * minimum; geometry is optional). Compute pipelines use a single compute module. Linking resolves cross-stage
 * references and validates interface matching. Link errors are reported as exceptions at creation time.
 *
 * <p>Once linked, the program is immutable. The individual modules may be
 * retained or closed independently after linking — the linked program holds its own references to the compiled GPU
 * objects.
 *
 * <p><b>Thread safety:</b> immutable after linking — safe to read from any
 * thread.
 *
 * @see ShaderModule
 * @see PipelineDesc.Builder#shaderProgram
 */
public interface ShaderProgram extends AutoCloseable {
  /**
   * Returns the shader modules that were linked into this program.
   *
   * @return the constituent modules in link order
   */
  ShaderModule[] modules();

  /**
   * Returns the link error message, or {@code null} if linking succeeded.
   *
   * @return the info log on failure, or {@code null}
   */
  @Nullable String checkCompilationError();

  @Override
  void close();
}
