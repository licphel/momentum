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

package io.viki.momentum.gfx.ui.element;

import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.ui.Resolution;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.gfx.view.View;
import io.viki.momentum.math.Vector2;
import org.jspecify.annotations.Nullable;

/**
 * Owns the logical-coordinate mapping shared by one UI canvas.
 *
 * <p>The context translates input coordinates and applies the logical camera and viewport to a
 * graphics instance. A view-backed context follows the view's input size, while a detached context
 * accepts explicit input-size updates. Instances are mutable and not thread-safe.
 */
public final class DpiContext implements AutoCloseable {
  private final @Nullable View view;
  private final TransformHandler transformHandler;
  private final Resolution resolution;
  private Vector2 inputSize;
  private boolean closed;

  /**
   * Creates a detached context using the default logical canvas size.
   *
   * @param framebufferWidth the initial framebuffer width
   * @param framebufferHeight the initial framebuffer height
   * @param transformHandler the backend coordinate conversion handler
   */
  public DpiContext(int framebufferWidth, int framebufferHeight,
                    TransformHandler transformHandler) {
    this(framebufferWidth, framebufferHeight, Resolution.DEFAULT_LOGICAL_WIDTH,
        Resolution.DEFAULT_LOGICAL_HEIGHT, false, transformHandler);
  }

  /**
   * Creates a detached context with a custom logical canvas.
   *
   * @param framebufferWidth the initial framebuffer width
   * @param framebufferHeight the initial framebuffer height
   * @param logicalWidth the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param onlyIntegerScale whether automatic scaling must use an integer factor
   * @param transformHandler the backend coordinate conversion handler
   */
  public DpiContext(int framebufferWidth, int framebufferHeight, float logicalWidth,
                    float logicalHeight, boolean onlyIntegerScale,
                    TransformHandler transformHandler) {
    this(null, new Vector2(framebufferWidth, framebufferHeight),
        Resolution.auto(framebufferWidth, framebufferHeight, logicalWidth, logicalHeight,
            onlyIntegerScale, transformHandler), transformHandler);
  }

  /**
   * Creates a view-backed context using the default logical canvas size.
   *
   * @param view the view whose dimensions and input size are followed
   * @param transformHandler the backend coordinate conversion handler
   */
  public DpiContext(View view, TransformHandler transformHandler) {
    this(view, Resolution.DEFAULT_LOGICAL_WIDTH, Resolution.DEFAULT_LOGICAL_HEIGHT, false,
        transformHandler);
  }

  /**
   * Creates a view-backed context with a custom logical canvas.
   *
   * @param view the view whose dimensions and input size are followed
   * @param logicalWidth the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param onlyIntegerScale whether automatic scaling must use an integer factor
   * @param transformHandler the backend coordinate conversion handler
   */
  public DpiContext(View view, float logicalWidth, float logicalHeight,
                    boolean onlyIntegerScale, TransformHandler transformHandler) {
    this(view, view.getInputSize(),
        Resolution.auto(view.getWidth(), view.getHeight(), logicalWidth, logicalHeight,
            onlyIntegerScale, transformHandler), transformHandler);
  }

  private DpiContext(@Nullable View view, Vector2 inputSize, Resolution resolution,
                     TransformHandler transformHandler) {
    validateSize(inputSize.x(), inputSize.y(), "Input coordinate");
    this.view = view;
    this.inputSize = inputSize;
    this.resolution = resolution;
    this.transformHandler = transformHandler;
  }
  /**
   * Returns the input surface size used for coordinate conversion.
   *
   * <p>A view-backed context obtains this value from the view on demand; a detached context uses
   * the last value supplied to {@link #setInputSize(float, float)}.
   *
   * @return current input surface dimensions
   */
  public Vector2 getInputSize() {
    return view == null ? inputSize : view.getInputSize();
  }
  /**
   * Returns the logical dimensions assigned to this context.
   *
   * <p>The logical size remains stable while the framebuffer and input surfaces are resized.
   *
   * @return logical UI dimensions
   */
  public Vector2 getLogicalSize() {
    return resolution.logicalSize();
  }
  /**
   * Returns the transform handler used by the graphics backend.
   *
   * <p>The handler is retained for the lifetime of this context and is also used by its camera.
   *
   * @return coordinate transform handler
   */
  public TransformHandler getTransformHandler() {
    return transformHandler;
  }
  /**
   * Returns the mutable resolution mapping maintained by this context.
   *
   * <p>Resizing the context updates this mapping in place so callers can retain the returned
   * reference.
   *
   * @return current resolution mapping
   */
  public Resolution resolution() {
    return resolution;
  }

  /**
   * Converts an input-space point into logical UI coordinates.
   *
   * @param inputX the input-space X coordinate
   * @param inputY the input-space Y coordinate
   * @return the corresponding logical point
   */
  public Vector2 inputToLogical(double inputX, double inputY) {
    Vector2 size = getInputSize();
    return resolution.inputToLogical(inputX, inputY, size.x(), size.y());
  }

  /**
   * Sets the input surface size for a detached context.
   *
   * @param width the input width
   * @param height the input height
   * @throws IllegalStateException if this context follows a view
   * @throws IllegalArgumentException if either dimension is not finite and positive
   */
  public void setInputSize(float width, float height) {
    ensureOpen();
    if (view != null) {
      throw new IllegalStateException("A View-backed UI context obtains input size from its View");
    }
    validateSize(width, height, "Input coordinate");
    inputSize = new Vector2(width, height);
  }

  /**
   * Updates the framebuffer dimensions and recalculates the logical viewport.
   *
   * @param framebufferWidth the new framebuffer width
   * @param framebufferHeight the new framebuffer height
   * @throws IllegalStateException if this context has been closed
   */
  public void resize(int framebufferWidth, int framebufferHeight) {
    ensureOpen();
    resolution.resize(framebufferWidth, framebufferHeight);
    if (view == null) {
      inputSize = new Vector2(framebufferWidth, framebufferHeight);
    }
  }

  /** Releases this context and rejects subsequent mutations. */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
  }

  @Nullable View view() {
    return view;
  }

  void apply(Graphics graphics) {
    ensureOpen();
    resolution.apply(graphics);
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("UI logical context is closed");
    }
  }

  private static void validateSize(float width, float height, String name) {
    if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0F || height <= 0.0F) {
      throw new IllegalArgumentException(name + " size must be finite and positive: "
          + width + "x" + height);
    }
  }
}
