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

package net.fmhi.gfx.mesh.dim2;

import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.math.Box2D;
import net.fmhi.math.Matrix4x4;
import net.fmhi.math.dim2.Camera2D;
import org.jspecify.annotations.Nullable;

/**
 * A batched 2D drawing context backed by vertex and index staging buffers.
 *
 * <p>A {@code Graphics2D} records draw commands — textures, rectangles, lines, points, and text —
 * into staging buffers and submits them to the GPU via {@link #flush()}. It carries render state
 * including a camera, sampler, render target, viewport, scissor test, pipeline, resource set, and
 * view-projection matrix. All of these affect subsequent draw calls until changed.
 *
 * <p>This class is not thread-safe.
 *
 * @see BatchedGraphics2D
 * @see MeshGraphics2D
 */
public abstract class Graphics2D extends InplaceVertexBuilder2D implements AutoCloseable {
  /**
   * Returns the current camera, or {@code null} if none is set.
   *
   * @return the camera, or {@code null}
   */
  public abstract @Nullable Camera2D camera();

  /**
   * Sets the camera for subsequent draws.
   *
   * @param camera the camera to use
   */
  public abstract void setCamera(Camera2D camera);

  /**
   * Returns the current sampler, or {@code null} if none is set.
   *
   * @return the sampler, or {@code null}
   */
  public abstract @Nullable Sampler sampler();

  /**
   * Sets the sampler, flushing pending draws first.
   *
   * @param sampler the sampler to use
   */
  public abstract void setSampler(Sampler sampler);

  /**
   * Returns the current render target, or {@code null} if none is set.
   *
   * @return the render target, or {@code null}
   */
  public abstract @Nullable RenderTarget renderTarget();

  /**
   * Sets the render target, flushing pending draws first.
   *
   * @param renderTarget the render target to use
   */
  public abstract void setRenderTarget(RenderTarget renderTarget);

  /**
   * Returns the current viewport rectangle.
   *
   * @return the viewport
   */
  public abstract Box2D currentViewport();

  /**
   * Replays the sections of the given mesh with the current render state.
   *
   * @param mesh the mesh to replay
   */
  public abstract void drawMesh(Mesh mesh);

  /**
   * Submits pending draw data to the GPU.
   *
   * @param force if {@code true}, submits even when no vertex data has been recorded
   */
  public abstract void flush(boolean force);

  /**
   * Flushes pending draws without forcing submission of empty buffers.
   */
  public void flush() {
    flush(false);
  }

  /**
   * Begins a render pass.
   *
   * @param pass the render pass descriptor
   */
  public abstract void begin(RenderPass pass);

  /**
   * Begins a render pass with {@link RenderPass#DEFAULT}.
   */
  public void begin() {
    begin(RenderPass.DEFAULT);
  }

  /**
   * Ends the current render pass.
   */
  public abstract void end();

  /**
   * Sets the viewport rectangle in screen coordinates.
   *
   * @param box the viewport rectangle
   */
  public abstract void setViewport(Box2D box);

  /**
   * Pushes a scissor rectangle in world coordinates, flushing pending draws first.
   *
   * <p>The world-space rectangle is projected to screen coordinates using the current
   * camera and viewport.
   *
   * @param box the scissor rectangle in world coordinates
   */
  public abstract void pushScissor(Box2D box);

  /**
   * Disables the scissor test, flushing pending draws first.
   */
  public abstract void popScissor();

  /**
   * Sets the pipeline and resource set for subsequent draws, flushing pending draws first
   * if either differs from the current state.
   *
   * @param pipe the pipeline
   * @param rs   the resource set with bound textures and uniforms
   */
  public abstract void setPipeline(Pipeline pipe, ResourceSet rs);

  /**
   * Returns the currently active pipeline, or {@code null} if none is set.
   *
   * @return the active pipeline, or {@code null}
   */
  public abstract @Nullable Pipeline currentPipeline();

  /**
   * Returns the currently active resource set, or {@code null} if none is set.
   *
   * @return the active resource set, or {@code null}
   */
  public abstract @Nullable ResourceSet currentResourceSet();

  /**
   * Sets the view-projection matrix, flushing pending draws first.
   *
   * @param vpm the view-projection matrix
   */
  public abstract void setViewProjection(Matrix4x4 vpm);

  /**
   * Releases any GPU resources held by this graphics context.
   */
  @Override
  public abstract void close();
}
