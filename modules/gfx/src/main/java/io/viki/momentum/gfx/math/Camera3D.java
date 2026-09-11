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

import io.viki.momentum.math.*;
import io.viki.momentum.math.Frustum;
import io.viki.momentum.math.Quaternion;
import io.viki.momentum.math.Ray;

/**
 * Abstract base class for a 3D camera (Y-up convention).
 *
 * <p>This class provides common camera functionality including view matrix
 * management, clipping planes, and frustum culling. Subclasses implement {@link #buildProjectionMatrix()} to supply
 * perspective or orthographic projection.
 *
 * <p>All expensive matrix products are computed lazily and cached until camera
 * parameters change.
 */
public abstract class Camera3D {
  protected final TransformHandler handler;
  protected boolean dirty = true;
  private float near = 0.1F;
  private float far = 1000.0F;
  private Vector3 position = Vector3.ZERO;
  private Vector3 target = Vector3.UNIT_Z.negate();
  private Vector3 up = Vector3.UNIT_Y;
  private Matrix4x4 viewMatrix = Matrix4x4.IDENTITY;
  private Matrix4x4 projectionMatrix = Matrix4x4.IDENTITY;
  private Matrix4x4 viewProjectionMatrix = Matrix4x4.IDENTITY;
  private Frustum frustum = new Frustum(Matrix4x4.IDENTITY);

  /**
   * Creates a camera with the given transform handler.
   *
   * @param handler the transform handler encoding coordinate-system conventions
   */
  protected Camera3D(TransformHandler handler) {
    this.handler = handler;
  }

  /**
   * Returns the camera position in world space.
   *
   * @return camera position
   */
  public Vector3 getPosition() {
    return position;
  }

  /**
   * Sets the camera position.
   *
   * @param value new camera position
   */
  public void setPosition(Vector3 value) {
    if (!position.equals(value)) {
      position = value;
      dirty = true;
    }
  }

  /**
   * Returns the point the camera is aimed at.
   *
   * @return look-at target
   */
  public Vector3 getTarget() {
    return target;
  }

  /**
   * Sets the look-at target.
   *
   * @param value new target point
   */
  public void setTarget(Vector3 value) {
    if (!target.equals(value)) {
      target = value;
      dirty = true;
    }
  }

  /**
   * Returns the up direction vector.
   *
   * @return up vector (typically {@link Vector3#UNIT_Y})
   */
  public Vector3 getUp() {
    return up;
  }

  /**
   * Sets the up direction vector.
   *
   * @param value new up vector (should be normalized)
   */
  public void setUp(Vector3 value) {
    if (!up.equals(value)) {
      up = value;
      dirty = true;
    }
  }

  /**
   * Returns the normalized forward direction (target - position).
   *
   * @return forward unit vector
   */
  public Vector3 getForward() {
    return target.subtract(position).normalize();
  }

  /**
   * Returns the normalized right direction (forward × up).
   *
   * @return right unit vector
   */
  public Vector3 getRight() {
    return getForward().cross(up).normalize();
  }

  /**
   * Returns the near clipping distance.
   *
   * @return near plane distance (positive)
   */
  public float getNearPlane() {
    return near;
  }

  /**
   * Sets the near clipping distance.
   *
   * @param value near plane distance (must be positive)
   */
  public void setNearPlane(float value) {
    if (near != value) {
      near = value;
      dirty = true;
    }
  }

  /**
   * Returns the far clipping distance.
   *
   * @return far plane distance (positive)
   */
  public float getFarPlane() {
    return far;
  }

  /**
   * Sets the far clipping distance.
   *
   * @param value far plane distance (must be greater than near)
   */
  public void setFarPlane(float value) {
    if (far != value) {
      far = value;
      dirty = true;
    }
  }

  /**
   * Sets both clipping distances at once.
   *
   * @param near near plane distance
   * @param far  far plane distance
   */
  public void setClippingPlanes(float near, float far) {
    this.near = near;
    this.far = far;
    dirty = true;
  }

  /**
   * Returns the view matrix (camera to world space). Rebuilt lazily when camera parameters change.
   *
   * @return view matrix
   */
  public Matrix4x4 getViewMatrix() {
    checkedRebuild();
    return viewMatrix;
  }

  /**
   * Returns the projection matrix. Rebuilt lazily when camera parameters change.
   *
   * @return projection matrix
   */
  public Matrix4x4 getProjectionMatrix() {
    checkedRebuild();
    return projectionMatrix;
  }

  /**
   * Returns the combined view-projection matrix (projection × view). Rebuilt lazily when camera parameters change.
   *
   * @return view-projection matrix
   */
  public Matrix4x4 getViewProjectionMatrix() {
    checkedRebuild();
    return viewProjectionMatrix;
  }

  /**
   * Returns the view frustum for culling. Rebuilt lazily when camera parameters change.
   *
   * @return view frustum
   */
  public Frustum getFrustum() {
    checkedRebuild();
    return frustum;
  }

  /**
   * Moves the camera position (target is unchanged).
   *
   * @param translation offset to add to position
   */
  public void translate(Vector3 translation) {
    setPosition(position.add(translation));
  }

  /**
   * Moves both position and target by the same vector.
   *
   * @param translation offset to add
   */
  public void cotranslate(Vector3 translation) {
    position = position.add(translation);
    target = target.add(translation);
    dirty = true;
  }

  /**
   * Rotates the camera view using a quaternion, keeping position fixed.
   *
   * @param rotation   absolute rotation quaternion
   * @param baseLookAt default forward direction (typically −Z)
   * @param baseUp     default world up (typically +Y)
   */
  public void setRotation(Quaternion rotation, Vector3 baseLookAt, Vector3 baseUp) {
    setTarget(position.add(rotation.rotate(baseLookAt)));
    setUp(rotation.rotate(baseUp));
  }

  /**
   * Rotates the camera using a quaternion (uses −Z forward, +Y up defaults).
   *
   * @param rotation absolute rotation quaternion
   */
  public void setRotation(Quaternion rotation) {
    setRotation(rotation, Vector3.UNIT_Z.negate(), Vector3.UNIT_Y);
  }

  /**
   * Projects a world-space point to screen coordinates.
   *
   * @param worldPosition world-space position (Y-up)
   * @param viewport      viewport rectangle (Y-down screen coordinates)
   * @param out           receives {@code [screenX, screenY, depth]}; depth is in NDC Z range (0..1)
   * @return {@code true} if the point is within the NDC cube and the result is valid
   */
  public boolean project(Vector3 worldPosition, Box2D viewport, float[] out) {
    Matrix4x4 vp = getViewProjectionMatrix();
    Vector4 clip = vp.transform(new Vector4(worldPosition, 1.0F));

    if (Math.abs(clip.w()) < 1E-10F) {
      out[0] = out[1] = out[2] = 0.0F;
      return false;
    }

    float ndcX = clip.x() / clip.w();
    float ndcY = clip.y() / clip.w();
    float ndcZ = clip.z() / clip.w();
    out[2] = ndcZ;

    if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F || ndcZ < 0.0F || ndcZ > 1.0F) {
      out[0] = out[1] = 0.0F;
      return false;
    }

    out[0] = (ndcX + 1.0F) * 0.5F * viewport.width() + viewport.minX();
    out[1] = ndcY * -0.5F * viewport.height() + 0.5F * viewport.height() + viewport.minY();
    return true;
  }

  /**
   * Creates a world-space ray from screen coordinates.
   *
   * @param screenPosition screen position (Y-down)
   * @param viewport       viewport rectangle (Y-down)
   * @return unprojected world ray
   */
  public Ray unproject(Vector2 screenPosition, Box2D viewport) {
    return Ray.createFromScreen(screenPosition.x(), screenPosition.y(), viewport.width(), viewport.height(),
        getProjectionMatrix(), getViewMatrix());
  }

  /**
   * Computes and returns a fresh projection matrix. Called once per dirty rebuild; subclasses implement this.
   *
   * @return the projection matrix
   */
  protected abstract Matrix4x4 buildProjectionMatrix();

  private void checkedRebuild() {
    if (!dirty) {
      return;
    }
    viewMatrix = Matrix4x4.createLookAt(position, target, up);
    projectionMatrix = buildProjectionMatrix();
    viewProjectionMatrix = projectionMatrix.multiply(viewMatrix);
    frustum = new Frustum(viewProjectionMatrix);
    dirty = false;
  }
}