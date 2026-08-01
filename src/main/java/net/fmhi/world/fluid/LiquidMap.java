package net.fmhi.world.fluid;

import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;

/**
 * Per-chunk liquid layer: discrete level per tile ({@code 255} is a full
 * tile, levels above {@code 255} are overfill/pressure, the minimum amount
 * is 1).
 *
 * <p>Coordinates are masked into the chunk (world coordinates work like on
 * {@code MappingArray}).
 */
@NullMarked
public final class LiquidMap {

  private static final int MASK = ChunkPos.SIZE - 1;

  private final int[] level = new int[ChunkPos.SIZE * ChunkPos.SIZE];
  private final byte[] liquidType = new byte[ChunkPos.SIZE * ChunkPos.SIZE];

  /** The liquid level of a tile ({@code 0} = empty, {@code 255} = full). */
  public int level(int wx, int wy) {
    return level[idx(wx, wy)];
  }

  /** The liquid id of a tile, see {@link Liquids#byId(int)}. */
  public byte liquidType(int wx, int wy) {
    return liquidType[idx(wx, wy)];
  }

  /** Sets the liquid level of a tile; {@code <= 0} clears it. */
  public void setLevel(int wx, int wy, int v) {
    int i = idx(wx, wy);
    if (v <= 0) {
      level[i] = 0;
      liquidType[i] = 0;
    } else {
      level[i] = v;
    }
  }

  /** Sets the liquid type of a tile. */
  public void setType(int wx, int wy, byte id) {
    liquidType[idx(wx, wy)] = id;
  }

  /** Sets the liquid of a tile; levels {@code <= 0} clear it. */
  public void set(int wx, int wy, Liquid liquid, int level) {
    int i = idx(wx, wy);
    if (level <= 0) {
      this.level[i] = 0;
      this.liquidType[i] = 0;
    } else {
      this.level[i] = level;
      this.liquidType[i] = liquid.id();
    }
  }

  private static int idx(int wx, int wy) {
    return ((wy & MASK) << 4) | (wx & MASK);
  }
}
