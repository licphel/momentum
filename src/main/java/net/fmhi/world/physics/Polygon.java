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

package net.fmhi.world.physics;

import net.fmhi.math.Box2D;
import net.fmhi.math.Vector2;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Immutable convex polygon for collision detection and physics.
 *
 * <p>Vertices are stored in counter-clockwise order. All methods that would
 * mutate return new instances.
 *
 * <p>Thread-safe: all instances are effectively immutable.
 *
 * @see IntersectResult
 */
@NullMarked
public final class Polygon {
  private static final float EPSILON = 1E-6F;
  public static final Polygon CUBE = Polygon.fromBox(Box2D.create(0, 0, 1, 1));

  private final Vector2[] vertices;
  private final Box2D boundBox;

  private Polygon(Vector2[] vertices, Box2D boundBox) {
    this.vertices = vertices;
    this.boundBox = boundBox;
  }

  /**
   * Creates a polygon from the given vertices. The caller must ensure
   * vertices are in counter-clockwise order and form a convex polygon.
   * A defensive copy is made.
   *
   * @param vertices polygon vertices in CCW order
   * @throws IllegalArgumentException if fewer than 3 vertices
   */
  public Polygon(Vector2... vertices) {
    if (vertices.length < 3) {
      throw new IllegalArgumentException(
          "Polygon requires at least 3 vertices, got " + vertices.length);
    }
    this.vertices = vertices.clone();
    this.boundBox = computeBounds(this.vertices);
  }

  /**
   * Creates a polygon from a list of vertices.
   *
   * @param vertices polygon vertices in CCW order
   * @throws IllegalArgumentException if fewer than 3 vertices
   */
  public Polygon(List<Vector2> vertices) {
    this(vertices.toArray(Vector2[]::new));
  }

  // -- factories ------------------------------------------------------------

  /**
   * Creates an axis-aligned rectangular polygon from a bounding box.
   *
   * @param box the axis-aligned box
   * @return a 4-vertex CCW polygon
   */
  public static Polygon fromBox(Box2D box) {
    return new Polygon(
        new Vector2(box.minX(), box.minY()),
        new Vector2(box.maxX(), box.minY()),
        new Vector2(box.maxX(), box.maxY()),
        new Vector2(box.minX(), box.maxY())
    );
  }

  /**
   * Creates the convex hull of a set of points using the monotone chain
   * algorithm. Runs in O(n log n) time.
   *
   * <p>If all points are collinear, the result is {@code null}.
   * The hull vertices are in CCW order.
   *
   * @param points the input points
   * @return the convex hull polygon, or {@code null} if fewer than 3
   * non-collinear points
   */
  public static Polygon convexHull(Vector2[] points) {
    if (points.length < 3) {
      return null;
    }

    var sorted = points.clone();
    Arrays.sort(sorted,
        Comparator.comparingDouble(Vector2::x).thenComparingDouble(Vector2::y));

    var lower = new ArrayList<Vector2>();
    for (var p : sorted) {
      while (lower.size() >= 2
          && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), p) <= 0F) {
        lower.removeLast();
      }
      lower.add(p);
    }

    var upper = new ArrayList<Vector2>();
    for (int i = sorted.length - 1; i >= 0; i--) {
      var p = sorted[i];
      while (upper.size() >= 2
          && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), p) <= 0F) {
        upper.removeLast();
      }
      upper.add(p);
    }

    lower.removeLast();
    upper.removeLast();
    lower.addAll(upper);

    if (lower.size() < 3) {
      return null;
    }

    return new Polygon(lower);
  }

  /**
   * Clips {@code subject} against the convex {@code clip} polygon using
   * the Sutherland-Hodgman algorithm.
   *
   * @param subject the polygon to clip
   * @param clip    the convex clipping polygon
   * @return the intersection polygon, or {@code null} if the result is empty
   */
  public static Polygon clip(Polygon subject, Polygon clip) {
    if (subject.sides() == 0) {
      return null;
    }

    var output = new ArrayList<>(Arrays.asList(subject.vertices));

    for (int i = 0; i < clip.sides(); i++) {
      if (output.isEmpty()) {
        break;
      }

      var clipEdge = clip.sideAt(i);
      var input = new ArrayList<>(output);
      output.clear();

      Vector2 s = input.getLast();
      for (var e : input) {
        if (isInsideEdge(clipEdge, e)) {
          if (!isInsideEdge(clipEdge, s)) {
            output.add(lineIntersection(clipEdge[0], clipEdge[1], s, e));
          }
          output.add(e);
        } else if (isInsideEdge(clipEdge, s)) {
          output.add(lineIntersection(clipEdge[0], clipEdge[1], s, e));
        }
        s = e;
      }
    }

    if (output.size() < 3) {
      return null;
    }

    return new Polygon(output);
  }

  // -- accessors ------------------------------------------------------------

  private static float cross(Vector2 a, Vector2 b, Vector2 c) {
    return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
  }

  private static float isLeft(Vector2 a, Vector2 b, Vector2 c) {
    return (b.x() - a.x()) * (c.y() - a.y()) - (c.x() - a.x()) * (b.y() - a.y());
  }

  private static Vector2 rot90Norm(Vector2 v) {
    float len = v.length();
    if (len < EPSILON) {
      return Vector2.ZERO;
    }
    return new Vector2(-v.y() / len, v.x() / len);
  }

  private static boolean isInsideEdge(Vector2[] edge, Vector2 p) {
    float ex = edge[1].x() - edge[0].x();
    float ey = edge[1].y() - edge[0].y();
    float px = p.x() - edge[0].x();
    float py = p.y() - edge[0].y();
    return ex * py - ey * px > 0F;
  }

  private static Vector2 lineIntersection(
      Vector2 a1, Vector2 a2, Vector2 b1, Vector2 b2) {
    float x1 = a1.x();
    float y1 = a1.y();
    float x2 = a2.x();
    float y2 = a2.y();
    float x3 = b1.x();
    float y3 = b1.y();
    float x4 = b2.x();
    float y4 = b2.y();

    float d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
    if (Math.abs(d) < EPSILON) {
      return a2;
    }
    float t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / d;
    return new Vector2(x1 + t * (x2 - x1), y1 + t * (y2 - y1));
  }

  private static float pointToSegmentDistance(
      Vector2 p, Vector2 a, Vector2 b) {
    float abx = b.x() - a.x();
    float aby = b.y() - a.y();
    float len2 = abx * abx + aby * aby;
    if (len2 < EPSILON) {
      return Vector2.distance(p, a);
    }
    float t = Math.clamp(
        ((p.x() - a.x()) * abx + (p.y() - a.y()) * aby) / len2, 0F, 1F);
    var proj = new Vector2(a.x() + t * abx, a.y() + t * aby);
    return Vector2.distance(p, proj);
  }

  private static Box2D computeBounds(Vector2[] verts) {
    float mnX = verts[0].x();
    float mxX = mnX;
    float mnY = verts[0].y();
    float mxY = mnY;
    for (var v : verts) {
      mnX = Math.min(mnX, v.x());
      mxX = Math.max(mxX, v.x());
      mnY = Math.min(mnY, v.y());
      mxY = Math.max(mxY, v.y());
    }
    return new Box2D(mnX, mnY, mxX, mxY);
  }

  /**
   * Returns a copy of the vertices in CCW order.
   *
   * @return vertex array copy
   */
  public Vector2[] vertices() {
    return vertices.clone();
  }

  // -- queries --------------------------------------------------------------

  /**
   * Returns the vertex at the given index, wrapping around.
   *
   * @param i the vertex index (modulo side count)
   * @return the vertex
   */
  public Vector2 vertex(int i) {
    return vertices[Math.floorMod(i, vertices.length)];
  }

  /**
   * Returns the number of sides (vertices).
   *
   * @return side count
   */
  public int sides() {
    return vertices.length;
  }

  /**
   * Returns the pre-computed axis-aligned bounding box.
   *
   * @return the AABB
   */
  public Box2D boundBox() {
    return boundBox;
  }

  /**
   * Returns the outward-facing normal of the given side.
   *
   * <p>For a CCW polygon the normal of edge (vᵢ → vᵢ₊₁) is the
   * 90&#xB0; CCW rotation of the edge direction.
   *
   * @param i side index
   * @return normalized outward normal, or {@link Vector2#ZERO} for a
   * degenerate edge
   */
  public Vector2 normal(int i) {
    int j = (i + 1) % vertices.length;
    float dx = vertices[j].x() - vertices[i].x();
    float dy = vertices[j].y() - vertices[i].y();
    float len = (float) Math.sqrt(dx * dx + dy * dy);
    if (len < EPSILON) {
      return Vector2.ZERO;
    }
    return new Vector2(-dy / len, dx / len);
  }

  /**
   * Returns the edge as a pair of vertices: (start, end).
   *
   * @param i side index
   * @return (vertex[i], vertex[i+1]) as a 2-element array
   */
  public Vector2[] sideAt(int i) {
    int j = (i + 1) % vertices.length;
    return new Vector2[] {vertices[i], vertices[j]};
  }

  // -- transformations -------------------------------------------------------

  /**
   * Returns the centroid (average of all vertices).
   *
   * @return the center point
   */
  public Vector2 center() {
    float cx = 0F;
    float cy = 0F;
    for (var v : vertices) {
      cx += v.x();
      cy += v.y();
    }
    float n = vertices.length;
    return new Vector2(cx / n, cy / n);
  }

  /**
   * Returns the area of this convex polygon (positive for CCW ordering).
   *
   * @return the signed area
   */
  public float convexArea() {
    float area = 0F;
    for (int i = 0; i < vertices.length; i++) {
      var v1 = vertices[i];
      var v2 = vertices[(i + 1) % vertices.length];
      area += v1.x() * v2.y() - v1.y() * v2.x();
    }
    return 0.5F * area;
  }

  /**
   * Tests whether the given point is inside or on the boundary using the
   * winding number algorithm.
   *
   * @param p the point to test
   * @return true if the point is inside or on the boundary
   */
  public boolean contains(Vector2 p) {
    return windingNumber(p) != 0;
  }

  // -- equality -------------------------------------------------------------

  /**
   * Returns the winding number of the point relative to this polygon.
   * 0 means outside, &#xB1;1 means inside (for simple polygons).
   *
   * @param p the point
   * @return the winding number
   */
  public int windingNumber(Vector2 p) {
    int wn = 0;
    for (int i = 0; i < vertices.length; i++) {
      var v1 = vertices[i];
      var v2 = vertices[(i + 1) % vertices.length];
      if (v1.y() <= p.y()) {
        if (v2.y() > p.y() && isLeft(v1, v2, p) > 0F) {
          wn++;
        }
      } else {
        if (v2.y() <= p.y() && isLeft(v1, v2, p) < 0F) {
          wn--;
        }
      }
    }
    return wn;
  }

  /**
   * SAT (Separating Axis Theorem) intersection test for two convex polygons.
   *
   * <p>If the polygons intersect, the result contains the minimum translation
   * vector (MTV) that should be applied to {@code this} polygon to separate
   * it from {@code other}.
   *
   * @param other the other convex polygon
   * @return the intersection result
   */
  public IntersectResult satIntersection(Polygon other) {
    float shortestOverlap = Float.MAX_VALUE;
    float sepX = 0F;
    float sepY = 0F;

    // test axes from this polygon's edge normals
    if (vertices.length > 0) {
      Vector2 pv = vertices[vertices.length - 1];
      for (var v : vertices) {
        Vector2 edge = pv.subtract(v);
        if (edge.lengthSquared() > EPSILON * EPSILON) {
          Vector2 axis = rot90Norm(edge); // outward normal of this edge
          // project onto -axis (inward)
          float ax = -axis.x();
          float ay = -axis.y();

          float myLow = Float.MAX_VALUE;
          float otherHigh = -Float.MAX_VALUE;
          for (var tv : vertices) {
            float proj = ax * tv.x() + ay * tv.y();
            if (proj < myLow) {
              myLow = proj;
            }
          }
          for (var ov : other.vertices) {
            float proj = ax * ov.x() + ay * ov.y();
            if (proj > otherHigh) {
              otherHigh = proj;
            }
          }

          float overlap = otherHigh - myLow;
          if (overlap < shortestOverlap) {
            shortestOverlap = overlap;
            sepX = ax;
            sepY = ay;
            if (overlap <= 0F) {
              break; // separated
            }
          }
        }
        pv = v;
      }
    }

    // test axes from other polygon's edge normals
    if (shortestOverlap > 0F && other.vertices.length > 0) {
      Vector2 pv = other.vertices[other.vertices.length - 1];
      for (var v : other.vertices) {
        Vector2 edge = pv.subtract(v);
        if (edge.lengthSquared() > EPSILON * EPSILON) {
          Vector2 axis = rot90Norm(edge); // outward normal of other's edge
          float ax = axis.x();
          float ay = axis.y();

          float myLow = Float.MAX_VALUE;
          float otherHigh = -Float.MAX_VALUE;
          for (var tv : vertices) {
            float proj = ax * tv.x() + ay * tv.y();
            if (proj < myLow) {
              myLow = proj;
            }
          }
          for (var ov : other.vertices) {
            float proj = ax * ov.x() + ay * ov.y();
            if (proj > otherHigh) {
              otherHigh = proj;
            }
          }

          float overlap = otherHigh - myLow;
          if (overlap < shortestOverlap) {
            shortestOverlap = overlap;
            sepX = ax;
            sepY = ay;
            if (overlap <= 0F) {
              break; // separated
            }
          }
        }
        pv = v;
      }
    }

    if (shortestOverlap > 0F) {
      return new IntersectResult(true,
          new Vector2(sepX * shortestOverlap, sepY * shortestOverlap));
    }
    return IntersectResult.NO_INTERSECT;
  }

  /**
   * Directional SAT intersection.
   *
   * <p>Like {@link #satIntersection(Polygon)} but separation is constrained
   * to be parallel to the given direction. This is essential for
   * slope-aware movement:
   * <ul>
   *   <li>Horizontal movement: use {@code direction = (1, 0)} or
   *       {@code (-1, 0)} with {@code chooseSign = false}</li>
   *   <li>Vertical correction (slide up slopes): use
   *       {@code direction = (0, 1)} with {@code chooseSign = true}</li>
   * </ul>
   *
   * @param other      the other convex polygon
   * @param direction  the allowed separation direction (does not need to be
   *                   normalized)
   * @param chooseSign if true, separation may be in either direction parallel
   *                   to the given direction; if false, only in the exact
   *                   direction specified
   * @return the intersection result
   */
  public IntersectResult directionalSatIntersection(
      Polygon other, Vector2 direction, boolean chooseSign) {
    float shortestOverlap = Float.MAX_VALUE;
    float sepX = 0F;
    float sepY = 0F;
    float dirX = direction.x();
    float dirY = direction.y();

    // test axes from this polygon's edge normals
    if (vertices.length > 0) {
      Vector2 pv = vertices[vertices.length - 1];
      for (var v : vertices) {
        Vector2 edge = pv.subtract(v);
        if (edge.lengthSquared() > EPSILON * EPSILON) {
          Vector2 norm = rot90Norm(edge);
          float ax = -norm.x();
          float ay = -norm.y();

          float myLow = Float.MAX_VALUE;
          float otherHigh = -Float.MAX_VALUE;
          for (var tv : vertices) {
            float proj = ax * tv.x() + ay * tv.y();
            if (proj < myLow) {
              myLow = proj;
            }
          }
          for (var ov : other.vertices) {
            float proj = ax * ov.x() + ay * ov.y();
            if (proj > otherHigh) {
              otherHigh = proj;
            }
          }

          float overlap = otherHigh - myLow;

          if (overlap <= 0F) {
            if (overlap < shortestOverlap) {
              shortestOverlap = overlap;
              sepX = ax;
              sepY = ay;
            }
          } else {
            // try to resolve overlap along the requested direction
            float axisDot = ax * dirX + ay * dirY;
            if (axisDot != 0F) {
              float projOverlap = overlap / axisDot;
              if (chooseSign) {
                float absProj = projOverlap >= 0F ? projOverlap : -projOverlap;
                if (absProj < shortestOverlap) {
                  float sign = projOverlap >= 0F ? 1F : -1F;
                  shortestOverlap = absProj;
                  sepX = dirX * sign;
                  sepY = dirY * sign;
                }
              } else if (projOverlap >= 0F) {
                if (projOverlap < shortestOverlap) {
                  shortestOverlap = projOverlap;
                  sepX = dirX;
                  sepY = dirY;
                }
              }
            }
          }
        }
        pv = v;
      }
    }

    // test axes from other polygon's edge normals
    if (shortestOverlap > 0F && other.vertices.length > 0) {
      Vector2 pv = other.vertices[other.vertices.length - 1];
      for (var v : other.vertices) {
        Vector2 edge = pv.subtract(v);
        if (edge.lengthSquared() > EPSILON * EPSILON) {
          Vector2 norm = rot90Norm(edge);
          float ax = norm.x();
          float ay = norm.y();

          float myLow = Float.MAX_VALUE;
          float otherHigh = -Float.MAX_VALUE;
          for (var tv : vertices) {
            float proj = ax * tv.x() + ay * tv.y();
            if (proj < myLow) {
              myLow = proj;
            }
          }
          for (var ov : other.vertices) {
            float proj = ax * ov.x() + ay * ov.y();
            if (proj > otherHigh) {
              otherHigh = proj;
            }
          }

          float overlap = otherHigh - myLow;

          if (overlap <= 0F) {
            if (overlap < shortestOverlap) {
              shortestOverlap = overlap;
              sepX = ax;
              sepY = ay;
            }
          } else {
            float axisDot = ax * dirX + ay * dirY;
            if (axisDot != 0F) {
              float projOverlap = overlap / axisDot;
              if (chooseSign) {
                float absProj = projOverlap >= 0F ? projOverlap : -projOverlap;
                if (absProj < shortestOverlap) {
                  float sign = projOverlap >= 0F ? 1F : -1F;
                  shortestOverlap = absProj;
                  sepX = dirX * sign;
                  sepY = dirY * sign;
                }
              } else if (projOverlap >= 0F) {
                if (projOverlap < shortestOverlap) {
                  shortestOverlap = projOverlap;
                  sepX = dirX;
                  sepY = dirY;
                }
              }
            }
          }
        }
        pv = v;
      }
    }

    if (shortestOverlap > 0F) {
      return new IntersectResult(true,
          new Vector2(sepX * shortestOverlap, sepY * shortestOverlap));
    }
    return IntersectResult.NO_INTERSECT;
  }

  // -- internal helpers -----------------------------------------------------

  /**
   * Returns the shortest distance from the given point to this polygon.
   * Returns 0 if the point is inside.
   *
   * @param p the point
   * @return the distance
   */
  public float distance(Vector2 p) {
    if (contains(p)) {
      return 0F;
    }
    float dist = Float.MAX_VALUE;
    for (int i = 0; i < vertices.length; i++) {
      dist = Math.min(dist,
          pointToSegmentDistance(p, vertices[i], vertices[(i + 1) % vertices.length]));
    }
    return dist;
  }

  /**
   * Returns a new polygon translated by the given offset.
   *
   * @param offset the translation vector
   * @return the translated polygon
   */
  public Polygon translate(Vector2 offset) {
    if (offset.equals(Vector2.ZERO)) {
      return this;
    }
    var newVerts = new Vector2[vertices.length];
    for (int i = 0; i < vertices.length; i++) {
      newVerts[i] = vertices[i].add(offset);
    }
    return new Polygon(newVerts, boundBox.translate(offset.x(), offset.y()));
  }

  /**
   * Returns a new polygon translated by (dx, dy).
   *
   * @param dx x offset
   * @param dy y offset
   * @return the translated polygon
   */
  public Polygon translate(float dx, float dy) {
    return translate(new Vector2(dx, dy));
  }

  /**
   * Returns a new polygon with its center moved to the given position.
   *
   * @param c the new center
   * @return the re-centered polygon
   */
  public Polygon withCenter(Vector2 c) {
    return translate(c.subtract(center()));
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(vertices);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Polygon other)) {
      return false;
    }
    return Arrays.equals(vertices, other.vertices);
  }

  @Override
  public String toString() {
    var sb = new StringBuilder("[Poly:");
    for (var v : vertices) {
      sb.append(' ').append(v);
    }
    sb.append(']');
    return sb.toString();
  }
}
