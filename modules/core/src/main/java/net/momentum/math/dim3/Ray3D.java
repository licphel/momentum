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
import org.jspecify.annotations.Nullable;

/**
 * Immutable 3D ray defined by an origin and a (normalized) direction.
 *
 * <p>A ray is a half-line: {@code origin + t * direction} for {@code t >= 0}.
 * The direction is automatically normalized on construction.
 *
 * @param origin    the starting point of the ray
 * @param direction the direction vector (automatically normalized)
 */
public record Ray3D(Vector3 origin, Vector3 direction) {
  public static final Ray3D ZERO = new Ray3D(Vector3.ZERO, Vector3.UNIT_X);

  /**
   * Auto-normalizes the direction on construction.
   *
   * @param origin    ray origin point
   * @param direction ray direction
   */
  public Ray3D {
    direction = direction.normalize();
  }

  /**
   * Creates a ray from one point to another.
   *
   * @param from start point
   * @param to   end point
   * @return ray from {@code from} toward {@code to}
   */
  public static Ray3D createFromPoints(Vector3 from, Vector3 to) {
    return new Ray3D(from, to.subtract(from));
  }

  /**
   * Linearly interpolates between two rays.
   *
   * @param a start ray
   * @param b end ray
   * @param t interpolation factor
   * @return interpolated ray
   */
  public static Ray3D lerp(Ray3D a, Ray3D b, float t) {
    return new Ray3D(Vector3.lerp(a.origin, b.origin, t), Vector3.lerp(a.direction, b.direction, t));
  }

  /**
   * Creates a world-space picking ray from screen coordinates.
   *
   * @param screenX          screen X in pixels
   * @param screenY          screen Y in pixels (Y-down)
   * @param screenWidth      viewport width in pixels
   * @param screenHeight     viewport height in pixels
   * @param projectionMatrix projection matrix
   * @param viewMatrix       view matrix
   * @return world-space ray
   */
  public static Ray3D createFromScreen(float screenX, float screenY, float screenWidth, float screenHeight,
                                       Matrix4x4 projectionMatrix, Matrix4x4 viewMatrix) {
    float ndcX = 2.0F * screenX / screenWidth - 1.0F;
    float ndcY = 1.0F - 2.0F * screenY / screenHeight;
    Matrix4x4 invVP = projectionMatrix.multiply(viewMatrix).invert();
    Vector3 near = invVP.transform(new Vector3(ndcX, ndcY, -1.0F));
    Vector3 far = invVP.transform(new Vector3(ndcX, ndcY, 1.0F));
    return createFromPoints(near, far);
  }

  /**
   * Returns the point at parameter {@code t} along the ray.
   *
   * @param t distance along the ray (must be >= 0)
   * @return point at origin + t * direction
   */
  public Vector3 getPoint(float t) {
    return origin.add(direction.multiply(t));
  }

  /**
   * Returns the closest point on this ray to a given point.
   *
   * @param point the point to test
   * @return the closest point on the ray
   */
  public Vector3 getClosestPoint(Vector3 point) {
    float proj = Math.max(point.subtract(origin).dot(direction), 0.0F);
    return origin.add(direction.multiply(proj));
  }

  /**
   * Returns the shortest distance from a point to this ray.
   *
   * @param point the point to test
   * @return shortest distance
   */
  public float distanceTo(Vector3 point) {
    return (float) Math.sqrt(distanceSquaredTo(point));
  }

  /**
   * Returns the squared shortest distance from a point to this ray.
   *
   * @param point the point to test
   * @return squared shortest distance
   */
  public float distanceSquaredTo(Vector3 point) {
    Vector3 op = point.subtract(origin);
    float proj = op.dot(direction);
    if (proj < 0) {
      return op.lengthSquared();
    }
    return point.subtract(origin.add(direction.multiply(proj))).lengthSquared();
  }

  /**
   * Tests intersection with a sphere.
   *
   * @param sphere the sphere to test
   * @return ray parameter {@code t} at the first hit, or {@code Float.NaN}
   */
  public float intersectsSphere(Sphere sphere) {
    Vector3 oc = origin.subtract(sphere.center());
    float b = oc.dot(direction);
    float c = oc.dot(oc) - sphere.radius() * sphere.radius();
    float disc = b * b - c;
    if (disc < 0.0F) {
      return Float.NaN;
    }
    float sqrtDisc = (float) Math.sqrt(disc);
    float t = -b - sqrtDisc;
    if (t >= 0.0F) {
      return t;
    }
    t = -b + sqrtDisc;
    return t >= 0.0F ? t : Float.NaN;
  }

  /**
   * Tests intersection with an axis-aligned bounding box.
   *
   * @param box the box to test
   * @return ray parameter {@code t} at the entry, or {@code Float.NaN}
   */
  public float intersectsBox(Box3D box) {
    float tmin = Float.NEGATIVE_INFINITY;
    float tmax = Float.POSITIVE_INFINITY;

    float[] orig = {origin.x(), origin.y(), origin.z()};
    float[] dir = {direction.x(), direction.y(), direction.z()};
    float[] bMin = {box.minX(), box.minY(), box.minZ()};
    float[] bMax = {box.maxX(), box.maxY(), box.maxZ()};

    for (int i = 0; i < 3; i++) {
      if (Math.abs(dir[i]) < 1E-8F) {
        if (orig[i] < bMin[i] || orig[i] > bMax[i]) {
          return Float.NaN;
        }
      } else {
        float t1 = (bMin[i] - orig[i]) / dir[i];
        float t2 = (bMax[i] - orig[i]) / dir[i];
        if (t1 > t2) {
          float tmp = t1;
          t1 = t2;
          t2 = tmp;
        }
        tmin = Math.max(tmin, t1);
        tmax = Math.min(tmax, t2);
        if (tmin > tmax) {
          return Float.NaN;
        }
      }
    }
    return tmin >= 0.0F ? tmin : (tmax >= 0.0F ? tmax : Float.NaN);
  }

  /**
   * Tests intersection with a plane.
   *
   * @param plane the plane to test
   * @return ray parameter {@code t}, or {@code Float.NaN}
   */
  public float intersectsPlane(Plane plane) {
    float d = plane.normal().dot(direction);
    if (Math.abs(d) < 1E-7F) {
      return Float.NaN;
    }
    float t = -(plane.normal().dot(origin) + plane.distance()) / d;
    return t >= 0.0F ? t : Float.NaN;
  }

  /**
   * Triangle intersection test.
   *
   * @param v0 first triangle vertex
   * @param v1 second triangle vertex
   * @param v2 third triangle vertex
   * @return ray parameter {@code t}, or {@code Float.NaN} if no intersection
   */
  public float intersectsTriangle(Vector3 v0, Vector3 v1, Vector3 v2) {
    final float EPSILON = 1E-7F;
    Vector3 e1 = v1.subtract(v0);
    Vector3 e2 = v2.subtract(v0);
    Vector3 h = direction.cross(e2);
    float a = e1.dot(h);
    if (Math.abs(a) < EPSILON) {
      return Float.NaN; // Parallel
    }

    float f = 1.0F / a;
    Vector3 s = origin.subtract(v0);
    float u = f * s.dot(h);
    if (u < 0.0F || u > 1.0F) {
      return Float.NaN;
    }

    Vector3 q = s.cross(e1);
    float v = f * direction.dot(q);
    if (v < 0.0F || u + v > 1.0F) {
      return Float.NaN;
    }

    float t = f * e2.dot(q);
    return t >= EPSILON ? t : Float.NaN;
  }

  /**
   * Reflects this ray off a surface at the given point.
   *
   * @param normal surface normal (must be normalized)
   * @param point  reflection point on the surface
   * @return reflected ray
   */
  public Ray3D reflect(Vector3 normal, Vector3 point) {
    return new Ray3D(point, direction.reflect(normal));
  }

  /**
   * Reflects this ray using its existing origin.
   *
   * @param normal surface normal (must be normalized)
   * @return reflected ray
   */
  public Ray3D reflect(Vector3 normal) {
    return new Ray3D(origin, direction.reflect(normal));
  }

  /**
   * Refracts this ray through a surface.
   *
   * @param normal surface normal (must be normalized)
   * @param point  refraction point
   * @param ior    index of refraction (n2 / n1)
   * @return refracted ray, or {@code null} if total internal reflection
   */
  public @Nullable Ray3D refract(Vector3 normal, Vector3 point, float ior) {
    float cosI = -normal.dot(direction);
    float sinT2 = ior * ior * (1.0F - cosI * cosI);
    if (sinT2 > 1.0F) {
      return null;
    }
    float cosT = (float) Math.sqrt(1.0F - sinT2);
    Vector3 refDir = direction.multiply(ior).add(normal.multiply(ior * cosI - cosT));
    return new Ray3D(point, refDir);
  }

  /**
   * Transforms this ray by a matrix.
   *
   * @param m transformation matrix
   * @return transformed ray
   */
  public Ray3D transform(Matrix4x4 m) {
    Vector3 newOrigin = m.transform(origin);
    Vector3 newDir = m.transform(origin.add(direction)).subtract(newOrigin);
    return new Ray3D(newOrigin, newDir);
  }
}