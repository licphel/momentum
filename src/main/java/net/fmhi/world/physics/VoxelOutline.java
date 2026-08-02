package net.fmhi.world.physics;

import net.fmhi.math.Box2D;

import java.util.List;

/**
 * A collision shape made of several {@link VoxelBox}es, like Enchant's
 * {@code VoxelOutline}. Slopes and stairs are approximated with a few
 * boxes of increasing height; movement is clipped against every box and
 * the tightest result wins.
 */
public final class VoxelOutline implements VoxelClip {

  private final VoxelBox[] boxes;

  private VoxelOutline(VoxelBox[] boxes) {
    this.boxes = boxes;
  }

  /** Builds an outline from the given boxes. */
  public static VoxelOutline of(VoxelBox... boxes) {
    return new VoxelOutline(boxes.clone());
  }

  /** Builds an outline from a box list. */
  public static VoxelOutline of(List<VoxelBox> boxes) {
    return new VoxelOutline(boxes.toArray(new VoxelBox[0]));
  }

  @Override
  public float clipX(float dx, Box2D aabb, float ox, float oy) {
    for (VoxelBox box : boxes) dx = box.clipX(dx, aabb, ox, oy);
    return dx;
  }

  @Override
  public float clipY(float dy, Box2D aabb, float ox, float oy) {
    for (VoxelBox box : boxes) dy = box.clipY(dy, aabb, ox, oy);
    return dy;
  }

  @Override
  public boolean interacts(Box2D aabb, float ox, float oy) {
    for (VoxelBox box : boxes) {
      if (box.interacts(aabb, ox, oy)) return true;
    }
    return false;
  }

  @Override
  public float topAt(float x0, float x1, float ox, float oy) {
    float top = Float.NaN;
    for (VoxelBox box : boxes) {
      float t = box.topAt(x0, x1, ox, oy);
      if (Float.isNaN(t)) continue;
      top = Float.isNaN(top) ? t : Math.min(top, t);
    }
    return top;
  }
}
