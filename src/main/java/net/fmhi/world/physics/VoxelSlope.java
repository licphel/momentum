package net.fmhi.world.physics;

import net.fmhi.math.Box2D;

/**
 * A triangular slope (Terraria-style): the surface rises linearly across the
 * tile from {@code top0} at the left edge to {@code top1} at the right edge
 * (Y-up offsets from the tile bottom). A body near the surface has its feet
 * pulled onto it, so walking up or down the slope is smooth instead of
 * stepped. The surface height is sampled over the body's whole footprint
 * (its X range overlapping the tile), not a single center point.
 */
public final class VoxelSlope implements VoxelClip {

  /** Surface height at the left edge of the tile (offset from the bottom). */
  private final float top0;
  /** Surface height at the right edge of the tile (offset from the bottom). */
  private final float top1;

  /**
   * @param top0 surface offset at the left edge ({@code 0} = bottom)
   * @param top1 surface offset at the right edge ({@code 1} = top)
   */
  public VoxelSlope(float top0, float top1) {
    this.top0 = top0;
    this.top1 = top1;
  }

  /** The world Y of the surface at the given world X, or NaN outside the
   * tile. */
  private float surfaceAt(float x, float ox, float oy) {
    float tx = Math.clamp(x - ox, 0F, 1F);
    return oy + top0 + (top1 - top0) * tx;
  }

  /** The highest surface under the body's footprint (its X range
   * overlapping this tile), or NaN if it does not overlap. */
  public float surfaceMax(Box2D aabb, float ox, float oy) {
    float x0 = Math.max(aabb.minX(), ox);
    float x1 = Math.min(aabb.maxX(), ox + 1F);
    if (x1 <= x0) return Float.NaN;
    return Math.max(surfaceAt(x0, ox, oy), surfaceAt(x1, ox, oy));
  }

  @Override
  public float clipX(float dx, Box2D aabb, float ox, float oy) {
    float sy = surfaceMax(aabb, ox, oy);
    // the body may move freely when its feet are at or near the surface
    // (within this frame's horizontal step, Terraria's flag2); feet
    // clearly above the slope mean the body is hitting the vertical face
    if (Float.isNaN(sy) || aabb.minY() <= sy + Math.abs(dx) + 0.05F) return dx;
    if (dx > 0F && aabb.maxX() <= ox + 1F) dx = Math.min(dx, ox + 1F - aabb.maxX());
    if (dx < 0F && aabb.minX() >= ox) dx = Math.max(dx, ox - aabb.minX());
    return dx;
  }

  @Override
  public float clipY(float dy, Box2D aabb, float ox, float oy) {
    float sy = surfaceMax(aabb, ox, oy);
    if (Float.isNaN(sy)) return dy;
    float feet = aabb.minY();
    if (feet > sy) {
      // above the slope: falling lands on the surface
      if (dy < 0F) dy = Math.max(dy, sy - feet);
    } else if (sy - feet <= 1F) {
      // at or slightly below the surface: rest on it / pull the feet onto
      // it (Terraria SlopeCollision)
      dy = sy - feet;
    }
    return dy;
  }

  @Override
  public boolean interacts(Box2D aabb, float ox, float oy) {
    return aabb.maxX() > ox && aabb.minX() < ox + 1F
        && aabb.maxY() > oy && aabb.minY() < oy + 1F;
  }

  @Override
  public float topAt(float x0, float x1, float ox, float oy) {
    float sx0 = surfaceAt(x0, ox, oy);
    float sx1 = surfaceAt(x1, ox, oy);
    // the surface under the body is the higher of the two (Y-up)
    return Math.max(sx0, sx1);
  }
}
