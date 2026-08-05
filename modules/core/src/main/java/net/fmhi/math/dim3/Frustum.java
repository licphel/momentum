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

import net.fmhi.math.*;

/**
 * View frustum defined by six clipping planes.
 *
 * <p>The frustum represents the visible region of 3D space as defined by a camera.
 * It is used for culling objects that are outside the camera's view.
 *
 * <p>Planes are stored in order: Left, Right, Bottom, Top, Near, Far.
 * Corners are computed lazily from plane intersection and are available via {@link #corner}.
 */
public final class Frustum {
  /**
   * Six clipping planes in order: LEFT, RIGHT, BOTTOM, TOP, NEAR, FAR.
   */
  private final Plane[] planes = new Plane[6];
  /**
   * Eight frustum corners (near plane first, then far plane).
   */
  private final Vector3[] corners = new Vector3[8];

  /**
   * Creates a frustum from six clipping planes.
   *
   * @param planes six planes in order: Left, Right, Bottom, Top, Near, Far
   * @throws IllegalArgumentException if the array does not have exactly six elements
   */
  public Frustum(Plane[] planes) {
    if (planes.length != 6) {
      throw new IllegalArgumentException("Frustum requires exactly six planes");
    }
    for (int i = 0; i < 6; i++) {
      this.planes[i] = planes[i].normalize();
    }
    genCorners();
  }

  /**
   * Creates a frustum from a combined view-projection matrix using Gribb/Hartmann extraction.
   *
   * @param viewProjection combined VP matrix
   */
  public Frustum(Matrix4x4 viewProjection) {
    genPlanes(viewProjection);
    genCorners();
  }

  /**
   * Creates a perspective frustum from projection parameters.
   *
   * @param fovY   vertical field of view in radians
   * @param aspect aspect ratio (width / height)
   * @param near   near clipping distance (must be > 0)
   * @param far    far clipping distance (must be > near)
   * @return perspective frustum
   */
  public static Frustum createPerspective(float fovY, float aspect, float near, float far) {
    float tanHalf = (float) Math.tan(fovY * 0.5F);
    float nh = near * tanHalf;
    float nw = nh * aspect;
    float fh = far * tanHalf;
    float fw = fh * aspect;
    Vector3 nc = new Vector3(0, 0, -near);
    Vector3 fc = new Vector3(0, 0, -far);

    Vector3[] c = {nc.add(new Vector3(-nw, -nh, 0)), // near-left-bottom
        nc.add(new Vector3(nw, -nh, 0)),  // near-right-bottom
        nc.add(new Vector3(nw, nh, 0)),   // near-right-top
        nc.add(new Vector3(-nw, nh, 0)),  // near-left-top
        fc.add(new Vector3(-fw, -fh, 0)), // far-left-bottom
        fc.add(new Vector3(fw, -fh, 0)),  // far-right-bottom
        fc.add(new Vector3(fw, fh, 0)),   // far-right-top
        fc.add(new Vector3(-fw, fh, 0))   // far-left-top
    };

    Plane[] p = new Plane[6];
    p[0] = Plane.createFromPoints(c[4], c[7], c[3]); // left
    p[1] = Plane.createFromPoints(c[5], c[1], c[2]); // right
    p[2] = Plane.createFromPoints(c[4], c[0], c[1]); // bottom
    p[3] = Plane.createFromPoints(c[7], c[6], c[5]); // top
    p[4] = Plane.createFromPoints(c[0], c[3], c[2]); // near
    p[5] = Plane.createFromPoints(c[4], c[5], c[6]); // far
    return new Frustum(p);
  }

  /**
   * Creates an orthographic frustum from projection parameters.
   *
   * @param left   left boundary
   * @param right  right boundary
   * @param bottom bottom boundary
   * @param top    top boundary
   * @param near   near clipping distance
   * @param far    far clipping distance
   * @return orthographic frustum
   */
  public static Frustum createOrthographic(float left, float right, float bottom, float top, float near, float far) {
    Plane[] p = new Plane[6];
    p[0] = new Plane(Vector3.UNIT_X, left);                    // left
    p[1] = new Plane(Vector3.UNIT_X.negate(), -right);         // right
    p[2] = new Plane(Vector3.UNIT_Y, bottom);                  // bottom
    p[3] = new Plane(Vector3.UNIT_Y.negate(), -top);           // top
    p[4] = new Plane(Vector3.UNIT_Z, near);                    // near
    p[5] = new Plane(Vector3.UNIT_Z.negate(), -far);           // far
    return new Frustum(p);
  }

  /**
   * Creates a frustum from separate view and projection matrices.
   *
   * @param view       view matrix (camera to world)
   * @param projection projection matrix
   * @return view frustum in world space
   */
  public static Frustum createFromMatrices(Matrix4x4 view, Matrix4x4 projection) {
    return new Frustum(projection.multiply(view));
  }

  /**
   * Returns the intersection point of three planes.
   *
   * @param p1 first plane
   * @param p2 second plane
   * @param p3 third plane
   * @return intersection point, or {@link Vector3#ZERO} if planes are parallel
   */
  private static Vector3 intersectOf(Plane p1, Plane p2, Plane p3) {
    Matrix3x3 mat = new Matrix3x3(p1.normal().x(), p1.normal().y(), p1.normal().z(), p2.normal().x(), p2.normal().y()
        , p2.normal().z(), p3.normal().x(), p3.normal().y(), p3.normal().z());
    if (Math.abs(mat.determinant()) < 1E-10F) {
      return Vector3.ZERO;
    }
    Vector3 b = new Vector3(-p1.distance(), -p2.distance(), -p3.distance());
    return mat.invert().transform(b);
  }

  /**
   * Returns the specified clipping plane.
   *
   * @param index plane index
   * @return the plane
   */
  public Plane getPlane(PlaneIndex index) {
    return planes[index.ordinal()];
  }

  /**
   * Returns a corner point of the frustum.
   *
   * <p>Order: near-left-bottom, near-right-bottom, near-right-top, near-left-top,
   * then the same four for the far plane.
   *
   * @param index corner index (0–7)
   * @return corner position
   * @throws IndexOutOfBoundsException if index is out of range
   */
  public Vector3 corner(int index) {
    return corners[index];
  }

  /**
   * Returns all eight corner points.
   *
   * @return copy of corners array
   */
  public Vector3[] corners() {
    return corners.clone();
  }

  /**
   * Returns the center point of the frustum (average of all eight corners).
   *
   * @return centre position
   */
  public Vector3 center() {
    float sx = 0;
    float sy = 0;
    float sz = 0;
    for (Vector3 c : corners) {
      sx += c.x();
      sy += c.y();
      sz += c.z();
    }
    return new Vector3(sx / 8.0F, sy / 8.0F, sz / 8.0F);
  }

  /**
   * Returns the axis-aligned bounding box that encloses this frustum.
   *
   * @return bounding box
   */
  public Box3D boundingBox() {
    Vector3 min = corners[0];
    Vector3 max = corners[0];
    for (int i = 1; i < 8; i++) {
      min = Vector3.min(min, corners[i]);
      max = Vector3.max(max, corners[i]);
    }
    return new Box3D(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
  }

  /**
   * Returns whether a point is inside or on all six planes.
   *
   * @param point the point to test
   * @return true if inside the frustum
   */
  public boolean testPoint(Vector3 point) {
    for (int i = 0; i < 6; i++) {
      if (planes[i].distanceToPoint(point) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests a sphere against this frustum.
   *
   * @param sphere the sphere to test
   * @return {@code -1} if completely outside, {@code 0} if intersecting, {@code 1} if completely inside
   */
  public int testSphere(Sphere sphere) {
    int result = 1;
    for (int i = 0; i < 6; i++) {
      float dist = planes[i].distanceToPoint(sphere.center());
      if (dist < -sphere.radius()) {
        return -1;
      }
      if (dist < sphere.radius()) {
        result = 0;
      }
    }
    return result;
  }

  /**
   * Tests an axis-aligned bounding box against this frustum.
   *
   * @param box the box to test
   * @return {@code -1} if completely outside, {@code 0} if intersecting, {@code 1} if completely inside
   */
  public int testBox(Box3D box) {
    int result = 1;
    for (int i = 0; i < 6; i++) {
      Plane p = planes[i];
      Vector3 pos = new Vector3(p.normal().x() > 0 ? box.maxX() : box.minX(), p.normal().y() > 0 ? box.maxY() :
          box.minY(), p.normal().z() > 0 ? box.maxZ() : box.minZ());
      Vector3 neg = new Vector3(p.normal().x() > 0 ? box.minX() : box.maxX(), p.normal().y() > 0 ? box.minY() :
          box.maxY(), p.normal().z() > 0 ? box.minZ() : box.maxZ());
      if (p.distanceToPoint(pos) < 0) {
        return -1;
      }
      if (p.distanceToPoint(neg) < 0) {
        result = 0;
      }
    }
    return result;
  }

  /**
   * Tests whether a bounding box is completely inside the frustum.
   *
   * @param box the box to test
   * @return true if the box is fully inside (not intersecting boundaries)
   */
  public boolean containsBox(Box3D box) {
    for (int i = 0; i < 6; i++) {
      Plane p = planes[i];
      Vector3 pos = new Vector3(p.normal().x() > 0 ? box.maxX() : box.minX(), p.normal().y() > 0 ? box.maxY() :
          box.minY(), p.normal().z() > 0 ? box.maxZ() : box.minZ());
      if (p.distanceToPoint(pos) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests a ray against this frustum.
   *
   * @param ray the ray to test
   * @return ray parameter {@code t} at the entry, or {@link Float#NaN} if no intersection
   */
  public float testRay(Ray3D ray) {
    float tMin = 0.0F;
    float tMax = Float.MAX_VALUE;
    for (int i = 0; i < 6; i++) {
      Plane p = planes[i];
      float d = p.normal().dot(ray.direction());
      float dist = -(p.normal().dot(ray.origin()) + p.distance()) / d;
      if (Math.abs(d) < 1E-7F) {
        if (p.distanceToPoint(ray.origin()) < 0) {
          return Float.NaN;
        }
      } else {
        if (d > 0) {
          tMin = Math.max(tMin, dist);
        } else {
          tMax = Math.min(tMax, dist);
        }
      }
      if (tMin > tMax) {
        return Float.NaN;
      }
    }
    float t = tMin >= 0 ? tMin : tMax;
    return t >= 0 ? t : Float.NaN;
  }

  /**
   * Returns a new frustum transformed by the given matrix.
   *
   * <p>Useful for transforming the frustum into object space for local culling.
   *
   * @param matrix transformation matrix
   * @return transformed frustum
   */
  public Frustum transform(Matrix4x4 matrix) {
    Plane[] newPlanes = new Plane[6];
    for (int i = 0; i < 6; i++) {
      newPlanes[i] = planes[i].transform(matrix);
    }
    return new Frustum(newPlanes);
  }

  /**
   * Returns a new frustum transformed by the inverse of the given matrix.
   *
   * <p>Equivalent to {@code frustum.transform(matrix.invert())}.
   *
   * @param matrix transformation matrix
   * @return transformed frustum in the matrix's local space
   */
  public Frustum transformInverse(Matrix4x4 matrix) {
    return transform(matrix.invert());
  }

  /**
   * Extracts frustum planes from a view-projection matrix using Gribb/Hartmann algorithm.
   *
   * @param m combined view-projection matrix
   */
  private void genPlanes(Matrix4x4 m) {
    Vector4 r0 = new Vector4(m.m00(), m.m10(), m.m20(), m.m30()); // row 0
    Vector4 r1 = new Vector4(m.m01(), m.m11(), m.m21(), m.m31()); // row 1
    Vector4 r2 = new Vector4(m.m02(), m.m12(), m.m22(), m.m32()); // row 2
    Vector4 r3 = new Vector4(m.m03(), m.m13(), m.m23(), m.m33()); // row 3

    Vector4[] eq = {r3.add(r0), // left
        r3.subtract(r0), // right
        r3.add(r1), // bottom
        r3.subtract(r1), // top
        r3.add(r2), // near
        r3.subtract(r2)  // far
    };

    for (int i = 0; i < 6; i++) {
      Vector3 n = new Vector3(eq[i].x(), eq[i].y(), eq[i].z());
      float len = n.length();
      if (len > 1E-10F) {
        planes[i] = new Plane(n.divide(len), eq[i].w() / len);
      } else {
        planes[i] = new Plane(Vector3.UNIT_Z, 0.0F);
      }
    }
  }

  private void genCorners() {
    corners[0] = intersectOf(planes[4], planes[0], planes[2]); // near-left-bottom
    corners[1] = intersectOf(planes[4], planes[1], planes[2]); // near-right-bottom
    corners[2] = intersectOf(planes[4], planes[1], planes[3]); // near-right-top
    corners[3] = intersectOf(planes[4], planes[0], planes[3]); // near-left-top
    corners[4] = intersectOf(planes[5], planes[0], planes[2]); // far-left-bottom
    corners[5] = intersectOf(planes[5], planes[1], planes[2]); // far-right-bottom
    corners[6] = intersectOf(planes[5], planes[1], planes[3]); // far-right-top
    corners[7] = intersectOf(planes[5], planes[0], planes[3]); // far-left-top
  }

  /**
   * Symbolic indices for the six frustum planes.
   */
  public enum PlaneIndex {
    LEFT,
    RIGHT,
    BOTTOM,
    TOP,
    NEAR,
    FAR
  }
}