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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable two-dimensional polygon represented as one or more convex parts.
 * Instances are thread-safe.
 */
public final class Poly implements Shape<Poly> {
  /** An empty polygon with no convex components. */
  public static final Poly EMPTY = new Poly();
  /** Tolerance used for geometric equality, orientation, and intersection checks. */
  private static final float GEOMETRY_EPSILON = 1.0E-6F;
  /** Minimum number of vertices required to define a polygon. */
  private static final int MINIMUM_VERTEX_COUNT = 3;
  /** Number of coordinate values stored for each vertex. */
  private static final int COORDINATE_STRIDE = 2;

  private final ConvexPart[] parts;
  private final Rectangle bounds;
  private final float area;

  private Poly(ConvexPart... parts) {
    this.parts = parts;

    if (parts.length == 0) {
      bounds = Rectangle.ZERO;
      area = 0.0F;
      return;
    }

    float minX = Float.POSITIVE_INFINITY;
    float minY = Float.POSITIVE_INFINITY;
    float maxX = Float.NEGATIVE_INFINITY;
    float maxY = Float.NEGATIVE_INFINITY;
    float totalArea = 0.0F;
    for (ConvexPart part : parts) {
      minX = Math.min(minX, part.minX);
      minY = Math.min(minY, part.minY);
      maxX = Math.max(maxX, part.maxX);
      maxY = Math.max(maxY, part.maxY);
      totalArea += part.area;
    }

    bounds = new Rectangle(minX, minY, maxX, maxY);
    area = totalArea;
  }

  /**
   * Creates a polygon from a simple vertex loop. Concave loops are decomposed
   * into convex triangles. Clockwise and counter-clockwise input are accepted.
   *
   * @param vertices ordered vertices of a simple polygon
   * @return immutable polygon
   * @throws IllegalArgumentException if the vertices do not define a valid simple polygon
   */
  public static Poly of(Vector2... vertices) {
    float[] coordinates = sanitize(vertices);
    validateSimple(coordinates);
    if (isConvex(coordinates)) {
      return new Poly(ConvexPart.of(coordinates));
    }
    return new Poly(decompose(coordinates));
  }

  /**
   * Creates a compound polygon from existing polygons. Components may be
   * disconnected. Their interiors are expected not to overlap.
   *
   * @param polygons polygons to combine
   * @return immutable compound polygon
   * @throws ArithmeticException if the combined number of convex components exceeds the integer range
   */
  public static Poly of(Poly... polygons) {
    if (polygons.length == 0) {
      return EMPTY;
    }

    int partCount = 0;
    for (Poly polygon : polygons) {
      partCount = Math.addExact(partCount, polygon.parts.length);
    }
    if (partCount == 0) {
      return EMPTY;
    }

    ConvexPart[] parts = new ConvexPart[partCount];
    int offset = 0;
    for (Poly polygon : polygons) {
      System.arraycopy(polygon.parts, 0, parts, offset, polygon.parts.length);
      offset += polygon.parts.length;
    }
    return new Poly(parts);
  }

  /**
   * Creates a polygon from a non-degenerate rectangle.
   *
   * @param rectangles source rectangles
   * @return immutable polygon
   * @throws IllegalArgumentException if the rectangle has non-finite coordinates or non-positive dimensions
   */
  public static Poly of(Rectangle... rectangles) {
    List<ConvexPart> parts = new ArrayList<>();

    for  (Rectangle rectangle : rectangles) {
      ConvexPart part = ConvexPart.of(new float[] {
          rectangle.minX(), rectangle.minY(),
          rectangle.maxX(), rectangle.minY(),
          rectangle.maxX(), rectangle.maxY(),
          rectangle.minX(), rectangle.maxY()
      });
      parts.add(part);
    }

    return new Poly(parts.toArray(new ConvexPart[0]));
  }

  /**
   * Normalizes and validates the vertices of a polygon before construction.
   *
   * @param vertices candidate polygon vertices
   * @return counter-clockwise coordinates with redundant vertices removed
   * @throws IllegalArgumentException if the vertices are insufficient, non-finite, or degenerate
   */
  private static float[] sanitize(Vector2[] vertices) {
    if (vertices.length < MINIMUM_VERTEX_COUNT) {
      throw new IllegalArgumentException("A polygon requires at least three vertices, got " + vertices.length);
    }

    float[] coordinates = new float[vertices.length * COORDINATE_STRIDE];
    int size = 0;
    for (Vector2 vertex : vertices) {
      float x = vertex.x();
      float y = vertex.y();
      if (!Float.isFinite(x) || !Float.isFinite(y)) {
        throw new IllegalArgumentException("Polygon vertices must be finite: " + vertex);
      }
      if (size >= COORDINATE_STRIDE
          && samePoint(coordinates[size - 2], coordinates[size - 1], x, y)) {
        continue;
      }
      coordinates[size++] = x;
      coordinates[size++] = y;
    }

    if (size >= COORDINATE_STRIDE * 2
        && samePoint(coordinates[0], coordinates[1], coordinates[size - 2], coordinates[size - 1])) {
      size -= COORDINATE_STRIDE;
    }
    coordinates = Arrays.copyOf(coordinates, size);
    coordinates = removeCollinearVertices(coordinates);
    if (coordinates.length < MINIMUM_VERTEX_COUNT * COORDINATE_STRIDE) {
      throw new IllegalArgumentException("A polygon requires at least three distinct non-collinear vertices");
    }

    float signedArea = signedArea(coordinates);
    if (Math.abs(signedArea) <= GEOMETRY_EPSILON) {
      throw new IllegalArgumentException("Polygon area must be greater than zero");
    }
    if (signedArea < 0.0F) {
      reverse(coordinates);
    }
    return coordinates;
  }

  /**
   * Removes vertices that are collinear with their immediate neighbors.
   *
   * @param coordinates polygon coordinates
   * @return coordinates without removable collinear vertices
   */
  private static float[] removeCollinearVertices(float[] coordinates) {
    float[] result = coordinates;
    boolean changed;
    do {
      int count = result.length / COORDINATE_STRIDE;
      if (count <= MINIMUM_VERTEX_COUNT) {
        return result;
      }

      boolean[] remove = new boolean[count];
      int removeCount = 0;
      for (int i = 0; i < count; i++) {
        int previous = (i + count - 1) % count;
        int next = (i + 1) % count;
        if (Math.abs(cross(result, previous, i, next)) <= GEOMETRY_EPSILON) {
          remove[i] = true;
          removeCount++;
        }
      }

      changed = removeCount > 0 && count - removeCount >= MINIMUM_VERTEX_COUNT;
      if (changed) {
        float[] compacted = new float[(count - removeCount) * COORDINATE_STRIDE];
        int output = 0;
        for (int i = 0; i < count; i++) {
          if (!remove[i]) {
            compacted[output++] = result[i * 2];
            compacted[output++] = result[i * 2 + 1];
          }
        }
        result = compacted;
      }
    } while (changed);
    return result;
  }

  /**
   * Validates that a polygon vertex loop has no self-intersections.
   *
   * @param coordinates polygon coordinates
   * @throws IllegalArgumentException if non-adjacent polygon edges intersect
   */
  private static void validateSimple(float[] coordinates) {
    int count = coordinates.length / COORDINATE_STRIDE;
    for (int first = 0; first < count; first++) {
      int firstNext = (first + 1) % count;
      for (int second = first + 1; second < count; second++) {
        int secondNext = (second + 1) % count;
        if (first == second || firstNext == second || secondNext == first) {
          continue;
        }
        if (segmentsIntersect(coordinates, first, firstNext, second, secondNext)) {
          throw new IllegalArgumentException("Polygon vertex loop self-intersects between edges "
              + first + " and " + second);
        }
      }
    }
  }

  /**
   * Returns whether two polygon edges intersect, including endpoint contact.
   *
   * @param coordinates polygon coordinates
   * @param a           first endpoint of the first edge
   * @param b           second endpoint of the first edge
   * @param c           first endpoint of the second edge
   * @param d           second endpoint of the second edge
   * @return whether the edges intersect
   */
  private static boolean segmentsIntersect(float[] coordinates, int a, int b, int c, int d) {
    float abC = cross(coordinates, a, b, c);
    float abD = cross(coordinates, a, b, d);
    float cdA = cross(coordinates, c, d, a);
    float cdB = cross(coordinates, c, d, b);
    if (((abC > GEOMETRY_EPSILON && abD < -GEOMETRY_EPSILON)
        || (abC < -GEOMETRY_EPSILON && abD > GEOMETRY_EPSILON))
        && ((cdA > GEOMETRY_EPSILON && cdB < -GEOMETRY_EPSILON)
        || (cdA < -GEOMETRY_EPSILON && cdB > GEOMETRY_EPSILON))) {
      return true;
    }
    return Math.abs(abC) <= GEOMETRY_EPSILON && onSegment(coordinates, a, b, c)
        || Math.abs(abD) <= GEOMETRY_EPSILON && onSegment(coordinates, a, b, d)
        || Math.abs(cdA) <= GEOMETRY_EPSILON && onSegment(coordinates, c, d, a)
        || Math.abs(cdB) <= GEOMETRY_EPSILON && onSegment(coordinates, c, d, b);
  }

  private static boolean onSegment(float[] coordinates, int a, int b, int point) {
    float px = coordinates[point * 2];
    float py = coordinates[point * 2 + 1];
    return px >= Math.min(coordinates[a * 2], coordinates[b * 2]) - GEOMETRY_EPSILON
        && px <= Math.max(coordinates[a * 2], coordinates[b * 2]) + GEOMETRY_EPSILON
        && py >= Math.min(coordinates[a * 2 + 1], coordinates[b * 2 + 1]) - GEOMETRY_EPSILON
        && py <= Math.max(coordinates[a * 2 + 1], coordinates[b * 2 + 1]) + GEOMETRY_EPSILON;
  }

  private static boolean isConvex(float[] coordinates) {
    int count = coordinates.length / COORDINATE_STRIDE;
    for (int i = 0; i < count; i++) {
      if (cross(coordinates, i, (i + 1) % count, (i + 2) % count) <= GEOMETRY_EPSILON) {
        return false;
      }
    }
    return true;
  }

  /**
   * Decomposes a counter-clockwise concave polygon into convex triangles.
   *
   * @param coordinates counter-clockwise polygon coordinates
   * @return convex triangular components
   * @throws IllegalArgumentException if no valid decomposition can be found
   */
  private static ConvexPart[] decompose(float[] coordinates) {
    int vertexCount = coordinates.length / COORDINATE_STRIDE;
    int[] indices = new int[vertexCount];
    for (int i = 0; i < vertexCount; i++) {
      indices[i] = i;
    }

    ConvexPart[] result = new ConvexPart[vertexCount - 2];
    int remaining = vertexCount;
    int output = 0;
    while (remaining > MINIMUM_VERTEX_COUNT) {
      boolean earFound = false;
      for (int i = 0; i < remaining; i++) {
        int previous = indices[(i + remaining - 1) % remaining];
        int current = indices[i];
        int next = indices[(i + 1) % remaining];
        if (cross(coordinates, previous, current, next) <= GEOMETRY_EPSILON
            || containsOtherVertex(coordinates, indices, remaining, previous, current, next)) {
          continue;
        }

        result[output++] = ConvexPart.triangle(coordinates, previous, current, next);
        System.arraycopy(indices, i + 1, indices, i, remaining - i - 1);
        remaining--;
        earFound = true;
        break;
      }
      if (!earFound) {
        throw new IllegalArgumentException("Unable to decompose polygon; check for duplicate or invalid vertices");
      }
    }
    result[output] = ConvexPart.triangle(coordinates, indices[0], indices[1], indices[2]);
    return result;
  }

  /**
   * Returns whether a triangle contains any remaining polygon vertex.
   *
   * @param coordinates polygon coordinates
   * @param indices     remaining polygon vertex indices
   * @param count       number of remaining indices
   * @param a           first triangle vertex index
   * @param b           second triangle vertex index
   * @param c           third triangle vertex index
   * @return whether another vertex lies in the triangle
   */
  private static boolean containsOtherVertex(float[] coordinates, int[] indices, int count,
                                             int a, int b, int c) {
    for (int i = 0; i < count; i++) {
      int point = indices[i];
      if (point != a && point != b && point != c && pointInTriangle(coordinates, point, a, b, c)) {
        return true;
      }
    }
    return false;
  }

  private static boolean pointInTriangle(float[] coordinates, int point, int a, int b, int c) {
    return cross(coordinates, a, b, point) >= -GEOMETRY_EPSILON
        && cross(coordinates, b, c, point) >= -GEOMETRY_EPSILON
        && cross(coordinates, c, a, point) >= -GEOMETRY_EPSILON;
  }

  private static float cross(float[] coordinates, int a, int b, int c) {
    float abX = coordinates[b * 2] - coordinates[a * 2];
    float abY = coordinates[b * 2 + 1] - coordinates[a * 2 + 1];
    float acX = coordinates[c * 2] - coordinates[a * 2];
    float acY = coordinates[c * 2 + 1] - coordinates[a * 2 + 1];
    return abX * acY - abY * acX;
  }

  private static float signedArea(float[] coordinates) {
    float twiceArea = 0.0F;
    int count = coordinates.length / COORDINATE_STRIDE;
    for (int i = 0; i < count; i++) {
      int next = (i + 1) % count;
      twiceArea += coordinates[i * 2] * coordinates[next * 2 + 1]
          - coordinates[next * 2] * coordinates[i * 2 + 1];
    }
    return twiceArea * 0.5F;
  }

  private static void reverse(float[] coordinates) {
    int count = coordinates.length / COORDINATE_STRIDE;
    for (int left = 0, right = count - 1; left < right; left++, right--) {
      float x = coordinates[left * 2];
      float y = coordinates[left * 2 + 1];
      coordinates[left * 2] = coordinates[right * 2];
      coordinates[left * 2 + 1] = coordinates[right * 2 + 1];
      coordinates[right * 2] = x;
      coordinates[right * 2 + 1] = y;
    }
  }

  private static boolean samePoint(float firstX, float firstY, float secondX, float secondY) {
    return firstX == secondX && firstY == secondY;
  }

  /**
   * Returns the number of convex polygons composing this shape.
   *
   * @return convex component count
   */
  public int convexCount() {
    return parts.length;
  }

  /**
   * Returns whether this shape has no convex components.
   *
   * @return whether this polygon is empty
   */
  public boolean isEmpty() {
    return parts.length == 0;
  }

  /**
   * Returns the number of vertices in a convex component.
   *
   * @param convexIndex convex component index
   * @return vertex count
   * @throws IndexOutOfBoundsException if {@code convexIndex} is outside the component range
   */
  public int vertexCount(int convexIndex) {
    return part(convexIndex).vertexCount();
  }

  /**
   * Returns a vertex in a convex component.
   *
   * @param convexIndex convex component index
   * @param vertexIndex vertex index
   * @return vertex value
   * @throws IndexOutOfBoundsException if either index is outside its corresponding range
   */
  public Vector2 vertex(int convexIndex, int vertexIndex) {
    ConvexPart part = part(convexIndex);
    int coordinateIndex = part.coordinateIndex(vertexIndex);
    return new Vector2(part.coordinates[coordinateIndex], part.coordinates[coordinateIndex + 1]);
  }

  /**
   * Returns a vertex x coordinate without allocating a vector.
   *
   * @param convexIndex convex component index
   * @param vertexIndex vertex index
   * @return vertex x coordinate
   * @throws IndexOutOfBoundsException if either index is outside its corresponding range
   */
  public float vertexX(int convexIndex, int vertexIndex) {
    ConvexPart part = part(convexIndex);
    return part.coordinates[part.coordinateIndex(vertexIndex)];
  }

  /**
   * Returns a vertex y coordinate without allocating a vector.
   *
   * @param convexIndex convex component index
   * @param vertexIndex vertex index
   * @return vertex y coordinate
   * @throws IndexOutOfBoundsException if either index is outside its corresponding range
   */
  public float vertexY(int convexIndex, int vertexIndex) {
    ConvexPart part = part(convexIndex);
    return part.coordinates[part.coordinateIndex(vertexIndex) + 1];
  }

  /**
   * Returns the enclosing axis-aligned bounds.
   *
   * @return immutable bounds
   */
  public Rectangle bounds() {
    return bounds;
  }

  @Override
  public float area() {
    return area;
  }

  @Override
  public float centralX() {
    return bounds.centralX();
  }

  @Override
  public float centralY() {
    return bounds.centralY();
  }

  @Override
  public Vector2 center() {
    return bounds.center();
  }

  @Override
  public boolean contains(float x, float y) {
    if (!bounds.contains(x, y)) {
      return false;
    }
    for (ConvexPart part : parts) {
      if (part.contains(x, y)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a new polygon scaled by the given factors.
   *
   * @param sx x scale factor
   * @param sy y scale factor
   * @return scaled polygon
   * @throws IllegalArgumentException if either scale factor is non-finite or zero
   */
  @Override
  public Poly scale(float sx, float sy) {
    if (isEmpty()) {
      return this;
    }

    ConvexPart[] scaled = new ConvexPart[parts.length];
    for (int i = 0; i < parts.length; i++) {
      scaled[i] = parts[i].scale(sx, sy);
    }
    return new Poly(scaled);
  }

  /**
   * Returns a new polygon translated by the given amounts.
   *
   * @param tx x translation
   * @param ty y translation
   * @return translated polygon
   * @throws IllegalArgumentException if either translation is non-finite
   */
  @Override
  public Poly translate(float tx, float ty) {
    if (isEmpty()) {
      return this;
    }

    ConvexPart[] translated = new ConvexPart[parts.length];
    for (int i = 0; i < parts.length; i++) {
      translated[i] = parts[i].translate(tx, ty);
    }
    return new Poly(translated);
  }

  @Override
  public float minX() {
    return bounds.minX();
  }

  @Override
  public float minY() {
    return bounds.minY();
  }

  @Override
  public float maxX() {
    return bounds.maxX();
  }

  @Override
  public float maxY() {
    return bounds.maxY();
  }

  private ConvexPart part(int convexIndex) {
    if (convexIndex < 0 || convexIndex >= parts.length) {
      throw new IndexOutOfBoundsException("Convex component index out of range: " + convexIndex);
    }
    return parts[convexIndex];
  }

  private static final class ConvexPart {
    private final float[] coordinates;
    private final float minX;
    private final float minY;
    private final float maxX;
    private final float maxY;
    private final float area;

    private ConvexPart(float[] coordinates) {
      this.coordinates = coordinates;

      float minX = coordinates[0];
      float minY = coordinates[1];
      float maxX = minX;
      float maxY = minY;
      for (int i = COORDINATE_STRIDE; i < coordinates.length; i += COORDINATE_STRIDE) {
        minX = Math.min(minX, coordinates[i]);
        minY = Math.min(minY, coordinates[i + 1]);
        maxX = Math.max(maxX, coordinates[i]);
        maxY = Math.max(maxY, coordinates[i + 1]);
      }
      this.minX = minX;
      this.minY = minY;
      this.maxX = maxX;
      this.maxY = maxY;
      area = signedArea(coordinates);
    }

    private static ConvexPart of(float[] coordinates) {
      return new ConvexPart(coordinates.clone());
    }

    private static ConvexPart triangle(float[] source, int a, int b, int c) {
      return new ConvexPart(new float[] {
          source[a * 2], source[a * 2 + 1],
          source[b * 2], source[b * 2 + 1],
          source[c * 2], source[c * 2 + 1]
      });
    }

    private int vertexCount() {
      return coordinates.length / COORDINATE_STRIDE;
    }

    private int coordinateIndex(int vertexIndex) {
      int count = vertexCount();
      if (vertexIndex < 0 || vertexIndex >= count) {
        throw new IndexOutOfBoundsException("Vertex index out of range: " + vertexIndex);
      }
      return vertexIndex * COORDINATE_STRIDE;
    }

    private boolean contains(float x, float y) {
      int count = vertexCount();
      for (int i = 0; i < count; i++) {
        int next = (i + 1) % count;
        float edgeX = coordinates[next * 2] - coordinates[i * 2];
        float edgeY = coordinates[next * 2 + 1] - coordinates[i * 2 + 1];
        float pointX = x - coordinates[i * 2];
        float pointY = y - coordinates[i * 2 + 1];
        if (edgeX * pointY - edgeY * pointX < -GEOMETRY_EPSILON) {
          return false;
        }
      }
      return true;
    }

    private ConvexPart scale(float sx, float sy) {
      float[] scaled = new float[coordinates.length];
      for (int i = 0; i < coordinates.length; i += COORDINATE_STRIDE) {
        scaled[i] = coordinates[i] * sx;
        scaled[i + 1] = coordinates[i + 1] * sy;
      }
      if (sx * sy < 0.0F) {
        reverse(scaled);
      }
      return new ConvexPart(scaled);
    }

    private ConvexPart translate(float tx, float ty) {
      float[] translated = new float[coordinates.length];
      for (int i = 0; i < coordinates.length; i += COORDINATE_STRIDE) {
        translated[i] = coordinates[i] + tx;
        translated[i + 1] = coordinates[i + 1] + ty;
      }
      return new ConvexPart(translated);
    }
  }
}
