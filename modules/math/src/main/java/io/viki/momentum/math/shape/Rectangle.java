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

package io.viki.momentum.math.shape;

import io.viki.momentum.math.Vector2;

/**
 * Immutable axis-aligned 2D bounding box defined by min and max corners.
 *
 * @param minX minimum x coordinate (left)
 * @param minY minimum y coordinate (bottom)
 * @param maxX maximum x coordinate (right)
 * @param maxY maximum y coordinate (top)
 *             <p>
 *             Instances are immutable and thread-safe.
 */
public record Rectangle(float minX, float minY, float maxX, float maxY) implements Shape<Rectangle> {
  /** A zero-area rectangle whose coordinates are all zero. */
  public static final Rectangle ZERO = new Rectangle(0.0F, 0.0F, 0.0F, 0.0F);

  /**
   * Creates a box from min and max corners.
   *
   * @param min minimum corner (inclusive)
   * @param max maximum corner (inclusive)
   */
  public Rectangle(Vector2 min, Vector2 max) {
    this(min.x(), min.y(), max.x(), max.y());
  }

  /**
   * Creates a box from a corner position and dimensions.
   *
   * @param position bottom-left corner
   * @param width    box width
   * @param height   box height
   * @return new box
   */
  public static Rectangle of(Vector2 position, float width, float height) {
    return new Rectangle(position.x(), position.y(), position.x() + width, position.y() + height);
  }

  /**
   * Creates a box from a corner position and dimensions.
   *
   * @param position bottom-left corner
   * @param size     box size (width, height)
   * @return new box
   */
  public static Rectangle of(Vector2 position, Vector2 size) {
    return new Rectangle(position.x(), position.y(), position.x() + size.x(), position.y() + size.y());
  }

  /**
   * Creates a box centered at a point with the given dimensions.
   *
   * @param center center point
   * @param width  full width
   * @param height full height
   * @return new box
   */
  public static Rectangle ofCentral(Vector2 center, float width, float height) {
    float halfW = width * 0.5F;
    float halfH = height * 0.5F;
    return new Rectangle(center.x() - halfW, center.y() - halfH, center.x() + halfW, center.y() + halfH);
  }

  /**
   * Creates a box centered at a point with the given dimensions.
   *
   * @param center center point
   * @param size   full size (width, height)
   * @return new box
   */
  public static Rectangle ofCentral(Vector2 center, Vector2 size) {
    float halfW = size.x() * 0.5F;
    float halfH = size.y() * 0.5F;
    return new Rectangle(center.x() - halfW, center.y() - halfH, center.x() + halfW, center.y() + halfH);
  }

  /**
   * Creates the tightest box enclosing an array of points.
   *
   * @param points array of points
   * @return bounding box
   */
  public static Rectangle createByPoints(Vector2[] points) {
    float mnX = points[0].x();
    float mxX = points[0].x();
    float mnY = points[0].y();
    float mxY = points[0].y();
    for (Vector2 p : points) {
      mnX = Math.min(mnX, p.x());
      mxX = Math.max(mxX, p.x());
      mnY = Math.min(mnY, p.y());
      mxY = Math.max(mxY, p.y());
    }
    return new Rectangle(mnX, mnY, mxX, mxY);
  }

  /**
   * Creates a box from a corner position and dimensions.
   *
   * @param x      left x coordinate
   * @param y      bottom y coordinate
   * @param width  box width
   * @param height box height
   * @return new box
   */
  public static Rectangle of(float x, float y, float width, float height) {
    return new Rectangle(x, y, x + width, y + height);
  }

  /**
   * Creates a box centered at (cx, cy) with the given dimensions.
   *
   * @param cx     center x
   * @param cy     center y
   * @param width  full width
   * @param height full height
   * @return new box
   */
  public static Rectangle ofCentral(float cx, float cy, float width, float height) {
    float halfW = width * 0.5F;
    float halfH = height * 0.5F;
    return new Rectangle(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
  }

  /**
   * Returns the intersection of two boxes.
   *
   * @param a first box
   * @param b second box
   * @return intersecting box, or an empty box if they do not intersect
   */
  public static Rectangle getIntersection(Rectangle a, Rectangle b) {
    return new Rectangle(Math.max(a.minX, b.minX), Math.max(a.minY, b.minY), Math.min(a.maxX, b.maxX), Math.min(a.maxY,
        b.maxY));
  }

  /**
   * Returns the union of two boxes (smallest box containing both).
   *
   * @param a first box
   * @param b second box
   * @return union box
   */
  public static Rectangle getUnion(Rectangle a, Rectangle b) {
    return new Rectangle(Math.min(a.minX, b.minX), Math.min(a.minY, b.minY), Math.max(a.maxX, b.maxX), Math.max(a.maxY,
        b.maxY));
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

  @Override
  public float area() {
    return width() * height();
  }

  @Override
  public float centralX() {
    return (minX + maxX) * 0.5F;
  }

  @Override
  public float centralY() {
    return (minY + maxY) * 0.5F;
  }

  @Override
  public Vector2 center() {
    return new Vector2(centralX(), centralY());
  }

  @Override
  public boolean contains(float x, float y) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY;
  }

  @Override
  public Rectangle scale(float sx, float sy) {
    return new Rectangle(minX * sx, minY * sy, maxX * sx, maxY * sy);
  }

  @Override
  public Rectangle translate(float tx, float ty) {
    return new Rectangle(minX + tx, minY + ty, maxX + tx, maxY + ty);
  }

  /**
   * Returns the size (width, height) of this box.
   *
   * @return size vector
   */
  public Vector2 size() {
    return new Vector2(width(), height());
  }

  /**
   * Returns whether this box intersects another.
   *
   * @param o the other box
   * @return true if they intersect
   */
  public boolean intersects(Rectangle o) {
    return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY;
  }

  /**
   * Returns whether this box fully contains another box.
   *
   * @param o the other box
   * @return true if {@code o} is inside this box
   */
  public boolean contains(Rectangle o) {
    return o.minX >= minX && o.maxX <= maxX && o.minY >= minY && o.maxY <= maxY;
  }

  /**
   * Returns a new box inflated by the given amounts on each side.
   *
   * @param dx amount to add to left and right
   * @param dy amount to add to bottom and top
   * @return inflated box
   */
  public Rectangle inflate(float dx, float dy) {
    return new Rectangle(minX - dx, minY - dy, maxX + dx, maxY + dy);
  }

  /**
   * Returns a new box inflated by the given amounts on each side.
   *
   * @param v inflating vector
   * @return inflated box
   */
  public Rectangle inflate(Vector2 v) {
    return inflate(v.x(), v.y());
  }
}
