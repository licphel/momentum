package net.fmhi.gfx.math;

import net.fmhi.gfx.texture.Texture;
import net.fmhi.math.Matrix4x4;

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

  /**
   * Sets the y-axis points upward or downward.
   *
   * @param flipY whether to flip y down
   */
  void flipY(boolean flipY);

  /**
   * Returns whether to use flip-Y.
   *
   * @return the flip-Y flag
   */
  boolean isYFlipped();
}
