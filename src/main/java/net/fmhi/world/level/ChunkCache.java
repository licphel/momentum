package net.fmhi.world.level;

import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Pre-loaded chunk window for zero-allocation tile access.
 *
 * <p>Chunks in the covered world rectangle are loaded into a flat array
 * indexed by local chunk coordinates. All tile lookups are O(1) array
 * accesses — no HashMap, no ChunkPos allocation.
 *
 * <p>The window is created once and grown via {@link #checkLoss(int, int, int, int)}
 * when the requested rectangle extends beyond it; the overlapping chunks are
 * kept, only the new cells are filled in.
 */
public final class ChunkCache {
  private final Level level;
  private final int cs = ChunkPos.SIZE;
  private @Nullable Chunk[] chunks = new Chunk[0];
  private int minCX;
  private int minCY;
  private int stride;

  /** Creates an empty cache; fill it with {@link #checkLoss(int, int, int, int)}. */
  public ChunkCache(Level level) {
    this.level = level;
  }

  /**
   * Ensures the cache covers the given world rectangle, growing it and
   * filling in the newly covered chunks when needed; chunks that were not
   * loaded yet are picked up from the level.
   *
   * @param minWX the minimum world X to cover
   * @param minWY the minimum world Y to cover
   * @param maxWX the maximum world X to cover
   * @param maxWY the maximum world Y to cover
   */
  public void checkLoss(int minWX, int minWY, int maxWX, int maxWY) {
    int nMinCX = Math.floorDiv(minWX, cs);
    int nMinCY = Math.floorDiv(minWY, cs);
    int nMaxCX = Math.floorDiv(maxWX, cs);
    int nMaxCY = Math.floorDiv(maxWY, cs);
    if (nMinCX >= minCX && nMinCY >= minCY
        && nMaxCX < minCX + stride && nMaxCY < minCY + height()) {
      // the window already covers the rectangle: pick up chunks that were
      // not loaded when the cell was last touched
      fill(nMinCX, nMinCY, nMaxCX, nMaxCY);
      return;
    }
    grow(nMinCX, nMinCY, nMaxCX, nMaxCY);
  }

  /** Reallocates the window around the new rectangle, keeping the overlap. */
  private void grow(int nMinCX, int nMinCY, int nMaxCX, int nMaxCY) {
    @Nullable Chunk[] old = chunks;
    int oldMinCX = minCX;
    int oldMinCY = minCY;
    int oldStride = stride;
    int oldHeight = oldStride == 0 ? 0 : old.length / oldStride;
    minCX = nMinCX;
    minCY = nMinCY;
    stride = nMaxCX - nMinCX + 1;
    chunks = new Chunk[stride * (nMaxCY - nMinCY + 1)];

    for (int cx = Math.max(nMinCX, oldMinCX); cx <= Math.min(nMaxCX, oldMinCX + oldStride - 1); cx++) {
      for (int cy = Math.max(nMinCY, oldMinCY); cy <= Math.min(nMaxCY, oldMinCY + oldHeight - 1); cy++) {
        chunks[(cx - minCX) + (cy - minCY) * stride] =
            old[(cx - oldMinCX) + (cy - oldMinCY) * oldStride];
      }
    }

    fill(nMinCX, nMinCY, nMaxCX, nMaxCY);
  }

  /** Fills the null cells of the given chunk rectangle from the level. */
  private void fill(int nMinCX, int nMinCY, int nMaxCX, int nMaxCY) {
    for (int cx = nMinCX; cx <= nMaxCX; cx++) {
      for (int cy = nMinCY; cy <= nMaxCY; cy++) {
        int idx = (cx - minCX) + (cy - minCY) * stride;
        if (chunks[idx] == null) {
          chunks[idx] = level.getChunk(new ChunkPos(cx, cy));
        }
      }
    }
  }

  private int height() {
    return chunks.length / stride;
  }

  private @Nullable Chunk chunk(int wx, int wy) {
    int cx = Math.floorDiv(wx, cs) - minCX;
    int cy = Math.floorDiv(wy, cs) - minCY;
    if (cx < 0 || cy < 0 || cx >= stride || cy >= height()) {
      return null;
    }
    return chunks[cx + cy * stride];
  }

  public BlockState getBlock(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    return c != null ? c.getBlock(wx - Math.floorDiv(wx, cs) * cs, wy - Math.floorDiv(wy, cs) * cs) : BlockState.EMPTY;
  }

  public BlockState getWall(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    return c != null ? c.getWall(wx - Math.floorDiv(wx, cs) * cs, wy - Math.floorDiv(wy, cs) * cs) : BlockState.EMPTY;
  }

  public @Nullable Liquid getLiquid(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    if (c == null) {
      return null;
    }
    return Liquids.byId(c.getLiquidType(wx, wy));
  }

  public int getLiquidAmount(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    if (c == null) {
      return 0;
    }
    return c.getLiquidLevel(wx, wy);
  }

  public boolean isFrontSolid(int wx, int wy) {
    return getBlock(wx, wy).shape() == Shape.SOLID;
  }

  public boolean isLoaded(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    return c != null && c.isLoaded;
  }

  public int liquidLevel(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    return c != null ? c.getLiquidLevel(wx, wy) : 0;
  }

  public byte liquidType(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    return c != null ? c.getLiquidType(wx, wy) : 0;
  }
}
