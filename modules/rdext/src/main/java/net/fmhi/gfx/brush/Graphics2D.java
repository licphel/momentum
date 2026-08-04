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

package net.fmhi.gfx.brush;

import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.math.TransformHandler;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.math.Box2D;
import net.fmhi.math.Matrix4x4;
import org.jspecify.annotations.Nullable;

/**
 * A 2D drawing context that records draw commands into staging buffers and submits them
 * to the GPU.
 *
 * <p>Carries the render state — camera, sampler, render target, viewport, scissor test,
 * pipeline, resource set, and view-projection matrix — that applies to subsequent draws
 * until changed. Textures, rectangles, lines, points, and text are recorded with
 * {@link VertexBuilder2D} and submitted via {@link #flush()}.
 *
 * <p>Not thread-safe; each instance must be confined to a single thread.
 *
 * @see BatchedGraphics2D
 * @see MeshGraphics2D
 */
public abstract class Graphics2D extends VertexBuilder2D implements AutoCloseable {
  /**
   * Creates a new {@code Graphics2D}.
   *
   * @param data              the staging area receiving vertices and indices
   * @param transformHandler  the backend-specific transform handler
   */
  public Graphics2D(VertexStore data, TransformHandler transformHandler) {
    super(data, transformHandler);
  }

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
   * @param sampler the sampler to use; {@code null} unbinds
   */
  public abstract void setSampler(@Nullable Sampler sampler);

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
   * <p>The rectangle is projected to screen coordinates using the current camera and
   * viewport.
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
  public abstract void setPipeline(@Nullable Pipeline pipe, @Nullable ResourceSet rs);

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
   * Releases any GPU resources held by this graphics context.
   */
  @Override
  public abstract void close();
}
