package net.fmhi.world.physics;

import net.fmhi.math.Box2D;

/**
 * An axis-aligned box collision shape with local coordinates inside a tile
 * (Y-up: {@code (X, Y)} is the bottom-left corner, the box spans
 * {@code [X, X+W] × [Y, Y+H]}).
 */
public final class VoxelBox implements VoxelClip {

  private final float x;
  private final float y;
  private final float w;
  private final float h;

  public VoxelBox(float x, float y, float w, float h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
  }

  public float x() { return x; }
  public float y() { return y; }
  public float w() { return w; }
  public float h() { return h; }

  @Override
  public float clipX(float dx, Box2D aabb, float ox, float oy) {
    float ax = ox + x;
    float ay = oy + y;
    // the body is fully below or fully above the box: no X collision
    if (aabb.maxY() <= ay || aabb.minY() >= ay + h) return dx;
    if (dx > 0F && aabb.maxX() <= ax) dx = Math.min(dx, ax - aabb.maxX());
    if (dx < 0F && aabb.minX() >= ax + w) dx = Math.max(dx, ax + w - aabb.minX());
    return dx;
  }

  @Override
  public float clipY(float dy, Box2D aabb, float ox, float oy) {
    float ax = ox + x;
    float ay = oy + y;
    // the body is fully left or fully right of the box: no Y collision
    if (aabb.maxX() <= ax || aabb.minX() >= ax + w) return dy;
    // Y-up: dy > 0 is upward (hit the box bottom), dy < 0 is downward
    // (land on the box top)
    if (dy > 0F && aabb.maxY() <= ay) dy = Math.min(dy, ay - aabb.maxY());
    if (dy < 0F && aabb.minY() >= ay + h) dy = Math.max(dy, ay + h - aabb.minY());
    return dy;
  }

  @Override
  public boolean interacts(Box2D aabb, float ox, float oy) {
    float ax = ox + x;
    float ay = oy + y;
    return aabb.maxX() > ax && aabb.minX() < ax + w
        && aabb.maxY() > ay && aabb.minY() < ay + h;
  }

  @Override
  public float topAt(float x0, float x1, float ox, float oy) {
    float ax = ox + x;
    // inclusive edges: a body exactly on a box edge still covers it, so
    // a staircase surface measured across the body does not jump a step
    if (x1 < ax || x0 > ax + w) return Float.NaN;
    return oy + y + h;
  }

  @Override
  public float surfaceAt(float x, float ox, float oy) {
    // inclusive edges, so a point exactly on a box edge is covered
    float tx = x - ox;
    if (tx >= this.x && tx <= this.x + this.w) return oy + this.y + this.h;
    return Float.NaN;
  }
}
