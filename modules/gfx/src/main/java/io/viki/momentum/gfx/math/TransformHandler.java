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

import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.math.Matrix4x4;

/**
 * Encodes the project's fixed coordinate-system conventions for use by cameras and texture sampling.
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li><strong>Depth range:</strong> {@code [0, 1]} for both perspective and orthographic projections</li>
 *   <li><strong>Y-axis:</strong> matrices are Y-up; a Y-down view is achieved by the caller swapping top and bottom</li>
 *   <li><strong>Texture UV origin:</strong> top-left</li>
 * </ul>
 *
 * <p>Implementations bridge the gap between these conventions and the underlying graphics API.
 */
public interface TransformHandler {
  /**
   * Normalizes a texture U coordinate from texels to the {@code [0, 1]} range.
   *
   * @param tex the texture
   * @param u   the U coordinate in texels
   * @return normalized U coordinate
   */
  float u(Texture tex, float u);

  /**
   * Normalizes a texture V coordinate from texels to the {@code [0, 1]} range and flips it
   * so that sampling follows the top-left UV origin convention.
   *
   * @param tex the texture
   * @param v   the V coordinate in texels
   * @return normalized V coordinate, with {@code 1.0 - v} applied
   */
  float v(Texture tex, float v);

  /**
   * Creates a right-handed orthographic projection matrix with {@code [0, 1]} depth range.
   *
   * @param left   left plane
   * @param right  right plane
   * @param bottom bottom plane
   * @param top    top plane
   * @param near   near clip plane (positive)
   * @param far    far clip plane (positive)
   * @return orthographic projection matrix
   */
  Matrix4x4 createOrthographic(float left, float right, float bottom, float top, float near, float far);

  /**
   * Creates a right-handed perspective projection matrix with {@code [0, 1]} depth range.
   *
   * @param fovY   vertical field of view in radians
   * @param aspect width divided by height
   * @param near   near clip plane (positive)
   * @param far    far clip plane (positive)
   * @return perspective projection matrix
   */
  Matrix4x4 createPerspective(float fovY, float aspect, float near, float far);

}
