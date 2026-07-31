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
import net.fmhi.math.Vector3;
import org.jspecify.annotations.Nullable;

/**
 * Immutable 3D plane defined by a normal vector and a signed distance from the origin.
 *
 * <p>The plane equation is: {@code normal · x + distance = 0}.
 * That is, for any point X on the plane, {@code normal.x * X.x + normal.y * X.y + normal.z * X.z + distance = 0}.
 *
 * <p>The distance is measured from the origin along the normal direction.
 * For a normalized normal, {@code distance} is the signed distance from the origin to the plane.
 *
 * @param normal   the plane's normal vector (not necessarily normalized on input)
 * @param distance the signed distance from the origin
 */
public record Plane(Vector3 normal, float distance) {
  public static final Plane ZERO = new Plane(Vector3.UNIT_X, 0.0F);

  /**
   * Creates a plane from three non-collinear points.
   *
   * <p>The plane's normal is oriented according to the right-hand rule:
   * {@code (b - a) × (c - a)}.
   *
   * @param a first point
   * @param b second point
   * @param c third point
   * @return plane passing through the three points
   */
  public static Plane createFromPoints(Vector3 a, Vector3 b, Vector3 c) {
    Vector3 n = b.subtract(a).cross(c.subtract(a)).normalize();
    return new Plane(n, -n.dot(a));
  }

  /**
   * Creates a plane from a normal and a point on the plane.
   *
   * @param normal the plane's normal (will be normalized)
   * @param point  a point on the plane
   * @return plane passing through the point with the given normal
   */
  public static Plane createFromNormalAndPoint(Vector3 normal, Vector3 point) {
    Vector3 n = normal.normalize();
    return new Plane(n, -n.dot(point));
  }

  /**
   * Returns a normalized version of this plane.
   *
   * <p>After normalization, the normal is a unit vector and the distance
   * becomes the true signed distance from the origin.
   *
   * @return normalized plane
   */
  public Plane normalize() {
    float len = normal.length();
    if (len < 1E-10F) {
      return this;
    }
    return new Plane(normal.divide(len), distance / len);
  }

  /**
   * Returns the signed distance from a point to this plane.
   *
   * @param point the point to test
   * @return signed distance (positive = same side as normal)
   */
  public float distanceToPoint(Vector3 point) {
    return normal.dot(point) + distance;
  }

  /**
   * Projects a point onto this plane.
   *
   * @param point the point to project
   * @return the closest point on the plane
   */
  public Vector3 projectPoint(Vector3 point) {
    return point.subtract(normal.multiply(distanceToPoint(point)));
  }

  /**
   * Returns which side of the plane a point lies on.
   *
   * @param point the point to test
   * @return {@code >0} if on the normal side, {@code <0} if opposite, {@code 0} if on the plane
   */
  public float whichSide(Vector3 point) {
    return distanceToPoint(point);
  }

  /**
   * Returns whether a point is on the same side as the normal.
   *
   * @param point the point to test
   * @return true if point is on the side the normal points to
   */
  public boolean isSameSide(Vector3 point) {
    return distanceToPoint(point) >= 0.0F;
  }

  /**
   * Returns whether two points are on the same side of the plane.
   *
   * @param a first point
   * @param b second point
   * @return true if both points are on the same side (or on the plane)
   */
  public boolean areSameSide(Vector3 a, Vector3 b) {
    float da = distanceToPoint(a);
    float db = distanceToPoint(b);
    return (da * db) >= 0.0F;
  }

  /**
   * Returns the intersection point of this plane with a ray.
   *
   * @param ray the ray to intersect
   * @return intersection point, or {@code null} if the ray is parallel or behind the origin
   */
  public @Nullable Vector3 intersectRay(Ray3D ray) {
    float d = normal.dot(ray.direction());
    if (Math.abs(d) < 1E-7F) {
      return null; // Parallel
    }
    float t = -(normal.dot(ray.origin()) + distance) / d;
    if (t < 0) {
      return null;
    }
    return ray.getPoint(t);
  }

  /**
   * Returns the intersection line of this plane with another plane.
   *
   * @param other the other plane
   * @return the line of intersection as a ray, or {@code null} if planes are parallel
   */
  public @Nullable Ray3D intersectPlane(Plane other) {
    Vector3 dir = normal.cross(other.normal);
    float d = dir.lengthSquared();
    if (d < 1E-10F) {
      return null; // parallel
    }

    // Find a point on the intersection line
    Vector3 n1 = normal;
    Vector3 n2 = other.normal;
    float d2 = other.distance;

    Vector3 point = n2.cross(n1).multiply(d2).add(n2.multiply(distance)).subtract(n1.multiply(d2));
    point = point.divide(d);

    return new Ray3D(point, dir.normalize());
  }

  /**
   * Transforms this plane by a matrix.
   *
   * <p>The normal is transformed by the inverse-transpose of the matrix's
   * 3×3 rotation/scale submatrix. A point on the plane is transformed and the new distance is recomputed.
   *
   * @param m transformation matrix
   * @return transformed plane
   */
  public Plane transform(Matrix4x4 m) {
    // Transform a point on the plane
    Vector3 pointOnPlane = normal.multiply(-distance);
    Vector3 newPoint = m.transform(pointOnPlane);

    // Normal transforms by the inverse-transpose (upper-left 3×3)
    Matrix4x4 invT = m.invert().transpose();
    Vector3 newNormal = new Vector3(invT.m00() * normal.x() + invT.m01() * normal.y() + invT.m02() * normal.z(),
        invT.m10() * normal.x() + invT.m11() * normal.y() + invT.m12() * normal.z(),
        invT.m20() * normal.x() + invT.m21() * normal.y() + invT.m22() * normal.z()).normalize();

    float newDist = -newNormal.dot(newPoint);
    return new Plane(newNormal, newDist);
  }

  /**
   * Translates this plane by a vector.
   *
   * @param offset translation vector
   * @return translated plane
   */
  public Plane translate(Vector3 offset) {
    float newDist = distance - normal.dot(offset);
    return new Plane(normal, newDist);
  }
}