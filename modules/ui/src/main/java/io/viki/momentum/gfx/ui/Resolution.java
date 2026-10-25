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
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Maps a dynamically sized logical UI canvas onto the entire framebuffer.
 *
 * <p>The configured logical dimensions are minimum dimensions for automatic scale selection.
 * The available logical size is the framebuffer size divided by the selected scale. Framebuffer
 * sizing and the automatic sharp-scale interval are updated through {@link #resize(int, int)} and
 * {@link #setResolutionKeptSharp(int)}. Use this object on the view thread; it is not
 * thread-safe.
 */
public final class Resolution {
  /** Default logical canvas width. */
  public static final float DEFAULT_LOGICAL_WIDTH = 480.0F;
  /** Default logical canvas height. */
  public static final float DEFAULT_LOGICAL_HEIGHT = 270.0F;
  /** Initial scale used by automatic resolution selection. */
  public static final float SCALE_START = 0.5F;
  /** Scale increment used by automatic resolution selection. */
  public static final float SCALE_STEP = 0.5F;

  private final float minimumLogicalWidth;
  private final float minimumLogicalHeight;
  private final boolean automatic;
  private final boolean onlyInteger;
  private final float fixedScale;
  private final float logicalScale;
  private final Camera2D camera;
  private float logicalWidth;
  private float logicalHeight;
  private int width;
  private int height;
  private float scale;
  private int resolutionKeptSharp = 1;
  private Rectangle viewport;
  private Vector2 logicalSize;

  /**
   * Creates a resolution mapping with validated framebuffer and logical dimensions.
   *
   * @param width framebuffer width in pixels
   * @param height framebuffer height in pixels
   * @param logicalWidth minimum logical canvas width
   * @param logicalHeight minimum logical canvas height
   * @param automatic whether to select the scale from the framebuffer size
   * @param onlyInteger whether automatic scale selection uses integer increments
   * @param fixedScale fixed scale or the starting scale for automatic selection
   * @param logicalScale additional multiplier applied to the selected scale
   * @param handler backend coordinate conversion handler
   * @throws IllegalArgumentException if a framebuffer dimension, logical dimension, or scale is
   *         less than or equal to zero
   */
  private Resolution(int width, int height, float logicalWidth, float logicalHeight,
                     boolean automatic, boolean onlyInteger, float fixedScale,
                     float logicalScale, TransformHandler handler) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Framebuffer size must be positive: " + width + "x" + height);
    }
    if (logicalWidth <= 0.0F || logicalHeight <= 0.0F) {
      throw new IllegalArgumentException("Logical size must be positive: " + logicalWidth + "x" + logicalHeight);
    }
    if (fixedScale <= 0.0F) {
      throw new IllegalArgumentException("Resolution scale must be positive: " + fixedScale);
    }
    if (logicalScale <= 0.0F) {
      throw new IllegalArgumentException("Logical scale must be positive: " + logicalScale);
    }
    this.width = width;
    this.height = height;
    this.logicalWidth = logicalWidth;
    this.logicalHeight = logicalHeight;
    this.minimumLogicalWidth = logicalWidth;
    this.minimumLogicalHeight = logicalHeight;
    this.automatic = automatic;
    this.onlyInteger = onlyInteger;
    this.fixedScale = fixedScale;
    this.logicalScale = logicalScale;
    this.camera = new Camera2D(logicalWidth, logicalHeight, handler);
    camera.setFlipY(true);
    recalculate();
  }

  /**
   * Creates an automatically scaled mapping using the default logical canvas size.
   *
   * @param width       the framebuffer width in pixels
   * @param height      the framebuffer height in pixels
   * @param onlyInteger whether the selected scale must be an integer
   * @param handler     the backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if either framebuffer dimension is less than or equal to zero
   */
  public static Resolution auto(int width, int height, boolean onlyInteger, TransformHandler handler) {
    return auto(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, onlyInteger, handler);
  }

  /**
   * Creates an automatically scaled mapping for a custom logical canvas.
   *
   * @param width         the framebuffer width in pixels
   * @param height        the framebuffer height in pixels
   * @param logicalWidth  the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param onlyInteger   whether the selected scale must be an integer
   * @param handler       the backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if a framebuffer or logical dimension is less than or equal to zero
   */
  public static Resolution auto(int width, int height, float logicalWidth, float logicalHeight,
                                boolean onlyInteger, TransformHandler handler) {
    return auto(width, height, logicalWidth, logicalHeight, onlyInteger, 1.0F, handler);
  }

  /**
   * Creates an automatically scaled mapping with an additional per-canvas logical scale.
   *
   * <p>A logical scale of {@code 2} gives the canvas twice the logical pixel density while
   * preserving its declared coordinate units.
   *
   * @param width framebuffer width in pixels
   * @param height framebuffer height in pixels
   * @param logicalWidth minimum logical canvas width
   * @param logicalHeight minimum logical canvas height
   * @param onlyInteger whether automatic selection uses integer increments
   * @param logicalScale additional multiplier applied to the selected scale
   * @param handler backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if a framebuffer or logical dimension, or
   *         {@code logicalScale}, is less than or equal to zero
   */
  public static Resolution auto(int width, int height, float logicalWidth, float logicalHeight,
                                boolean onlyInteger, float logicalScale, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, true, onlyInteger,
        SCALE_START, logicalScale, handler);
  }

  /**
   * Creates a fixed-scale mapping using the default logical canvas size.
   *
   * @param width   the framebuffer width in pixels
   * @param height  the framebuffer height in pixels
   * @param scale   the logical-to-framebuffer scale
   * @param handler the backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if a framebuffer dimension or {@code scale} is less than or
   *         equal to zero
   */
  public static Resolution fixed(int width, int height, float scale, TransformHandler handler) {
    return fixed(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, scale, handler);
  }

  /**
   * Creates a fixed-scale mapping for a custom logical canvas.
   *
   * @param width         the framebuffer width in pixels
   * @param height        the framebuffer height in pixels
   * @param logicalWidth  the logical canvas width
   * @param logicalHeight the logical canvas height
   * @param scale         the logical-to-framebuffer scale
   * @param handler       the backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if a framebuffer or logical dimension, or {@code scale}, is
   *         less than or equal to zero
   */
  public static Resolution fixed(int width, int height, float logicalWidth, float logicalHeight,
                                 float scale, TransformHandler handler) {
    return fixed(width, height, logicalWidth, logicalHeight, scale, 1.0F, handler);
  }

  /**
   * Creates a fixed-scale mapping with an additional per-canvas logical multiplier.
   *
   * @param width framebuffer width in pixels
   * @param height framebuffer height in pixels
   * @param logicalWidth logical canvas width
   * @param logicalHeight logical canvas height
   * @param scale fixed framebuffer scale
   * @param logicalScale additional multiplier applied to the fixed scale
   * @param handler backend coordinate conversion handler
   * @return the configured resolution mapping
   * @throws IllegalArgumentException if a framebuffer or logical dimension, {@code scale}, or
   *         {@code logicalScale} is less than or equal to zero
   */
  public static Resolution fixed(int width, int height, float logicalWidth, float logicalHeight,
                                 float scale, float logicalScale, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, false, false, scale,
        logicalScale, handler);
  }

  /**
   * Selects the largest allowed automatic scale that fits the framebuffer.
   *
   * @param width framebuffer width in pixels
   * @param height framebuffer height in pixels
   * @param logicalWidth minimum logical canvas width
   * @param logicalHeight minimum logical canvas height
   * @param onlyInteger whether allowed scales use integer increments instead of half increments
   * @return the largest allowed fit scale, or the fractional fit when the framebuffer is smaller
   *         than one step
   */
  private static float resolveScale(int width, int height, float logicalWidth, float logicalHeight, boolean onlyInteger) {
    float required = Math.min(width / logicalWidth, height / logicalHeight);
    // Fit even a framebuffer smaller than the minimum logical dimensions.
    float factor = required < SCALE_START ? required
        : (float) (Math.floor(required / SCALE_STEP) * SCALE_STEP);
    if (onlyInteger) {
      factor = required < 1.0F ? required : (float) Math.floor(required);
    }
    return factor;
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
   * Returns the currently available logical canvas width.
   *
   * @return logical width in units
   */
  public float logicalWidth() {
    return logicalWidth;
  }

  /**
   * Returns the currently available logical canvas height.
   *
   * @return logical height in units
   */
  public float logicalHeight() {
    return logicalHeight;
  }

  /**
   * Returns the currently available logical canvas dimensions.
   *
   * @return logical canvas size
   */
  public Vector2 logicalSize() {
    return logicalSize;
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
   * Configures the allowed automatic framebuffer scale interval. For example, 2 allows
   * scales 2, 4, 6, etc. A value of 1 retains the existing scaling policy. This constraint
   * applies after the per-canvas logical multiplier; explicit fixed scales are unchanged.
   * If no allowed scale fits, the mapping falls back to at most 1, retaining fractional
   * downscaling for windows smaller than the minimum logical canvas.
   *
   * @param resolutionKeptSharp positive integral scale interval
   * @throws IllegalArgumentException if {@code resolutionKeptSharp} is less than one
   */
  public void setResolutionKeptSharp(int resolutionKeptSharp) {
    if (resolutionKeptSharp < 1) {
      throw new IllegalArgumentException("Sharp scale interval must be positive");
    }
    this.resolutionKeptSharp = resolutionKeptSharp;
    recalculate();
  }

  /**
   * Returns the additional automatic scale interval, or one for the original policy.
   *
   * @return the automatic scale interval
   */
  public int resolutionKeptSharp() {
    return resolutionKeptSharp;
  }

  /**
   * Returns the optional logical scale multiplier configured for this mapping.
   *
   * @return per-canvas logical scale
   */
  public float logicalScale() {
    return logicalScale;
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
   * Returns the full framebuffer viewport used to draw the logical canvas.
   *
   * @return framebuffer viewport
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
   * Resizes the framebuffer mapping and logical dimensions while preserving camera identity.
   *
   * @param width  the new framebuffer width in pixels
   * @param height the new framebuffer height in pixels
   * @throws IllegalArgumentException if either framebuffer dimension is less than or equal to
   *         zero
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
   * <p>Nonpositive input dimensions are treated as one pixel, allowing conversion while an input
   * surface is minimized.
   *
   * @param x           the input-space X coordinate
   * @param y           the input-space Y coordinate
   * @param inputWidth  the input surface width
   * @param inputHeight the input surface height
   * @return the corresponding logical point
   */
  public Vector2 inputToLogical(double x, double y, double inputWidth, double inputHeight) {
    if (inputWidth <= 0.0) {
      inputWidth = 1.0F; // Normalize for minimized window
    }
    if (inputHeight <= 0.0) {
      inputHeight = 1.0F; // Normalize for minimized window
    }
    return screenToLogical(x * width / inputWidth, y * height / inputHeight);
  }

  /**
   * Converts a logical point into an arbitrary input surface.
   *
   * @param point       the logical point
   * @param inputWidth  the input surface width
   * @param inputHeight the input surface height
   * @return the corresponding input-space point
   * @throws IllegalArgumentException if an input dimension is less than or equal to zero
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

  /** Recomputes the scale, logical dimensions, viewport, and owned camera projection. */
  private void recalculate() {
    if (automatic) {
      scale = resolveScale(width, height, this.minimumLogicalWidth, this.minimumLogicalHeight,
          onlyInteger) * logicalScale;
      if (resolutionKeptSharp > 1) {
        scale = scale >= resolutionKeptSharp
            ? (float) Math.floor(scale / resolutionKeptSharp) * resolutionKeptSharp
            : Math.min(scale, 1F);
      }
    } else {
      scale = fixedScale * logicalScale;
    }
    // The whole framebuffer is usable; aspect-ratio changes become layout changes.
    logicalWidth = width / scale;
    logicalHeight = height / scale;
    logicalSize = new Vector2(logicalWidth, logicalHeight);
    viewport = Rectangle.of(0.0F, 0.0F, width, height);
    apply(camera);
  }
}
