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

package net.fmhi.math;

/**
 * Immutable axis-aligned 3D bounding box defined by min and max corners.
 *
 * @param minX minimum x coordinate
 * @param minY minimum y coordinate
 * @param minZ minimum z coordinate
 * @param maxX maximum x coordinate
 * @param maxY maximum y coordinate
 * @param maxZ maximum z coordinate
 */
public record Box3D(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
  public static final Box3D ZERO = new Box3D(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

  /**
   * Creates a box from min and max corners.
   *
   * @param min minimum corner (inclusive)
   * @param max maximum corner (inclusive)
   */
  public Box3D(Vector3 min, Vector3 max) {
    this(min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
  }

  /**
   * Creates a box from a corner position and dimensions.
   *
   * @param position bottom-left-back corner
   * @param width    box width
   * @param height   box height
   * @param depth    box depth
   * @return new box
   */
  public static Box3D create(Vector3 position, float width, float height, float depth) {
    return new Box3D(position.x(), position.y(), position.z(), position.x() + width, position.y() + height,
        position.z() + depth);
  }

  /**
   * Creates a box centered at a point with the given half-extents.
   *
   * @param center center point
   * @param halfW  half width
   * @param halfH  half height
   * @param halfD  half depth
   * @return new box
   */
  public static Box3D createCentral(Vector3 center, float halfW, float halfH, float halfD) {
    return new Box3D(center.x() - halfW, center.y() - halfH, center.z() - halfD, center.x() + halfW,
        center.y() + halfH, center.z() + halfD);
  }

  /**
   * Creates the tightest box enclosing an array of points.
   *
   * @param points array of points
   * @return bounding box
   */
  public static Box3D createByPoints(Vector3[] points) {
    float mnX = points[0].x();
    float mxX = points[0].x();
    float mnY = points[0].y();
    float mxY = points[0].y();
    float mnZ = points[0].z();
    float mxZ = points[0].z();
    for (Vector3 p : points) {
      mnX = Math.min(mnX, p.x());
      mxX = Math.max(mxX, p.x());
      mnY = Math.min(mnY, p.y());
      mxY = Math.max(mxY, p.y());
      mnZ = Math.min(mnZ, p.z());
      mxZ = Math.max(mxZ, p.z());
    }
    return new Box3D(mnX, mnY, mnZ, mxX, mxY, mxZ);
  }

  /**
   * Creates a box from a corner position and dimensions.
   *
   * @param x      left x coordinate
   * @param y      bottom y coordinate
   * @param z      back z coordinate
   * @param width  box width
   * @param height box height
   * @param depth  box depth
   * @return new box
   */
  public static Box3D create(float x, float y, float z, float width, float height, float depth) {
    return new Box3D(x, y, z, x + width, y + height, z + depth);
  }

  /**
   * Creates a box centered at (cx, cy, cz) with the given half-extents.
   *
   * @param cx    center x
   * @param cy    center y
   * @param cz    center z
   * @param halfW half width
   * @param halfH half height
   * @param halfD half depth
   * @return new box
   */
  public static Box3D createCentral(float cx, float cy, float cz, float halfW, float halfH, float halfD) {
    return new Box3D(cx - halfW, cy - halfH, cz - halfD, cx + halfW, cy + halfH, cz + halfD);
  }

  /**
   * Returns the intersection of two boxes.
   *
   * @param a first box
   * @param b second box
   * @return intersecting box, or an empty box if they do not intersect
   */
  public static Box3D getIntersection(Box3D a, Box3D b) {
    return new Box3D(Math.max(a.minX, b.minX), Math.max(a.minY, b.minY), Math.max(a.minZ, b.minZ), Math.min(a.maxX,
        b.maxX), Math.min(a.maxY, b.maxY), Math.min(a.maxZ, b.maxZ));
  }

  /**
   * Returns the union of two boxes (smallest box containing both).
   *
   * @param a first box
   * @param b second box
   * @return union box
   */
  public static Box3D getUnion(Box3D a, Box3D b) {
    return new Box3D(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.min(a.minZ, b.minZ), Math.max(a.maxX,
        b.maxX), Math.max(a.maxY, b.maxY), Math.max(a.maxZ, b.maxZ));
  }

  /**
   * Returns the width of this box (maxX - minX).
   *
   * @return width
   */
  public float width() {
    return maxX - minX;
  }

  /**
   * Returns the height of this box (maxY - minY).
   *
   * @return height
   */
  public float height() {
    return maxY - minY;
  }

  /**
   * Returns the depth of this box (maxZ - minZ).
   *
   * @return depth
   */
  public float depth() {
    return maxZ - minZ;
  }

  /**
   * Returns the volume of this box (width * height * depth).
   *
   * @return volume
   */
  public float volume() {
    return width() * height() * depth();
  }

  /**
   * Returns the center x coordinate.
   *
   * @return (min x + max x) / 2
   */
  public float centralX() {
    return (minX + maxX) * 0.5F;
  }

  /**
   * Returns the center y coordinate.
   *
   * @return (min y + max y) / 2
   */
  public float centralY() {
    return (minY + maxY) * 0.5F;
  }

  /**
   * Returns the center z coordinate.
   *
   * @return (min y + max z) / 2
   */
  public float centralZ() {
    return (minZ + maxZ) * 0.5F;
  }

  /**
   * Returns the center point of this box.
   *
   * @return center vector
   */
  public Vector3 center() {
    return new Vector3(centralX(), centralY(), centralZ());
  }

  /**
   * Returns the size (width, height, depth) of this box.
   *
   * @return size vector
   */
  public Vector3 size() {
    return new Vector3(width(), height(), depth());
  }

  /**
   * Returns the min corner as a vector.
   *
   * @return (min x, min y, min z)
   */
  public Vector3 min() {
    return new Vector3(minX, minY, minZ);
  }

  /**
   * Returns the max corner as a vector.
   *
   * @return (max x, max y, max z)
   */
  public Vector3 max() {
    return new Vector3(maxX, maxY, maxZ);
  }

  /**
   * Returns whether this box intersects another.
   *
   * @param o the other box
   * @return true if they intersect
   */
  public boolean intersects(Box3D o) {
    return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
  }

  /**
   * Returns whether this box contains the given point.
   *
   * @param x x coordinate
   * @param y y coordinate
   * @param z z coordinate
   * @return true if point is inside
   */
  public boolean contains(float x, float y, float z) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
  }

  /**
   * Returns whether this box contains the given point.
   *
   * @param v the point
   * @return true if point is inside
   */
  public boolean contains(Vector3 v) {
    return contains(v.x(), v.y(), v.z());
  }

  /**
   * Returns whether this box fully contains another box.
   *
   * @param o the other box
   * @return true if {@code o} is inside this box
   */
  public boolean contains(Box3D o) {
    return o.minX >= minX && o.maxX <= maxX && o.minY >= minY && o.maxY <= maxY && o.minZ >= minZ && o.maxZ <= maxZ;
  }

  /**
   * Returns a new box inflated by the given amounts on each side.
   *
   * @param dx amount to add to left and right
   * @param dy amount to add to bottom and top
   * @param dz amount to add to back and front
   * @return inflated box
   */
  public Box3D inflate(float dx, float dy, float dz) {
    return new Box3D(minX - dx, minY - dy, minZ - dz, maxX + dx, maxY + dy, maxZ + dz);
  }

  /**
   * Returns a new box translated by the given amounts.
   *
   * @param tx x translation
   * @param ty y translation
   * @param tz z translation
   * @return translated box
   */
  public Box3D translate(float tx, float ty, float tz) {
    return new Box3D(minX + tx, minY + ty, minZ + tz, maxX + tx, maxY + ty, maxZ + tz);
  }

  /**
   * Returns a new box translated by the given vector.
   *
   * @param v translation vector
   * @return translated box
   */
  public Box3D translate(Vector3 v) {
    return translate(v.x(), v.y(), v.z());
  }

  /**
   * Projects to 2D (drops Z).
   *
   * @return 2D bounding box
   */
  public Box2D toBox2() {
    return new Box2D(minX, minY, maxX, maxY);
  }
}