package net.fmhi.world.fluid;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable carrier for a discrete liquid level. Levels are in tile units:
 * {@code 255} is a full tile, the minimum amount is 1.
 */
@NullMarked
public record LiquidStack(Liquid liquid, int level) {

  /** An empty stack. */
  public static final LiquidStack EMPTY = new LiquidStack(Liquids.EMPTY, 0);

  public LiquidStack {
    if (level < 0) level = 0;
    if (level == 0) liquid = Liquids.EMPTY;
  }
}
