package net.fmhi.world.level;

import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.LiquidStack;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Pre-loaded chunk window for zero-allocation tile access.
 *
 * <p>Chunks in the given world rectangle are loaded into a flat array
 * indexed by local chunk coordinates. All tile lookups are O(1) array
 * accesses — no HashMap, no ChunkPos allocation.
 */
public final class ChunkCache {
  private final Chunk[] chunks;
  private final int minCX, minCY, stride;
  private final int cs = ChunkPos.SIZE;

  public ChunkCache(Level level, int minWX, int minWY, int maxWX, int maxWY) {
    minCX = Math.floorDiv(minWX, cs);
    minCY = Math.floorDiv(minWY, cs);
    int maxCX = Math.floorDiv(maxWX, cs);
    int maxCY = Math.floorDiv(maxWY, cs);
    stride = maxCX - minCX + 1;
    chunks = new Chunk[(maxCX - minCX + 1) * (maxCY - minCY + 1)];
    for (int cx = minCX; cx <= maxCX; cx++)
      for (int cy = minCY; cy <= maxCY; cy++)
        chunks[(cx - minCX) + (cy - minCY) * stride] = level.getChunk(new ChunkPos(cx, cy));
  }

  private Chunk chunk(int wx, int wy) {
    int cx = Math.floorDiv(wx, cs) - minCX;
    int cy = Math.floorDiv(wy, cs) - minCY;
    if (cx < 0 || cy < 0 || cx >= stride || cy >= chunks.length / stride) return null;
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
    if (c == null) return null;
    return Liquids.byId(c.getLiquidType(wx, wy));
  }

  public int getLiquidAmount(int wx, int wy) {
    Chunk c = chunk(wx, wy);
    if (c == null) return 0;
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
