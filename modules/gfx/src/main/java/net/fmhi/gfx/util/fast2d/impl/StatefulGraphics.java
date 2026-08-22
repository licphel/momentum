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

package net.fmhi.gfx.util.fast2d.impl;

import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.math.TransformHandler;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Scissor;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.util.VertexStore;
import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.util.internal.InternalApi;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Base implementation of {@link Graphics} that tracks render state and detects changes.
 *
 * <p>When the primitive type, texture, pipeline, or resource set changes, pending draws are
 * flushed automatically before the state is updated. Subclasses provide the actual flush
 * behavior and GPU submission logic.
 */
@InternalApi
abstract class StatefulGraphics extends Graphics {
  private final Deque<Scissor> scissorStack = new ArrayDeque<>();
  protected @Nullable Pipeline currentPipeline;
  protected @Nullable ResourceSet currentResourceSet;
  protected @Nullable Camera2D camera;
  protected @Nullable Sampler sampler;
  protected @Nullable RenderTarget renderTarget;
  protected Box2D viewport = Box2D.ZERO;
  protected Scissor scissor = Scissor.DISABLED;

  /**
   * Creates a new {@code AbstractStatefulGraphics2D}.
   *
   * @param data             the staging area receiving vertices and indices
   * @param transformHandler the backend-specific transform handler
   */
  public StatefulGraphics(VertexStore data, TransformHandler transformHandler) {
    super(data, transformHandler);
  }

  @Override
  protected void flush0() {
    flush();
  }

  @Override
  public @Nullable Camera2D camera() {
    return camera;
  }

  @Override
  public void setCamera(Camera2D c) {
    flush();
    camera = c;
    onCameraChanged();
  }

  @Override
  public @Nullable Sampler sampler() {
    return sampler;
  }

  @Override
  public void setSampler(@Nullable Sampler s) {
    sampler = s;
  }

  @Override
  public @Nullable RenderTarget renderTarget() {
    return renderTarget;
  }

  @Override
  public void setRenderTarget(RenderTarget t) {
    renderTarget = t;
  }

  @Override
  public Box2D currentViewport() {
    return viewport;
  }

  @Override
  public void setViewport(Box2D vp) {
    viewport = vp;
  }

  /**
   * Pushes a scissor rectangle in world coordinates onto the scissor stack.
   *
   * <p>The rectangle is projected to screen coordinates using the current camera and
   * viewport. Pending draws are flushed before the new rectangle is applied.
   *
   * @param worldBox the scissor rectangle in world coordinates
   * @throws GraphicsException if no camera is set
   */
  @Override
  public void pushScissor(Box2D worldBox) {
    if (camera == null) {
      throw new GraphicsException("Cannot scissor without a camera set");
    }
    Vector2 min = camera.project(new Vector2(worldBox.minX(), worldBox.minY()), viewport);
    Vector2 max = camera.project(new Vector2(worldBox.maxX(), worldBox.maxY()), viewport);
    scissorStack.push(scissor);
    scissor = new Scissor((int) min.x(), (int) min.y(), (int) (max.x() - min.x()), (int) (max.y() - min.y()), true);
  }

  /**
   * Restores the previous scissor rectangle from the stack, or disables the scissor test
   * if the stack is empty. Pending draws are flushed before applying.
   */
  @Override
  public void popScissor() {
    scissor = !scissorStack.isEmpty() ? scissorStack.pop() : Scissor.DISABLED;
  }

  /**
   * Sets the pipeline and resource set, flushing pending draws if either differs from
   * the current state.
   *
   * @param pipe the pipeline
   * @param rs   the resource set
   */
  @Override
  public void setPipeline(@Nullable Pipeline pipe, @Nullable ResourceSet rs) {
    if (pipe != currentPipeline || rs != currentResourceSet) {
      flush();
      currentPipeline = pipe;
      currentResourceSet = rs;
    }
  }

  @Override
  public @Nullable Pipeline currentPipeline() {
    return currentPipeline;
  }

  @Override
  public @Nullable ResourceSet currentResourceSet() {
    return currentResourceSet;
  }

  /**
   * Default no-op close. Subclasses may override to release GPU resources.
   */
  @Override
  public void close() {
  }

  protected void onCameraChanged() {
  }
}
