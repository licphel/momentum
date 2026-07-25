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

package net.fmhi.math.dim3;

import net.fmhi.math.Matrix4x4;

/**
 * A 3D perspective camera.
 *
 * <p>Field of view is clamped to {@code (0.01, π − 0.01)} to avoid degenerate projections.
 */
public class CameraPerspective3D extends Camera3D {
  private float fov = (float) (Math.PI / 3.0); // 60°
  private float aspectRatio = 16.0F / 9.0F;

  /**
   * Returns the vertical field of view in radians.
   *
   * @return field of view
   */
  public float getFieldOfView() {
    return fov;
  }

  /**
   * Sets the vertical field of view in radians. Values are clamped to {@code (0.01, π − 0.01)}.
   *
   * @param value field of view in radians
   */
  public void setFieldOfView(float value) {
    float clamped = Math.clamp(value, 0.01F, (float) Math.PI - 0.01F);
    if (fov != clamped) {
      fov = clamped;
      dirty = true;
    }
  }

  /**
   * Returns the aspect ratio (width / height).
   *
   * @return aspect ratio
   */
  public float getAspectRatio() {
    return aspectRatio;
  }

  /**
   * Sets the aspect ratio. Values below {@code 0.01} are clamped.
   *
   * @param value aspect ratio
   */
  public void setAspectRatio(float value) {
    float clamped = Math.max(value, 0.01F);
    if (aspectRatio != clamped) {
      aspectRatio = clamped;
      dirty = true;
    }
  }

  /**
   * Sets both field of view and aspect ratio at once.
   *
   * @param fieldOfView vertical FOV in radians
   * @param aspectRatio width / height
   */
  public void setPerspective(float fieldOfView, float aspectRatio) {
    setFieldOfView(fieldOfView);
    setAspectRatio(aspectRatio);
  }

  @Override
  protected Matrix4x4 buildProjectionMatrix() {
    return Matrix4x4.createPerspective(fov, aspectRatio, getNearPlane(), getFarPlane());
  }
}
