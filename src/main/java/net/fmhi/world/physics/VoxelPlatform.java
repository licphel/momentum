package net.fmhi.world.physics;

import net.fmhi.math.Box2D;

/**
 * One-way platform: blocks only a body falling onto its top edge from
 * above; horizontal movement and upward jumps pass through freely.
 */
public final class VoxelPlatform implements VoxelClip {

  @Override
  public float clipX(float dx, Box2D aabb, float ox, float oy) {
    return dx;
  }

  @Override
  public float clipY(float dy, Box2D aabb, float ox, float oy) {
    float top = oy + 1F;
    // only falling (Y-up: dy < 0) and only from above the platform
    if (dy >= 0F || aabb.minY() < top - 0.001F) return dy;
    if (aabb.minY() + dy <= top) dy = Math.min(0F, top - aabb.minY());
    return dy;
  }

  @Override
  public boolean interacts(Box2D aabb, float ox, float oy) {
    return aabb.minY() <= oy + 1F && aabb.maxY() >= oy;
  }

  @Override
  public float topAt(float x0, float x1, float ox, float oy) {
    return oy + 1F;
  }
}
