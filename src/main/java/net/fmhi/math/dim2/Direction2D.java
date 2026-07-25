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

package net.fmhi.math.dim2;

import net.fmhi.math.Vector2;

/**
 * Enumerated 2D cardinal and diagonal directions.
 *
 * <p>Eight singleton directions are provided (N, NE, E, SE, S, SW, W, NW).
 * Each carries an integer offset, a float angle in degrees, and an index.
 */
public enum Direction2D {
  /**
   * North — offset (0, -1), 90°.
   */
  NORTH(0, -1, 90.0F, 0),
  /**
   * North-east — offset (1, -1), 45°.
   */
  NORTH_EAST(1, -1, 45.0F, 1),
  /**
   * East — offset (1, 0), 0°.
   */
  EAST(1, 0, 0.0F, 2),
  /**
   * South-east — offset (1, 1), -45°.
   */
  SOUTH_EAST(1, 1, -45.0F, 3),
  /**
   * South — offset (0, 1), -90°.
   */
  SOUTH(0, 1, -90.0F, 4),
  /**
   * South-west — offset (-1, 1), -135°.
   */
  SOUTH_WEST(-1, 1, -135.0F, 5),
  /**
   * West — offset (-1, 0), 180°.
   */
  WEST(-1, 0, 180.0F, 6),
  /**
   * North-west — offset (-1, -1), 135°.
   */
  NORTH_WEST(-1, -1, 135.0F, 7);

  /**
   * Four cardinal directions: N, E, S, W.
   */
  public static final Direction2D[] CARDINAL = {NORTH, EAST, SOUTH, WEST};
  /**
   * Four diagonal directions: NE, SE, SW, NW.
   */
  public static final Direction2D[] DIAGONAL = {NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST};
  /**
   * All eight directions: N, NE, E, SE, S, SW, W, NW.
   */
  public static final Direction2D[] ALL = values();
  /**
   * Integer grid offset for this direction.
   */
  public final int[] offset;
  /**
   * Angle in degrees (East = 0°, counter-clockwise positive).
   */
  public final float angle;
  /**
   * Ordinal index (0–7, NORTH=0 through NORTH_WEST=7).
   */
  public final int index;

  Direction2D(int dx, int dy, float angle, int index) {
    offset = new int[] {dx, dy};
    this.angle = angle;
    this.index = index;
  }

  /**
   * Returns the direction with the given index.
   *
   * @param index index from 0 to 7
   * @return the corresponding direction
   * @throws IndexOutOfBoundsException if index is out of range
   */
  public static Direction2D fromIndex(int index) {
    return ALL[index];
  }

  /**
   * Returns the nearest direction to the given angle (in degrees).
   *
   * @param angle angle in degrees
   * @return nearest direction
   */
  public static Direction2D getNearest(float angle) {
    Direction2D nearest = NORTH;
    float minDelta = Float.MAX_VALUE;
    for (Direction2D d : ALL) {
      float delta = Math.abs(angle - d.angle);
      if (delta < minDelta) {
        minDelta = delta;
        nearest = d;
      }
    }
    return nearest;
  }

  /**
   * Returns the direction from a normalized offset vector.
   *
   * @param dx x offset (-1, 0, or 1)
   * @param dy y offset (-1, 0, or 1)
   * @return the corresponding direction, or null if not a valid direction
   */
  public static Direction2D fromOffset(int dx, int dy) {
    return switch (dx) {
      case 0 -> switch (dy) {
        case -1 -> NORTH;
        case 1 -> SOUTH;
        default -> throw new IllegalArgumentException("Invalid dy: " + dy);
      };
      case 1 -> switch (dy) {
        case -1 -> NORTH_EAST;
        case 0 -> EAST;
        case 1 -> SOUTH_EAST;
        default -> throw new IllegalArgumentException("Invalid dy: " + dy);
      };
      case -1 -> switch (dy) {
        case -1 -> NORTH_WEST;
        case 0 -> WEST;
        case 1 -> SOUTH_WEST;
        default -> throw new IllegalArgumentException("Invalid dy: " + dy);
      };
      default -> throw new IllegalArgumentException("Invalid dx: " + dx);
    };
  }

  /**
   * Returns the next direction clockwise by 90°.
   *
   * @return clockwise 90° neighbor (cardinal only)
   */
  public Direction2D cw90() {
    return ALL[(index + 2) % 8];
  }

  /**
   * Returns the next direction counter-clockwise by 90°.
   *
   * @return counter-clockwise 90° neighbor (cardinal only)
   */
  public Direction2D ccw90() {
    return ALL[(index + 6) % 8];
  }

  /**
   * Returns the next direction clockwise by 45°.
   *
   * @return clockwise 45° neighbour
   */
  public Direction2D cw45() {
    return ALL[(index + 1) % 8];
  }

  /**
   * Returns the next direction counter-clockwise by 45°.
   *
   * @return counter-clockwise 45° neighbour
   */
  public Direction2D ccw45() {
    return ALL[(index + 7) % 8];
  }

  /**
   * Returns the opposite direction.
   *
   * @return opposite direction
   */
  public Direction2D opposite() {
    return ALL[(index + 4) % 8];
  }

  /**
   * Returns whether this is a cardinal direction (N, E, S, W).
   *
   * @return true if cardinal, false if diagonal
   */
  public boolean isCardinal() {
    return (index & 1) == 0;
  }

  /**
   * Returns whether this is a diagonal direction (NE, SE, SW, NW).
   *
   * @return true if diagonal, false if cardinal
   */
  public boolean isDiagonal() {
    return (index & 1) == 1;
  }

  /**
   * Returns a new {@link Vector2} offset by this direction.
   *
   * @param pos origin position
   * @return shifted position
   */
  public Vector2 add(Vector2 pos) {
    return new Vector2(pos.x() + offset[0], pos.y() + offset[1]);
  }

  /**
   * Returns this direction as a normalized {@link Vector2}.
   *
   * @return unit vector in this direction
   */
  public Vector2 toVector() {
    return new Vector2(offset[0], offset[1]);
  }
}