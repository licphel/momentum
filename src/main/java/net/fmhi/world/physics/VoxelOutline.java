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

  /** The component boxes (copy; for e.g. liquid rendering into the gaps
   * between the boxes). */
  public VoxelBox[] boxes() {
    return boxes.clone();
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
    // the surface under the body's footprint: the highest box top
    // overlapping the X range — stepping down a staircase must rest on
    // the highest step below, never embed into a higher one
    float top = Float.NaN;
    for (VoxelBox box : boxes) {
      float t = box.topAt(x0, x1, ox, oy);
      if (Float.isNaN(t)) continue;
      top = Float.isNaN(top) ? t : Math.max(top, t);
    }
    return top;
  }

  @Override
  public float surfaceAt(float x, float ox, float oy) {
    // the box covering the point (inclusive edges, so a step boundary
    // resolves to the step behind it)
    float tx = Math.clamp(x - ox, 0F, 1F);
    for (VoxelBox box : boxes) {
      float t = box.surfaceAt(ox + tx, ox, oy);
      if (!Float.isNaN(t)) return t;
    }
    return Float.NaN;
  }
}
