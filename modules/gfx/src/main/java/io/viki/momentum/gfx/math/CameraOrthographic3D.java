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

package io.viki.momentum.gfx.math;

import io.viki.momentum.math.Matrix4x4;

/**
 * A 3D orthographic camera with a configurable vertical projection direction.
 *
 * <p>The projection volume is centred at the origin with half-extents
 * {@code width/2/zoom} and {@code height/2/zoom}.
 */
public class CameraOrthographic3D extends Camera3D {
  private float width;
  private float height;
  private float zoom = 1.0F;
  private boolean flipY;

  /**
   * Creates an orthographic camera with the given projection dimensions.
   *
   * @param width   projection width
   * @param height  projection height
   * @param handler the transform handler encoding coordinate-system conventions
   */
  public CameraOrthographic3D(float width, float height, TransformHandler handler) {
    super(handler);
    this.width = width;
    this.height = height;
  }

  /**
   * Returns the width of the orthographic projection.
   *
   * @return width
   */
  public float getWidth() {
    return width;
  }

  /**
   * Sets the width of the orthographic projection. Values below {@code 0.01} are clamped.
   *
   * @param value width
   */
  public void setWidth(float value) {
    float clamped = Math.max(value, 0.01F);
    if (width != clamped) {
      width = clamped;
      dirty = true;
    }
  }

  /**
   * Returns the height of the orthographic projection.
   *
   * @return height
   */
  public float getHeight() {
    return height;
  }

  /**
   * Sets the height of the orthographic projection. Values below {@code 0.01} are clamped.
   *
   * @param value height
   */
  public void setHeight(float value) {
    float clamped = Math.max(value, 0.01F);
    if (height != clamped) {
      height = clamped;
      dirty = true;
    }
  }

  /**
   * Returns the zoom factor (larger = zoomed in).
   *
   * @return zoom
   */
  public float getZoom() {
    return zoom;
  }

  /**
   * Returns whether increasing world-space Y is mapped toward the bottom of the viewport.
   *
   * @return {@code true} when the camera uses a downward screen-space Y direction
   */
  public boolean isYFlipped() {
    return flipY;
  }

  /**
   * Selects the vertical direction used by this orthographic projection.
   *
   * <p>Changing the direction invalidates the cached view-projection matrix inherited from
   * {@link Camera3D}.
   *
   * @param flipY {@code true} when increasing world-space Y should map toward the bottom
   */
  public void setFlipY(boolean flipY) {
    if (this.flipY != flipY) {
      this.flipY = flipY;
      dirty = true;
    }
  }

  /**
   * Sets the zoom factor. Values below {@code 0.01} are clamped.
   *
   * @param value zoom factor
   */
  public void setZoom(float value) {
    float clamped = Math.max(value, 0.01F);
    if (zoom != clamped) {
      zoom = clamped;
      dirty = true;
    }
  }

  /**
   * Returns the current aspect ratio (width / height).
   *
   * @return aspect ratio
   */
  public float getAspectRatio() {
    return width / height;
  }

  /**
   * Sets both width and height at once.
   *
   * @param width  projection width
   * @param height projection height
   */
  public void setOrthographic(float width, float height) {
    setWidth(width);
    setHeight(height);
  }

  /**
   * Sets width and derives height from an aspect ratio.
   *
   * @param width       projection width
   * @param aspectRatio width / height
   */
  public void setOrthographicByAspect(float width, float aspectRatio) {
    setWidth(width);
    setHeight(width / aspectRatio);
  }

  @Override
  protected Matrix4x4 buildProjectionMatrix() {
    float halfW = width * 0.5F / zoom;
    float halfH = height * 0.5F / zoom;
    float bottom = flipY ? halfH : -halfH;
    float top = flipY ? -halfH : halfH;
    return handler.createOrthographic(-halfW, halfW, bottom, top, getNearPlane(), getFarPlane());
  }
}
