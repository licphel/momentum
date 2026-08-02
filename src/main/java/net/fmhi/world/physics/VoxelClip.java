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
}
