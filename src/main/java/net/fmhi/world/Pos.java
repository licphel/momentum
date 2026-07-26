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

package net.fmhi.world;

import net.fmhi.math.Vector2;
import net.fmhi.math.dim2.Direction2D;

/**
 * Immutable double-precision world position.
 *
 * <p>Stored as {@code double} for precision at world scale; float accessors
 * {@link #xf()} / {@link #yf()} are provided for rendering and GPU upload.
 *
 * @param x the X coordinate
 * @param y the Y coordinate
 * @see BlockPos
 * @see ChunkPos
 */
public record Pos(double x, double y) {
  /** Origin. */
  public static final Pos ZERO = new Pos(0.0, 0.0);

  /**
   * Returns the Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the distance
   */
  public static double distance(Pos a, Pos b) {
    return a.subtract(b).length();
  }

  /**
   * Returns the squared Euclidean distance between two positions.
   *
   * @param a first position
   * @param b second position
   * @return the squared distance
   */
  public static double distanceSquared(Pos a, Pos b) {
    return a.subtract(b).lengthSquared();
  }

  /**
   * Linearly interpolates between two positions.
   *
   * @param a the start position
   * @param b the end position
   * @param t the interpolation factor ({@code 0.0} = a, {@code 1.0} = b)
   * @return the interpolated position
   */
  public static Pos lerp(Pos a, Pos b, double t) {
    double it = 1.0 - t;
    return new Pos(a.x * it + b.x * t, a.y * it + b.y * t);
  }

  /**
   * Returns the X coordinate as a float.
   *
   * @return {@code (float) x}
   */
  public float xf() {
    return (float) x;
  }

  /**
   * Returns the Y coordinate as a float.
   *
   * @return {@code (float) y}
   */
  public float yf() {
    return (float) y;
  }

  /**
   * Returns the sum of this position and another.
   *
   * @param o the other position
   * @return {@code this + o}
   */
  public Pos add(Pos o) {
    return new Pos(x + o.x, y + o.y);
  }

  /**
   * Returns the sum of this position and the given offsets.
   *
   * @param dx the X offset
   * @param dy the Y offset
   * @return {@code this + (dx, dy)}
   */
  public Pos add(double dx, double dy) {
    return new Pos(x + dx, y + dy);
  }

  /**
   * Returns the position offset by one block in the given direction.
   *
   * @param dir the direction to offset
   * @return {@code (x + dir.dx, y + dir.dy)}
   */
  public Pos offset(Direction2D dir) {
    return new Pos(x + dir.offset[0], y + dir.offset[1]);
  }

  /**
   * Returns the position offset by {@code n} blocks in the given
   * direction.
   *
   * @param dir the direction to offset
   * @param n   the distance in blocks
   * @return {@code (x + dir.dx * n, y + dir.dy * n)}
   */
  public Pos offset(Direction2D dir, double n) {
    return new Pos(x + dir.offset[0] * n, y + dir.offset[1] * n);
  }

  /**
   * Returns the difference of this position and another.
   *
   * @param o the other position
   * @return {@code this - o}
   */
  public Pos subtract(Pos o) {
    return new Pos(x - o.x, y - o.y);
  }

  /**
   * Returns this position scaled by a scalar.
   *
   * @param s the scaling factor
   * @return {@code this * s}
   */
  public Pos multiply(double s) {
    return new Pos(x * s, y * s);
  }

  /**
   * Returns this position divided by a scalar.
   *
   * @param s the divisor; must be non-zero
   * @return {@code this / s}
   */
  public Pos divide(double s) {
    return new Pos(x / s, y / s);
  }

  /**
   * Returns the squared Euclidean distance to another position.
   *
   * @param o the other position
   * @return {@code dx² + dy²}
   */
  public double distanceSquared(Pos o) {
    double dx = x - o.x;
    double dy = y - o.y;
    return dx * dx + dy * dy;
  }

  /**
   * Returns the Euclidean distance to another position.
   *
   * @param o the other position
   * @return the distance
   */
  public double distance(Pos o) {
    return Math.sqrt(distanceSquared(o));
  }

  /**
   * Returns the length (magnitude) of this position vector from the origin.
   *
   * @return {@code sqrt(x² + y²)}
   */
  public double length() {
    return Math.sqrt(lengthSquared());
  }

  /**
   * Returns the squared length of this position vector.
   *
   * @return {@code x² + y²}
   */
  public double lengthSquared() {
    return x * x + y * y;
  }

  /**
   * Converts to the nearest integer block position (floor of each component).
   *
   * @return the block position containing this world position
   */
  public BlockPos toBlockPos() {
    return new BlockPos((int) Math.floor(x), (int) Math.floor(y));
  }

  /**
   * Converts to a chunk position.
   *
   * @return the chunk containing this world position
   */
  public ChunkPos toChunkPos() {
    return toBlockPos().toChunkPos();
  }

  /**
   * Converts to a chunk position for the given chunk size.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return the chunk containing this world position
   */
  public ChunkPos toChunkPos(int chunkSize) {
    return toBlockPos().toChunkPos(chunkSize);
  }

  /**
   * Converts to a {@link Vector2} for use with the rendering system.
   *
   * @return a float vector
   */
  public Vector2 toVector2() {
    return new Vector2(xf(), yf());
  }
}
