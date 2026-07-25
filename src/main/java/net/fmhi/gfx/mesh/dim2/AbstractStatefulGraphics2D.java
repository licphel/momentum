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

import net.fmhi.gfx.GraphicsException;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pipe.Pipeline;
import net.fmhi.gfx.pipe.Scissor;
import net.fmhi.gfx.shader.ResourceSet;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import net.fmhi.math.dim2.Camera2D;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link Graphics2D} base implementation that tracks render state and detects changes.
 *
 * <p>When the primitive type, texture, pipeline, or resource set changes, pending draws are
 * automatically flushed before the state is updated. Subclasses provide the actual flush behavior
 * and GPU submission logic.
 */
abstract class AbstractStatefulGraphics2D extends Graphics2D {
  private final Deque<Scissor> scissorStack = new ArrayDeque<>();
  protected @Nullable Pipeline currentPipeline;
  protected @Nullable ResourceSet currentResourceSet;
  protected @Nullable Primitive2D currentPrimitive;
  protected @Nullable Texture currentTexture;
  protected @Nullable Camera2D camera;
  protected @Nullable Sampler sampler;
  protected @Nullable RenderTarget renderTarget;
  protected Box2D viewport = Box2D.ZERO;
  protected Scissor scissor = Scissor.DISABLED;

  /**
   * Asserts the current primitive type, flushing pending draws if the type changed.
   *
   * @param primitive the expected primitive type
   */
  @Override
  protected void assertPrimitive(Primitive2D primitive) {
    if (primitive != currentPrimitive) {
      flush();
      currentPrimitive = primitive;
    }
  }

  /**
   * Asserts the current texture, flushing pending draws if the texture changed.
   *
   * @param tex the expected texture
   */
  @Override
  protected void assertTexture(Texture tex) {
    if (tex != currentTexture) {
      flush();
      currentTexture = tex;
    }
  }

  @Override
  public @Nullable Camera2D camera() {
    return camera;
  }

  @Override
  public void setCamera(Camera2D c) {
    camera = c;
  }

  @Override
  public @Nullable Sampler sampler() {
    return sampler;
  }

  @Override
  public void setSampler(Sampler s) {
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
   * <p>The world-space rectangle is projected to screen coordinates using the current
   * camera and viewport. Flushes pending draws before applying.
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
   * if the stack is empty. Flushes pending draws before applying.
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
  public void setPipeline(Pipeline pipe, ResourceSet rs) {
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
}
