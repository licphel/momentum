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

package net.fmhi.gfx.pipe;

import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.gfx.shader.ResourceSetLayout;
import net.fmhi.gfx.shader.VertexLayout;

/**
 * An immutable render pipeline that bundles all fixed-function and programmable state for a draw call.
 *
 * <p>A {@code Pipeline} combines:
 * <ul>
 *   <li>Blend state ({@link Blend})
 *   <li>Depth/stencil state ({@link Depth}, {@link Stencil})
 *   <li>Rasterization state ({@link RasterizationDesc})
 *   <li>A linked shader program
 *   <li>A vertex attribute layout ({@link VertexLayout})
 *   <li>Resource set layouts ({@link ResourceSetLayout})
 * </ul>
 *
 * <p>Pipelines are heavy objects that should be created once and reused across
 * many frames. They are immutable after construction.
 *
 * <p><b>Thread safety:</b> read-only after creation — safe to use from any
 * thread. The underlying GPU state is applied on the render thread during
 * command execution.
 *
 * @see PipelineDesc
 * @see Encoder#setRenderPipe(Pipeline)
 */
public interface Pipeline extends AutoCloseable {
  /**
   * Returns the descriptor that defines this pipeline's state.
   *
   * @return the immutable pipeline descriptor
   */
  PipelineDesc desc();

  @Override
  void close();
}
