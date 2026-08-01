package net.fmhi.world.light;

/**
 * Per-channel composition of two light values: {@code src} is the light
 * already in the tile, {@code dst} is the incoming light.
 *
 * <ul>
 *   <li>{@link #MAX} — per-channel maximum. Fastest; a bright white light
 *       washes out colored light entirely (its high green/blue channels win).</li>
 *   <li>{@link #ADDITIVE} — unbounded addition. Colored light keeps its hue,
 *       but overlapping strong lights blow out to white.</li>
 *   <li>{@link #ADDITIVE_CAP} — additive with a luminance-aware cap:
 *       {@code src + dst * (1 - src/cap)}. Dark areas add fully (hue kept),
 *       bright areas asymptotically accept no more light (no whiteout).</li>
 * </ul>
 */
@FunctionalInterface
public interface CompositionFormula {
  /** Per-channel maximum: the brighter of the two values wins. */
  CompositionFormula MAX = Math::max;
  /** Unbounded per-channel sum of the two values. */
  CompositionFormula ADDITIVE = Float::sum;
  /**
   * Additive blend with a luminance-aware cap: dark areas add fully, bright
   * areas asymptotically accept no more light.
   */
  CompositionFormula ADDITIVE_CAP =
      (src, dst) -> src + dst * (1F - src / 2.0F);

  /**
   * Combines the existing light value with the incoming one.
   *
   * @param src the light already present
   * @param dst the incoming light
   * @return the combined light value
   */
  float blend(float src, float dst);
}
