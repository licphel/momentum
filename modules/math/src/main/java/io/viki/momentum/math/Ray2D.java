package io.viki.momentum.math;

import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Immutable, thread-safe two-dimensional half-line with a normalized direction.
 *
 * <p>A ray is represented by {@code origin + t * direction} for {@code t >= 0}. Geometric
 * queries return distances along the ray and do not allocate geometry objects.
 *
 * @param origin    the starting point of the ray
 * @param direction the direction vector, normalized during construction
 */
public record Ray2D(Vector2 origin, Vector2 direction) {
  /**
   * Creates a ray and normalizes its direction.
   *
   * @param origin    the starting point of the ray
   * @param direction the non-zero direction vector
   */
  public Ray2D {
    direction = direction.normalize();
  }

  /**
   * Creates a ray that starts at one point and points toward another.
   *
   * @param from the starting point
   * @param to   the point defining the direction
   * @return a ray from {@code from} toward {@code to}
   * @throws IllegalArgumentException if the two points are equal
   */
  public static Ray2D createFromPoints(Vector2 from, Vector2 to) {
    return new Ray2D(from, to.subtract(from));
  }

  /**
   * Returns the point at a distance along the ray.
   *
   * @param distance the signed distance from the origin
   * @return the point at {@code origin + distance * direction}
   */
  public Vector2 getPoint(float distance) {
    return origin.add(direction.multiply(distance));
  }

  /**
   * Returns the first distance at which this ray intersects an axis-aligned rectangle.
   *
   * @param box the rectangle to test
   * @return the first non-negative intersection distance, including zero when the origin is
   * inside the rectangle, or {@link Double#POSITIVE_INFINITY} when there is no hit
   */
  public double intersectsBox(Rectangle box) {
    double near = 0;
    double far = Double.POSITIVE_INFINITY;
    for (int axis = 0; axis < 2; axis++) {
      double p = axis == 0 ? origin.x() : origin.y();
      double d = axis == 0 ? direction.x() : direction.y();
      double min = axis == 0 ? box.minX() : box.minY();
      double max = axis == 0 ? box.maxX() : box.maxY();
      if (d == 0) {
        if (p < min || p > max) {
          return Double.POSITIVE_INFINITY;
        }
      } else {
        double a = (min - p) / d;
        double b = (max - p) / d;
        near = Math.max(near, Math.min(a, b));
        far = Math.min(far, Math.max(a, b));
        if (near > far) {
          return Double.POSITIVE_INFINITY;
        }
      }
    }
    return near;
  }

  /**
   * Returns the first distance at which this ray intersects any component of a polygon.
   *
   * <p>The polygon is tested at the supplied world-space offset without modifying it. The query
   * runs in O(vertices) time across all convex components.
   *
   * @param polygon the polygon to test
   * @param offsetX horizontal world-space offset of the polygon
   * @param offsetY vertical world-space offset of the polygon
   * @return the first non-negative intersection distance, or
   * {@link Double#POSITIVE_INFINITY} when there is no hit
   */
  public double intersectsPoly(Poly polygon, double offsetX, double offsetY) {
    double first = Double.POSITIVE_INFINITY;
    for (int component = 0; component < polygon.convexCount(); component++) {
      double near = 0;
      double far = first;
      int count = polygon.vertexCount(component);
      for (int i = 0; i < count; i++) {
        int next = (i + 1) % count;
        double x = polygon.vertexX(component, i) + offsetX;
        double y = polygon.vertexY(component, i) + offsetY;
        double ex = polygon.vertexX(component, next) - polygon.vertexX(component, i);
        double ey = polygon.vertexY(component, next) - polygon.vertexY(component, i);
        double side = ex * (origin.y() - y) - ey * (origin.x() - x);
        double rate = ex * direction.y() - ey * direction.x();
        if (rate == 0) {
          if (side < 0) {
            far = -1;
            break;
          }
        } else if (rate > 0) {
          near = Math.max(near, -side / rate);
        } else {
          far = Math.min(far, -side / rate);
        }
        if (near > far) {
          break;
        }
      }
      if (near <= far) {
        first = Math.min(first, near);
      }
    }
    return first;
  }

  /**
   * Visits each grid tile traversed by this ray up to a maximum distance.
   *
   * <p>The origin tile is visited first, including when the maximum distance is zero. Returning
   * {@code false} from the visitor stops traversal immediately, including from the origin.
   *
   * @param maximumDistance the largest distance to traverse, which must be non-negative
   * @param visitor         callback receiving tile coordinates and entry and exit distances
   * @return {@code true} if traversal reaches the maximum distance, or {@code false} if the
   * visitor stops it or a tile coordinate exceeds the integer range
   * @throws IllegalArgumentException if {@code maximumDistance} is negative
   */
  public boolean visitTiles(double maximumDistance, TileVisitor visitor) {
    if (maximumDistance < 0) {
      throw new IllegalArgumentException("Ray distance cannot be negative");
    }
    long x = (long) Math.floor(origin.x());
    long y = (long) Math.floor(origin.y());
    int sx = direction.x() > 0 ? 1 : direction.x() < 0 ? -1 : 0;
    int sy = direction.y() > 0 ? 1 : direction.y() < 0 ? -1 : 0;
    double stepX = sx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / direction.x());
    double stepY = sy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1D / direction.y());
    double nextX = sx == 0 ? Double.POSITIVE_INFINITY
        : ((sx > 0 ? x + 1 : x) - origin.x()) / direction.x();
    double nextY = sy == 0 ? Double.POSITIVE_INFINITY
        : ((sy > 0 ? y + 1 : y) - origin.y()) / direction.y();
    double entry = 0;
    while (entry <= maximumDistance) {
      if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
          || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) {
        return false;
      }
      double exit = Math.min(nextX, nextY);
      if (!visitor.visit((int) x, (int) y, entry, Math.min(exit, maximumDistance))) {
        return false;
      }
      if (exit >= maximumDistance) {
        return true;
      }
      boolean crossX = nextX <= nextY;
      boolean crossY = nextY <= nextX;
      if (crossX) {
        x += sx;
        nextX += stepX;
      }
      if (crossY) {
        y += sy;
        nextY += stepY;
      }
      entry = exit;
    }
    return true;
  }

  /**
   * Receives the segment of a ray's path through one grid tile.
   */
  @FunctionalInterface
  public interface TileVisitor {
    /**
     * Processes one traversed tile.
     *
     * @param x     the tile's horizontal coordinate
     * @param y     the tile's vertical coordinate
     * @param entry the distance at which the ray enters the tile
     * @param exit  the distance at which the ray exits the tile
     * @return {@code true} to continue traversal, or {@code false} to stop
     */
    boolean visit(int x, int y, double entry, double exit);
  }
}
