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

package net.fmhi.gfx.math;

import net.fmhi.math.Box2D;
import net.fmhi.math.Matrix4x4;
import net.fmhi.math.Vector2;
import net.fmhi.math.Vector3;

/**
 * 2D orthographic camera (Y-down, center-based).
 *
 * <p>The view-projection matrix maps world coordinates to NDC.
 * Lazily recomputed whenever position, size, or zoom changes.
 *
 * <p>The camera position represents the center of the visible area.
 * The view extends {@code width/zoom} horizontally and
 * {@code height/zoom} vertically, centered on the position.
 */
public class Camera2D {
  private Vector2 position = Vector2.ZERO;
  private float width;
  private float height;
  private float zoom = 1.0F;
  private Matrix4x4 vpMatrix = Matrix4x4.IDENTITY;
  private boolean dirty = true;
  private final TransformHandler handler;

  /**
   * Creates a camera with the specified viewport dimensions.
   *
   * @param width   viewport width in pixels
   * @param height  viewport height in pixels
   * @param handler the transform handler encoding coordinate-system conventions
   */
  public Camera2D(float width, float height, TransformHandler handler) {
    this.width = width;
    this.height = height;
    this.handler = handler;
  }

  /**
   * Returns the center of the visible area in world coordinates.
   *
   * @return center position
   */
  public Vector2 position() {
    return position;
  }

  /**
   * Sets the center of the visible area.
   *
   * @param center the new center in world coordinates
   */
  public void setCenter(Vector2 center) {
    setPosition(center);
  }

  /**
   * Returns the viewport width in pixels.
   *
   * @return viewport width
   */
  public float width() {
    return width;
  }

  /**
   * Returns the viewport height in pixels.
   *
   * @return viewport height
   */
  public float height() {
    return height;
  }

  /**
   * Returns the current zoom level.
   *
   * @return zoom factor (1.0 = normal, >1.0 = zoomed in)
   */
  public float zoom() {
    return zoom;
  }

  /**
   * Returns the aspect ratio (width / height).
   *
   * @return aspect ratio, or 1.0 if height is zero
   */
  public float aspectRatio() {
    return height != 0.0F ? width / height : 1.0F;
  }

  /**
   * Sets the camera position in world coordinates.
   *
   * @param pos new position
   */
  public void setPosition(Vector2 pos) {
    position = pos;
    dirty = true;
  }

  /**
   * Translates the camera by the given delta.
   *
   * @param delta translation vector
   */
  public void translate(Vector2 delta) {
    position = position.add(delta);
    dirty = true;
  }

  /**
   * Sets the orthographic projection dimensions.
   *
   * @param width  viewport width in pixels
   * @param height viewport height in pixels
   */
  public void setOrthographic(float width, float height) {
    this.width = width;
    this.height = height;
    dirty = true;
  }

  /**
   * Sets the orthographic width, automatically updating height to preserve aspect ratio.
   *
   * @param width new viewport width in pixels
   */
  public void setOrthographicByAspect(float width) {
    this.width = width;
    height = width / aspectRatio();
    dirty = true;
  }

  /**
   * Sets the zoom level.
   *
   * @param zoom zoom factor (1.0 = normal, >1.0 = zoomed in)
   */
  public void setZoom(float zoom) {
    this.zoom = zoom;
    dirty = true;
  }

  /**
   * Returns the combined view-projection matrix.
   *
   * <p>The matrix is cached and recomputed only when camera parameters change.
   *
   * @return view-projection matrix
   */
  public Matrix4x4 viewProjectionMatrix() {
    if (dirty) {
      rebuild();
      dirty = false;
    }
    return vpMatrix;
  }

  /**
   * Rebuilds the projection matrix from current camera parameters.
   */
  private void rebuild() {
    float effectiveW = width / zoom;
    float effectiveH = height / zoom;
    float halfW = effectiveW * 0.5F;
    float halfH = effectiveH * 0.5F;
    float left = position.x() - halfW;
    float right = position.x() + halfW;
    float top = position.y() - halfH;
    float bottom = position.y() + halfH;
    /*
     * Y-down orthographic: flip Y by swapping top/bottom.
     * (Conform to 2D conventions)
     */
    vpMatrix = handler.createOrthographic(left, right, bottom, top, 0.0F, -1.0F);
  }

  /**
   * Projects a world position into screen (viewport) coordinates.
   *
   * @param worldPos world-space position
   * @param viewport screen rectangle (x, y, width, height)
   * @return screen-space position in pixels
   */
  public Vector2 project(Vector2 worldPos, Box2D viewport) {
    Vector3 clip = viewProjectionMatrix().transform(new Vector3(worldPos));
    float sx = (clip.x() * 0.5F + 0.5F) * viewport.width() + viewport.minX();
    float sy = (1.0F - (clip.y() * 0.5F + 0.5F)) * viewport.height() + viewport.minY();
    return new Vector2(sx, sy);
  }

  /**
   * Unprojects a screen position into world coordinates.
   *
   * @param screenPos screen-space position in pixels
   * @param viewport  screen rectangle (x, y, width, height)
   * @return world-space position
   */
  public Vector2 unproject(Vector2 screenPos, Box2D viewport) {
    float ndcX = 2.0F * (screenPos.x() - viewport.minX()) / viewport.width() - 1.0F;
    float ndcY = -2.0F * (screenPos.y() - viewport.minY()) / viewport.height() + 1.0F;
    Vector3 world = viewProjectionMatrix().invert().transform(new Vector3(ndcX, ndcY, 0.0F));
    return new Vector2(world.x(), world.y());
  }
}