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

package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.math.Camera2D;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.Vector2;

/**
 * Maps a fixed logical UI canvas onto a resizable framebuffer viewport.
 *
 * <p>The mapping preserves aspect ratio and can optionally restrict scaling to integer factors,
 * which is useful for crisp pixel-oriented interfaces. The object is mutable only through
 * {@link #resize(int, int)} and is intended to be updated on the view thread.
 */
public final class Resolution {
  /** Default logical canvas width. */
  public static final float DEFAULT_LOGICAL_WIDTH = 800.0F;
  /** Default logical canvas height. */
  public static final float DEFAULT_LOGICAL_HEIGHT = 450.0F;
  /** Initial scale used by automatic resolution selection. */
  public static final float SCALE_START = 0.5F;
  /** Scale increment used by automatic resolution selection. */
  public static final float SCALE_STEP = 0.5F;

  private final float logicalWidth;
  private final float logicalHeight;
  private final boolean automatic;
  private final boolean onlyInteger;
  private final float fixedScale;
  private int width;
  private int height;
  private float scale;
  private Rectangle viewport;
  private final Camera2D camera;

  /** Creates a resolution mapping with validated framebuffer and logical dimensions. */
  private Resolution(int width, int height, float logicalWidth, float logicalHeight,
                     boolean automatic, boolean onlyInteger, float fixedScale, TransformHandler handler) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Framebuffer size must be positive: " + width + "x" + height);
    }
    if (!Float.isFinite(logicalWidth) || !Float.isFinite(logicalHeight) || logicalWidth <= 0.0F || logicalHeight <= 0.0F) {
      throw new IllegalArgumentException("Logical size must be finite and positive: " + logicalWidth + "x" + logicalHeight);
    }
    if (!Float.isFinite(fixedScale) || fixedScale <= 0.0F) {
      throw new IllegalArgumentException("Resolution scale must be finite and positive: " + fixedScale);
    }
    this.width = width;
    this.height = height;
    this.logicalWidth = logicalWidth;
    this.logicalHeight = logicalHeight;
    this.automatic = automatic;
    this.onlyInteger = onlyInteger;
    this.fixedScale = fixedScale;
    this.camera = new Camera2D(logicalWidth, logicalHeight, handler);
    camera.setFlipY(true);
    recalculate();
  }
  
  /**
   * Creates an automatically scaled mapping using the default logical canvas size.
   *
   * @param width the framebuffer width in pixels
   * @param height the framebuffer height in pixels
   * @param onlyInteger whether the selected scale must be an integer
   * @param handler the backend coordinate conversion handler
   * @return the configured resolution mapping
   */
  public static Resolution auto(int width, int height, boolean onlyInteger, TransformHandler handler) {
    return auto(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, onlyInteger, handler);
  }

  /**
   * Creates an automatically scaled mapping for a custom logical canvas.
   *
   * @param width the framebuffer width in pixels
   * @param height the framebuffer height in pixels
   * @param logicalWidth the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param onlyInteger whether the selected scale must be an integer
   * @param handler the backend coordinate conversion handler
   * @return the configured resolution mapping
   */
  public static Resolution auto(int width, int height, float logicalWidth, float logicalHeight,
      boolean onlyInteger, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, true, onlyInteger, SCALE_START, handler);
  }

  /**
   * Creates a fixed-scale mapping using the default logical canvas size.
   *
   * @param width the framebuffer width in pixels
   * @param height the framebuffer height in pixels
   * @param scale the logical-to-framebuffer scale
   * @param handler the backend coordinate conversion handler
   * @return the configured resolution mapping
   */
  public static Resolution fixed(int width, int height, float scale, TransformHandler handler) {
    return fixed(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, scale, handler);
  }

  /**
   * Creates a fixed-scale mapping for a custom logical canvas.
   *
   * @param width the framebuffer width in pixels
   * @param height the framebuffer height in pixels
   * @param logicalWidth the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param scale the logical-to-framebuffer scale
   * @param handler the backend coordinate conversion handler
   * @return the configured resolution mapping
   */
  public static Resolution fixed(int width, int height, float logicalWidth, float logicalHeight,
      float scale, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, false, false, scale, handler);
  }

  /**
   * Returns the current framebuffer width.
   *
   * @return framebuffer width in pixels
   */
  public int width() {
    return width;
  }

  /**
   * Returns the current framebuffer height.
   *
   * @return framebuffer height in pixels
   */
  public int height() {
    return height;
  }

  /**
   * Returns the fixed logical canvas width.
   *
   * @return logical width in units
   */
  public float logicalWidth() {
    return logicalWidth;
  }

  /**
   * Returns the fixed logical canvas height.
   *
   * @return logical height in units
   */
  public float logicalHeight() {
    return logicalHeight;
  }

  /**
   * Returns the fixed logical canvas dimensions.
   *
   * @return logical canvas size
   */
  public Vector2 logicalSize() {
    return new Vector2(logicalWidth, logicalHeight);
  }

  /**
   * Returns the current logical-to-framebuffer scale.
   *
   * @return scale factor
   */
  public float scale() {
    return scale;
  }

  /**
   * Returns the camera representing the logical canvas.
   *
   * @return logical-space camera
   */
  public Camera2D camera() {
    return camera;
  }

  /**
   * Returns the framebuffer rectangle occupied by the logical canvas.
   *
   * @return centered logical viewport
   */
  public Rectangle viewport() {
    return viewport;
  }

  /**
   * Returns the viewport used for framebuffer rendering.
   *
   * @return framebuffer viewport
   */
  public Rectangle framebufferViewport() {
    return viewport;
  }

  /**
   * Resizes the framebuffer mapping while preserving logical dimensions and camera identity.
   *
   * @param width the new framebuffer width in pixels
   * @param height the new framebuffer height in pixels
   */
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Framebuffer size must be positive: " + width + "x" + height);
    }
    this.width = width;
    this.height = height;
    recalculate();
  }

  /**
   * Converts a framebuffer point expressed in screen coordinates to logical coordinates.
   *
   * @param x the screen-space X coordinate
   * @param y the screen-space Y coordinate
   * @return the corresponding logical point
   */
  public Vector2 screenToLogical(double x, double y) {
    return camera.unproject(new Vector2((float) x, (float) y), viewport);
  }

  /**
   * Converts a screen-space point to logical coordinates.
   *
   * @param point the screen-space point
   * @return the corresponding logical point
   */
  public Vector2 screenToLogical(Vector2 point) {
    return screenToLogical(point.x(), point.y());
  }

  /**
   * Converts a framebuffer point to logical coordinates.
   *
   * @param point the framebuffer point
   * @return the corresponding logical point
   */
  public Vector2 framebufferToLogical(Vector2 point) {
    return screenToLogical(point);
  }

  /**
   * Converts a logical point to screen coordinates.
   *
   * @param point the logical point
   * @return the corresponding screen point
   */
  public Vector2 logicalToScreen(Vector2 point) {
    return camera.project(point, viewport);
  }

  /**
   * Converts a logical point to framebuffer coordinates.
   *
   * @param point the logical point
   * @return the corresponding framebuffer point
   */
  public Vector2 logicalToFramebuffer(Vector2 point) {
    return logicalToScreen(point);
  }

  /**
   * Converts coordinates from an arbitrary input surface into logical coordinates.
   *
   * @param x the input-space X coordinate
   * @param y the input-space Y coordinate
   * @param inputWidth the input surface width
   * @param inputHeight the input surface height
   * @return the corresponding logical point
   * @throws IllegalArgumentException if an input dimension is not positive
   */
  public Vector2 inputToLogical(double x, double y, double inputWidth, double inputHeight) {
    if (inputWidth <= 0.0 || inputHeight <= 0.0) {
      throw new IllegalArgumentException("Input coordinate size must be positive: " + inputWidth + "x" + inputHeight);
    }
    return screenToLogical(x * width / inputWidth, y * height / inputHeight);
  }

  /**
   * Converts a logical point into an arbitrary input surface.
   *
   * @param point the logical point
   * @param inputWidth the input surface width
   * @param inputHeight the input surface height
   * @return the corresponding input-space point
   * @throws IllegalArgumentException if an input dimension is not positive
   */
  public Vector2 logicalToInput(Vector2 point, double inputWidth, double inputHeight) {
    if (inputWidth <= 0.0 || inputHeight <= 0.0) {
      throw new IllegalArgumentException("Input coordinate size must be positive: " + inputWidth + "x" + inputHeight);
    }
    Vector2 screen = logicalToScreen(point);
    return new Vector2((float) (screen.x() * inputWidth / width),
        (float) (screen.y() * inputHeight / height));
  }

  /**
   * Converts a logical rectangle to screen coordinates.
   *
   * @param box the logical rectangle
   * @return the corresponding screen rectangle
   */
  public Rectangle logicalToScreen(Rectangle box) {
    Vector2 min = logicalToScreen(new Vector2(box.minX(), box.minY()));
    Vector2 max = logicalToScreen(new Vector2(box.maxX(), box.maxY()));
    return new Rectangle(min.x(), min.y(), max.x(), max.y());
  }

  /**
   * Projects a logical point into screen coordinates.
   *
   * @param logicalPoint the logical point
   * @return the projected screen point
   */
  public Vector2 project(Vector2 logicalPoint) {
    return logicalToScreen(logicalPoint);
  }

  /**
   * Unprojects a screen point into logical coordinates.
   *
   * @param screenPoint the screen point
   * @return the corresponding logical point
   */
  public Vector2 unproject(Vector2 screenPoint) {
    return screenToLogical(screenPoint);
  }

  /**
   * Applies the camera and viewport to a graphics context.
   *
   * @param graphics the graphics context to configure
   */
  public void apply(Graphics graphics) {
    graphics.setCamera(camera);
    graphics.setViewport(viewport);
  }

  /**
   * Applies this logical projection to another camera without replacing the owned camera.
   *
   * @param target the camera to configure
   */
  public void apply(Camera2D target) {
    target.setOrthographic(logicalWidth, logicalHeight);
    target.setCenter(new Vector2(logicalWidth * 0.5F, logicalHeight * 0.5F));
  }

  private void recalculate() {
    if (automatic) {
      scale = resolveScale(width, height, logicalWidth, logicalHeight, onlyInteger);
    } else {
      scale = fixedScale;
    }
    float width = logicalWidth * scale;
    float height = logicalHeight * scale;
    viewport = Rectangle.of((this.width - width) * 0.5F, (this.height - height) * 0.5F, width, height);
    apply(camera);
  }

  private static float resolveScale(int width, int height, float logicalWidth, float logicalHeight, boolean onlyInteger) {
    float required = Math.max(width / logicalWidth, height / logicalHeight);
    float factor = Math.max(SCALE_START, (float) (Math.ceil(required / SCALE_STEP) * SCALE_STEP));
    if (onlyInteger) {
      factor = Math.max(SCALE_START, (float) Math.ceil(required));
    }
    return factor;
  }
}
