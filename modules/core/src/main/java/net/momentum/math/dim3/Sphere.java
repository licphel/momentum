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

package net.momentum.math.dim3;

import net.momentum.math.Box3D;
import net.momentum.math.Matrix4x4;
import net.momentum.math.Vector3;

/**
 * Immutable 3D sphere.
 *
 * <p>A sphere is defined by a center point and a radius. It is useful for
 * bounding volume checks, culling, and collision detection.
 *
 * @param center the center point of the sphere
 * @param radius the radius (must be non-negative)
 */
public record Sphere(Vector3 center, float radius) {
  public static final Sphere ZERO = new Sphere(Vector3.ZERO, 0.0F);

  /**
   * Creates a sphere.
   *
   * @param center the center point
   * @param radius the radius; negative values are clamped to zero
   */
  public Sphere {
    radius = Math.max(0.0F, radius);
  }

  /**
   * Linearly interpolates between two spheres.
   *
   * @param a start sphere
   * @param b end sphere
   * @param t interpolation factor
   * @return interpolated sphere
   */
  public static Sphere lerp(Sphere a, Sphere b, float t) {
    return new Sphere(Vector3.lerp(a.center, b.center, t), a.radius * (1 - t) + b.radius * t);
  }

  /**
   * Creates a bounding sphere from a set of points using Ritter's algorithm.
   *
   * @param points array of points
   * @return bounding sphere
   * @throws IllegalArgumentException if points array is empty
   */
  public static Sphere createFromPoints(Vector3[] points) {
    if (points.length == 0) {
      throw new IllegalArgumentException("Points array cannot be empty");
    }

    // Ritter's bounding sphere approximation.
    Vector3 c = points[0];
    float r = 0.0F;
    for (Vector3 p : points) {
      float d = Vector3.distance(c, p);
      if (d > r) {
        r = d;
        c = Vector3.lerp(c, p, 0.5F);
      }
    }

    for (Vector3 p : points) {
      float d = Vector3.distance(c, p);
      if (d > r) {
        float newR = (r + d) * 0.5F;
        c = c.add(p.subtract(c).multiply((d - newR) / d));
        r = newR;
      }
    }
    return new Sphere(c, r);
  }

  /**
   * Creates a sphere that exactly bounds a box.
   *
   * @param box the box
   * @return bounding sphere
   */
  public static Sphere createFromBox(Box3D box) {
    return new Sphere(box.center(), box.size().length() * 0.5F);
  }

  /**
   * Returns the smallest sphere that contains both input spheres.
   *
   * @param a first sphere
   * @param b second sphere
   * @return union sphere
   */
  public static Sphere getUnion(Sphere a, Sphere b) {
    float d = Vector3.distance(a.center, b.center);
    float newR = (d + a.radius + b.radius) * 0.5F;
    if (newR <= a.radius) {
      return a;
    }
    if (newR <= b.radius) {
      return b;
    }
    Vector3 newC = a.center.add(b.center.subtract(a.center).multiply((newR - a.radius) / d));
    return new Sphere(newC, newR);
  }

  /**
   * Returns the diameter (radius * 2).
   *
   * @return diameter
   */
  public float diameter() {
    return radius * 2.0F;
  }

  /**
   * Returns the surface area (4πr²).
   *
   * @return surface area
   */
  public float surfaceArea() {
    return 4.0F * (float) Math.PI * radius * radius;
  }

  /**
   * Returns the volume (4/3 π r³).
   *
   * @return volume
   */
  public float volume() {
    return (4.0F / 3.0F) * (float) Math.PI * radius * radius * radius;
  }

  /**
   * Returns the axis-aligned bounding box of this sphere.
   *
   * @return bounding box
   */
  public Box3D boundingBox() {
    return new Box3D(center.x() - radius, center.y() - radius, center.z() - radius, center.x() + radius,
        center.y() + radius, center.z() + radius);
  }

  /**
   * Returns whether this sphere contains a point.
   *
   * @param point the point to test
   * @return true if the point is inside or on the surface
   */
  public boolean contains(Vector3 point) {
    return Vector3.distanceSquared(center, point) <= radius * radius;
  }

  /**
   * Returns whether this sphere fully contains another sphere.
   *
   * @param other the other sphere
   * @return true if other is completely inside this sphere
   */
  public boolean contains(Sphere other) {
    float d = Vector3.distance(center, other.center);
    return d + other.radius <= radius;
  }

  /**
   * Returns whether this sphere intersects another sphere.
   *
   * @param other the other sphere
   * @return true if they intersect
   */
  public boolean intersects(Sphere other) {
    float sum = radius + other.radius;
    return Vector3.distanceSquared(center, other.center) <= sum * sum;
  }

  /**
   * Returns whether this sphere intersects an axis-aligned bounding box.
   *
   * @param box the box
   * @return true if they intersect
   */
  public boolean intersects(Box3D box) {
    float dx = Math.max(0.0F, Math.max(box.minX() - center.x(), center.x() - box.maxX()));
    float dy = Math.max(0.0F, Math.max(box.minY() - center.y(), center.y() - box.maxY()));
    float dz = Math.max(0.0F, Math.max(box.minZ() - center.z(), center.z() - box.maxZ()));
    return dx * dx + dy * dy + dz * dz <= radius * radius;
  }

  /**
   * Returns whether this sphere intersects a plane.
   *
   * @param plane the plane
   * @return true if they intersect
   */
  public boolean intersects(Plane plane) {
    return Math.abs(plane.distanceToPoint(center)) <= radius;
  }

  /**
   * Returns a new sphere with increased radius.
   *
   * @param amount amount to add to the radius
   * @return expanded sphere
   */
  public Sphere expand(float amount) {
    return new Sphere(center, radius + amount);
  }

  /**
   * Returns a new sphere shifted by the given offset.
   *
   * @param offset translation vector
   * @return translated sphere
   */
  public Sphere translate(Vector3 offset) {
    return new Sphere(center.add(offset), radius);
  }

  /**
   * Returns a new sphere transformed by a matrix.
   *
   * <p>The radius is scaled by the maximum scale component of the matrix's
   * rotation/scale submatrix.
   *
   * @param m transformation matrix
   * @return transformed sphere
   */
  public Sphere transform(Matrix4x4 m) {
    Vector3 newCenter = m.transform(center);
    float sx = new Vector3(m.m00(), m.m10(), m.m20()).length();
    float sy = new Vector3(m.m01(), m.m11(), m.m21()).length();
    float sz = new Vector3(m.m02(), m.m12(), m.m22()).length();
    return new Sphere(newCenter, radius * Math.max(sx, Math.max(sy, sz)));
  }
}