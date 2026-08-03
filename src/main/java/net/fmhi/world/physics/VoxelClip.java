package net.fmhi.world.physics;

import net.fmhi.math.Box2D;

/**
 * Collision shape of a block tile, ported from Enchant's {@code VoxelClip}.
 *
 * <p>Movement is clipped per axis: {@link #clipX} shortens an X movement
 * so the body stops exactly at the shape's edge, {@link #clipY} does the
 * same for Y. No SAT, no separation loop — each axis is resolved
 * independently.
 */
public interface VoxelClip {

  /** No collision at all (air). */
  VoxelClip EMPTY = new VoxelClip() {
    @Override
    public float clipX(float dx, Box2D aabb, float ox, float oy) {
      return dx;
    }

    @Override
    public float clipY(float dy, Box2D aabb, float ox, float oy) {
      return dy;
    }

    @Override
    public boolean interacts(Box2D aabb, float ox, float oy) {
      return false;
    }

    @Override
    public float topAt(float x0, float x1, float ox, float oy) {
      return Float.NaN;
    }
  };

  /** A full tile. */
  VoxelClip CUBE = new VoxelBox(0F, 0F, 1F, 1F);

  /** The four slope directions (Y-up: the tile spans [0,1]² from the
   * bottom; "DOWN" surfaces are floors, "UP" surfaces are ceilings). */
  enum SlopeType {
    /** Floor slope rising to the right: low left, high right (↗). */
    LEFT_DOWN,
    /** Floor slope rising to the left: high left, low right (↖). */
    RIGHT_DOWN,
    /** Ceiling slope hanging lower on the left (↗ ceiling). */
    LEFT_UP,
    /** Ceiling slope hanging lower on the right (↖ ceiling). */
    RIGHT_UP
  }

  /**
   * Builds a slope approximated by {@code steps} stacked boxes, each
   * {@code 1/steps} wide. A floor slope stacks the boxes on the tile
   * bottom; a ceiling slope hangs them from the tile top.
   *
   * @param type  the slope direction
   * @param steps the number of staircase steps (>= 1)
   */
  static VoxelClip generateSlope(SlopeType type, int steps) {
    if (steps < 1) throw new IllegalArgumentException("steps must be >= 1: " + steps);
    float w = 1F / steps;
    VoxelBox[] boxes = new VoxelBox[steps];
    for (int i = 0; i < steps; i++) {
      float h = (i + 1) * w;
      switch (type) {
        case LEFT_DOWN -> boxes[i] = new VoxelBox(i * w, 0F, w, h);
        case RIGHT_DOWN -> boxes[i] = new VoxelBox(1F - (i + 1) * w, 0F, w, h);
        case LEFT_UP -> boxes[i] = new VoxelBox(i * w, 1F - h, w, h);
        case RIGHT_UP -> boxes[i] = new VoxelBox(1F - (i + 1) * w, 1F - h, w, h);
      }
    }
    return VoxelOutline.of(boxes);
  }

  /** 4-step floor slope rising to the right (↗): low left, high right. */
  VoxelClip SLOPE_LEFT_DOWN = generateSlope(SlopeType.LEFT_DOWN, 8);
  /** 4-step floor slope rising to the left (↖): high left, low right. */
  VoxelClip SLOPE_RIGHT_DOWN = generateSlope(SlopeType.RIGHT_DOWN, 8);
  /** 4-step ceiling slope hanging lower on the left. */
  VoxelClip SLOPE_LEFT_UP = generateSlope(SlopeType.LEFT_UP, 8);
  /** 4-step ceiling slope hanging lower on the right. */
  VoxelClip SLOPE_RIGHT_UP = generateSlope(SlopeType.RIGHT_UP, 8);

  /**
   * Shortens an X movement {@code dx} so the body does not enter this
   * shape. The shape occupies {@code [ox, ox+1] × [oy, oy+1]} with local
   * coordinates inside the tile.
   *
   * @param dx   the requested X movement
   * @param aabb the body bounds (world coordinates)
   * @param ox   the tile's world X
   * @param oy   the tile's world Y
   * @return the clipped movement
   */
  float clipX(float dx, Box2D aabb, float ox, float oy);

  /** Shortens a Y movement {@code dy} so the body does not enter this
   * shape (Y-down world: positive {@code dy} is downward). */
  float clipY(float dy, Box2D aabb, float ox, float oy);

  /** Whether the shape overlaps the given body bounds. */
  boolean interacts(Box2D aabb, float ox, float oy);

  /**
   * The top of the surface a body standing within {@code [x0, x1]} would
   * rest on (the lowest box top overlapping that X range), in world
   * coordinates; {@link Float#NaN} if the shape offers no surface there.
   * Used by the step-up logic to measure step height.
   */
  float topAt(float x0, float x1, float ox, float oy);

  /** The surface height at a single world X (the step ahead), or
   * {@link Float#NaN} if the shape offers no surface there. */
  default float surfaceAt(float x, float ox, float oy) {
    return topAt(x, x, ox, oy);
  }
}
