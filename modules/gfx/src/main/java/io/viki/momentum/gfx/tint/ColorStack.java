package io.viki.momentum.gfx.tint;

import java.util.Arrays;

/**
 * Nested multiplicative RGBA modifiers for immediate vertex recording.
 * Not thread-safe; each stack belongs to one vertex builder.
 *
 * <p>All pushed colors multiply component-wise, including alpha. This modulates
 * individual draws; it does not perform isolated compositing of overlapping draws.
 * Push and pop are O(1), except when push grows the backing storage.
 */
public final class ColorStack {
  private static final int CHANNELS = 4;
  private float[] products = new float[CHANNELS * 8];
  private int depth;

  /** Creates a stack whose empty value is opaque white. */
  public ColorStack() {
    Arrays.fill(products, 0, CHANNELS, 1F);
  }

  /**
   * Multiplies subsequent draw colors by this additional modifier.
   * @param color the RGBA modifier retained until the corresponding pop
   */
  public void push(Color color) {
    int previous = depth * CHANNELS;
    int next = previous + CHANNELS;
    if (next + CHANNELS > products.length) {
      products = Arrays.copyOf(products, products.length * 2);
    }
    products[next] = products[previous] * color.red();
    products[next + 1] = products[previous + 1] * color.green();
    products[next + 2] = products[previous + 2] * color.blue();
    products[next + 3] = products[previous + 3] * color.alpha();
    depth++;
  }

  /**
   * Restores the modifiers that preceded the last push.
   * @throws IllegalStateException if no modifier has been pushed
   */
  public void pop() {
    if (depth == 0) {
      throw new IllegalStateException("Color stack is empty");
    }
    depth--;
  }

  /** Removes all modifiers without changing a builder's local tint. */
  public void clear() {
    depth = 0;
  }

  /**
   * Multiplies a packed float16 RGBA color by the accumulated modifiers.
   * @param color the local draw color
   * @return the resulting packed float16 RGBA color
   */
  public long apply(long color) {
    if (depth == 0) {
      return color;
    }
    int offset = depth * CHANNELS;
    return Color.packF16LE(
        Float.float16ToFloat((short) color) * products[offset],
        Float.float16ToFloat((short) (color >>> 16)) * products[offset + 1],
        Float.float16ToFloat((short) (color >>> 32)) * products[offset + 2],
        Float.float16ToFloat((short) (color >>> 48)) * products[offset + 3]);
  }
}
