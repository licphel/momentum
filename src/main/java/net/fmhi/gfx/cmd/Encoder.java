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

package net.fmhi.gfx.cmd;

import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Topology;
import net.fmhi.gfx.shader.ResourceSet;
import org.jspecify.annotations.Nullable;

/**
 * Records GPU commands (draw calls, state changes, dispatches) for batched submission.
 *
 * <p>The encoder is the central recording API for issuing GPU work. Commands
 * are accumulated in a lightweight command list on the calling thread and then submitted to the render thread as a
 * single batch via {@link #queuedExecute()}.
 *
 * <p><b>Command ordering:</b> commands execute in recording order.
 * State set by {@code setRenderPipe}, {@code setViewport}, {@code setScissor}, and {@code setResource} persists across
 * draw calls until changed.
 *
 * <p><b>Thread safety:</b> recording is single-threaded — do not interleave
 * calls from multiple threads. {@link #queuedExecute()} and {@link #reset()} may be called from any thread.
 *
 * @see Pipeline
 * @see BufferObject
 * @see ResourceSet
 */
public interface Encoder extends AutoCloseable {
  /**
   * Discards all recorded commands, returning the encoder to its initial state.
   *
   * <p>Safe to call from any thread. After reset, the encoder has no
   * remembered pipeline, buffers, or viewport state.
   */
  void reset();

  /**
   * Submits the recorded commands to the render thread for execution.
   *
   * <p>The commands are copied into a snapshot before submission, so
   * {@link #reset()} may be called immediately without affecting the batch that is about to execute.
   */
  void queuedExecute();

  /**
   * Begins a render pass targeting the render target in {@code desc}.
   *
   * <p>Binds the target framebuffer, performs any requested clears, and stores
   * the target so that {@link #setViewport} and {@link #setScissor} can apply the correct Y-flip. If
   * {@code desc.target()} is {@code null}, the swapchain (default framebuffer) is used.
   *
   * <p>Must be followed by a matching {@link #endPass()} call.
   *
   * @param desc the render pass configuration
   */
  void beginPass(RenderPass desc);

  /**
   * Ends the current render pass, presenting the bound render target.
   *
   * <p>For the swapchain this triggers {@code swapBuffers}; for off-screen
   * targets this is a no-op (the result stays in GPU memory).
   *
   * <p>Clears the internal current-target reference; calling {@link #setViewport}
   * after {@code endPass} without a subsequent {@link #beginPass} throws {@link net.fmhi.gfx.GraphicsException}.
   */
  void endPass();

  /**
   * Sets the primitive topology for subsequent draw calls.
   *
   * @param topology the primitive type (e.g. {@link Topology#TRIANGLE})
   */
  void setTopology(Topology topology);

  /**
   * Binds a vertex buffer for subsequent draw calls.
   *
   * @param buffer the buffer to bind
   */
  void setVertexBuffer(BufferObject buffer);

  /**
   * Binds an index buffer for subsequent draw calls.
   *
   * @param buffer the buffer to bind
   */
  void setIndexBuffer(BufferObject buffer);

  /**
   * Binds an instance-data buffer for subsequent instanced draw calls.
   *
   * <p>Instance buffers carry per-instance vertex attributes (divisor &gt; 0).
   * The layout of this buffer is defined by the pipeline's
   * {@link net.fmhi.gfx.shader.VertexLayout}.
   *
   * @param buffer the instance buffer, or {@code null} to unbind
   */
  void setInstanceBuffer(@Nullable BufferObject buffer);

  /**
   * Binds a render pipeline for subsequent draw calls.
   *
   * <p>The pipeline's state (blend, depth, stencil, rasterization, shader)
   * is applied when the command executes on the render thread.
   *
   * @param pipe the pipeline to bind
   */
  void setRenderPipe(Pipeline pipe);

  /**
   * Sets the viewport rectangle in top-left–origin coordinates.
   *
   * <p>The backend performs the Y-flip required by the graphics API
   * (e.g. OpenGL expects bottom-left origin).
   *
   * @param x      left edge in pixels
   * @param y      top edge in pixels
   * @param width  viewport width in pixels
   * @param height viewport height in pixels
   */
  void setViewport(int x, int y, int width, int height);

  /**
   * Sets the scissor rectangle and enables scissor testing.
   *
   * <p>Pixels outside the scissor rectangle are discarded during rasterization.
   *
   * @param x      left edge in pixels (top-left origin)
   * @param y      top edge in pixels
   * @param width  scissor width in pixels
   * @param height scissor height in pixels
   * @param enable {@code true} to enable scissor testing
   */
  void setScissor(int x, int y, int width, int height, boolean enable);

  /**
   * Sets the scissor rectangle with scissor testing enabled.
   *
   * <p>Equivalent to {@link #setScissor(int, int, int, int, boolean)
   * setScissor(x, y, width, height, true)}.
   *
   * @param x      left edge in pixels (top-left origin)
   * @param y      top edge in pixels
   * @param width  scissor width in pixels
   * @param height scissor height in pixels
   */
  default void setScissor(int x, int y, int width, int height) {
    setScissor(x, y, width, height, true);
  }

  /**
   * Binds a resource set to a shader slot for subsequent draw calls.
   *
   * <p>A resource set carries texture/sampler bindings and uniform buffer
   * ranges. The slot index corresponds to the binding declared in the shader.
   *
   * @param slot the shader binding slot
   * @param set  the resource set to bind
   */
  void setResource(int slot, ResourceSet set);

  /**
   * Records a non-indexed draw call.
   *
   * <p>Requires that a vertex buffer and render pipeline have been set.
   *
   * @param vertexCount number of vertices to draw
   * @param firstVertex index of the first vertex
   */
  void draw(int vertexCount, int firstVertex);

  /**
   * Records an indexed draw call.
   *
   * <p>Requires that a vertex buffer, index buffer, and render pipeline
   * have been set.
   *
   * <p>The index buffer is treated as containing unsigned 32-bit integers.
   *
   * @param indexCount number of indices to draw
   * @param firstIndex index of the first index element
   */
  void drawIndexed(int indexCount, int firstIndex);

  /**
   * Records an instanced, non-indexed draw call.
   *
   * <p>Each instance receives the same vertex data but a different slice of
   * the instance buffer (see {@link #setInstanceBuffer}). The instance buffer's per-instance attributes are declared in
   * the pipeline's {@link net.fmhi.gfx.shader.VertexLayout} with {@code divisor > 0}.
   *
   * @param vertexCount   number of vertices to draw per instance
   * @param instanceCount number of instances to draw
   * @param firstVertex   index of the first vertex
   */
  void drawInstanced(int vertexCount, int instanceCount, int firstVertex);

  /**
   * Records an instanced, indexed draw call.
   *
   * <p>Each instance receives the same vertex/index data but a different slice
   * of the instance buffer.
   *
   * @param indexCount    number of indices to draw per instance
   * @param instanceCount number of instances to draw
   * @param firstIndex    index of the first index element
   */
  void drawIndexedInstanced(int indexCount, int instanceCount, int firstIndex);

  /**
   * Records a compute dispatch.
   *
   * <p>The currently bound pipeline must contain a compute shader.
   *
   * @param x number of work groups in X
   * @param y number of work groups in Y
   * @param z number of work groups in Z
   */
  void dispatch(int x, int y, int z);

  @Override
  void close();
}
