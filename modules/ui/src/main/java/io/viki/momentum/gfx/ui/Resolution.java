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
import io.viki.momentum.math.Box2D;
import io.viki.momentum.math.Vector2;

/**
 * Fixed-logical-size UI resolution with a reusable camera and cover viewport.
 *
 * <p>This mutable object is not thread-safe. Create, resize, apply, and coordinate-convert it
 * from the rendering thread that owns the injected transform handler.
 */
public final class Resolution {
  public static final float DEFAULT_LOGICAL_WIDTH = 800.0F;
  public static final float DEFAULT_LOGICAL_HEIGHT = 450.0F;
  public static final float SCALE_START = 0.5F;
  public static final float SCALE_STEP = 0.5F;
  private final float logicalWidth;
  private final float logicalHeight;
  private final boolean automatic;
  private final boolean onlyInteger;
  private final float fixedScale;
  private int width;
  private int height;
  private float scale;
  private Box2D viewport;
  private final Camera2D camera;

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
  
  public static Resolution auto(int width, int height, boolean onlyInteger, TransformHandler handler) {
    return auto(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, onlyInteger, handler);
  }

  public static Resolution auto(int width, int height, float logicalWidth, float logicalHeight,
      boolean onlyInteger, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, true, onlyInteger, SCALE_START, handler);
  }

  public static Resolution fixed(int width, int height, float scale, TransformHandler handler) {
    return fixed(width, height, DEFAULT_LOGICAL_WIDTH, DEFAULT_LOGICAL_HEIGHT, scale, handler);
  }

  public static Resolution fixed(int width, int height, float logicalWidth, float logicalHeight,
      float scale, TransformHandler handler) {
    return new Resolution(width, height, logicalWidth, logicalHeight, false, false, scale, handler);
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public float logicalWidth() {
    return logicalWidth;
  }

  public float logicalHeight() {
    return logicalHeight;
  }

  public Vector2 logicalSize() {
    return new Vector2(logicalWidth, logicalHeight);
  }

  public float scale() {
    return scale;
  }

  public Camera2D camera() {
    return camera;
  }

  public Box2D viewport() {
    return viewport;
  }

  public Box2D framebufferViewport() {
    return viewport;
  }

  /** Recalculates the viewport while preserving the camera instance and logical size. */
  public void resize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Framebuffer size must be positive: " + width + "x" + height);
    }
    this.width = width;
    this.height = height;
    recalculate();
  }

  public Vector2 screenToLogical(double x, double y) {
    return camera.unproject(new Vector2((float) x, (float) y), viewport);
  }

  public Vector2 screenToLogical(Vector2 point) {
    return screenToLogical(point.x(), point.y());
  }

  public Vector2 framebufferToLogical(Vector2 point) {
    return screenToLogical(point);
  }

  public Vector2 logicalToScreen(Vector2 point) {
    return camera.project(point, viewport);
  }

  public Vector2 logicalToFramebuffer(Vector2 point) {
    return logicalToScreen(point);
  }

  public Vector2 inputToLogical(double x, double y, double inputWidth, double inputHeight) {
    if (inputWidth <= 0.0 || inputHeight <= 0.0) {
      throw new IllegalArgumentException("Input coordinate size must be positive: "
          + inputWidth + "x" + inputHeight);
    }
    return screenToLogical(x * width / inputWidth, y * height / inputHeight);
  }

  public Vector2 logicalToInput(Vector2 point, double inputWidth, double inputHeight) {
    if (inputWidth <= 0.0 || inputHeight <= 0.0) {
      throw new IllegalArgumentException("Input coordinate size must be positive: " + inputWidth + "x" + inputHeight);
    }
    Vector2 screen = logicalToScreen(point);
    return new Vector2((float) (screen.x() * inputWidth / width),
        (float) (screen.y() * inputHeight / height));
  }

  public Box2D logicalToScreen(Box2D box) {
    Vector2 min = logicalToScreen(new Vector2(box.minX(), box.minY()));
    Vector2 max = logicalToScreen(new Vector2(box.maxX(), box.maxY()));
    return new Box2D(min.x(), min.y(), max.x(), max.y());
  }

  public Vector2 project(Vector2 logicalPoint) {
    return logicalToScreen(logicalPoint);
  }

  public Vector2 unproject(Vector2 screenPoint) {
    return screenToLogical(screenPoint);
  }

  public void apply(Graphics graphics) {
    graphics.setCamera(camera);
    graphics.setViewport(viewport);
  }

  /** Applies the fixed logical projection to another camera without replacing the owned camera. */
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
    viewport = Box2D.create((this.width - width) * 0.5F, (this.height - height) * 0.5F, width, height);
    apply(camera);
  }

  private static float resolveScale(int width, int height, float logicalWidth, float logicalHeight,
      boolean onlyInteger) {
    float required = Math.max(width / logicalWidth, height / logicalHeight);
    float factor = Math.max(SCALE_START, (float) (Math.ceil(required / SCALE_STEP) * SCALE_STEP));
    if (onlyInteger) {
      factor = Math.max(SCALE_START, (float) Math.ceil(required));
    }
    return factor;
  }
}
