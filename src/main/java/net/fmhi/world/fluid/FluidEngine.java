package net.fmhi.world.fluid;

import net.fmhi.util.Profiler;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Random;

/**
 * Classic Starbound liquid cellular automaton, run on the main thread from
 * {@link Level#tick(double)}.
 *
 * <p>Each tile holds a discrete liquid level ({@code 255} = full, more is
 * overfill, the minimum amount is 1). Every simulation tick, a tile first
 * falls into the tile below (filling it up to full), then equalizes
 * sideways by moving half the integer level difference into a lower
 * neighbour — a single unit that cannot split further evaporates — and
 * finally squeezes any overfill (level &gt; 255) upward, so water falls
 * quickly, seeks its own level and rises under pressure, all from purely
 * local rules.
 *
 * <p>Only tiles that hold liquid are simulated. A tile joins the active
 * list when liquid is written into it ({@link #join(int, int)}, called by
 * {@code Level.setLiquid} and by every flow) and is removed when its chunk
 * unloads ({@link #delChunk(ChunkPos)}), so the engine never scans the
 * whole world. The tick processes the list sorted bottom-up; flowing liquid
 * re-joins its destination, so it falls at most one tile per tick and every
 * tile is updated at most once. Solid tiles and unloaded chunks are
 * impassable. Different liquids touching each other react (lava + water →
 * stone) or the thinner one converts into the thicker one, so they never
 * just sit layered next to each other.
 */
@NullMarked
public final class FluidEngine {

  /** The level of a full tile. */
  public static final int FULL = 255;

  /** Simulation cadence in seconds (60 Hz, like Starbound's liquid tick). */
  public static final double TICK_INTERVAL = 1.0 / 60.0;

  /** The minimum amount of liquid that exists: a single unit that cannot
   * split further during equalization evaporates. */
  public static final int MINIMUM_LIQUID_LEVEL = 1;

  /** Minimum total surrounding liquid for lava to solidify. */
  public static final int LAVA_REACT_AMOUNT = 26;

  private final Level level;
  private final Random random = new Random(42);
  private double tickAcc;

  // Active cells: tiles that hold liquid and need an update. The list is
  // unsorted and may contain duplicates; each tick sorts and dedupes it,
  // then updates the tiles bottom-up. Cells join via join() when liquid is
  // written and leave via delChunk() when a chunk unloads.
  private long[] active = new long[256];
  private int activeCount;

  // Reused scratch cells so the hot loops allocate nothing.
  private final Cell cellA = new Cell();
  private final Cell cellB = new Cell();

  public FluidEngine(Level level) {
    this.level = level;
  }

  public void tick(double delta) {
    tickAcc += delta;
    while (tickAcc >= TICK_INTERVAL) {
      tickAcc -= TICK_INTERVAL;
      tickOnce();
    }
  }

  /**
   * Marks a tile as holding liquid so it is updated on the next tick.
   * Called by {@code Level.setLiquid} and by every flow.
   */
  public void join(int x, int y) {
    if (activeCount == active.length) active = Arrays.copyOf(active, active.length * 2);
    active[activeCount++] = ((long) y << 32) | (x & 0xFFFFFFFFL);
  }

  /** Removes all active cells of the given chunk (call when it unloads). */
  public void delChunk(ChunkPos pos) {
    int minX = pos.x() * ChunkPos.SIZE;
    int minY = pos.y() * ChunkPos.SIZE;
    int maxX = minX + ChunkPos.SIZE;
    int maxY = minY + ChunkPos.SIZE;
    int n = 0;
    for (int i = 0; i < activeCount; i++) {
      long key = active[i];
      int x = (int) key;
      int y = (int) (key >>> 32);
      if (x < minX || x >= maxX || y < minY || y >= maxY) active[n++] = key;
    }
    activeCount = n;
  }

  private void tickOnce() {
    // sort + dedupe the pending cells (join() may have added duplicates)
    try (Profiler.Scope _ = Profiler.scope("fluid:sort")) {
      Arrays.sort(active, 0, activeCount);
      int n = 0;
      for (int i = 0; i < activeCount; i++) {
        if (n == 0 || active[i] != active[n - 1]) active[n++] = active[i];
      }
      activeCount = n;
    }

    try (Profiler.Scope _ = Profiler.scope("fluid:update")) {
      updateCells();
    }
    try (Profiler.Scope _ = Profiler.scope("fluid:interact")) {
      findInteractions();
    }
  }

  /** The cellular automaton step: fall, then equalize sideways, then
   * squeeze overfill upward. */
  private void updateCells() {
    boolean leftFirst = random.nextBoolean();
    // bottom-up, like Starbound (Y-down world: highest y first)
    for (int i = activeCount - 1; i >= 0; i--) {
      Cell self = cellAt(i);
      if (self == null || self.type == 0 || self.level <= 0) continue;

      // 1) fall: drop into the tile below, filling it up to full
      Cell below = cellOf(self.x, self.y + 1, cellB);
      if (below != null) {
        transferLevel(self, self.x, self.y + 1, Math.min(self.level, FULL - below.level));
      }

      // 2) equalize: move half the level difference into a lower neighbour
      if (leftFirst) {
        equalizeSide(self, self.x - 1, self.y);
        equalizeSide(self, self.x + 1, self.y);
      } else {
        equalizeSide(self, self.x + 1, self.y);
        equalizeSide(self, self.x - 1, self.y);
      }

      // 3) pressure: overfill is squeezed upward
      if (self.level > FULL) {
        Cell top = cellOf(self.x, self.y - 1, cellB);
        if (top != null) {
          transferLevel(self, self.x, self.y - 1, Math.min(self.level - FULL, FULL - top.level));
        }
      }
    }
  }

  private void equalizeSide(Cell self, int nx, int ny) {
    Cell dst = cellOf(nx, ny, cellB);
    if (dst == null) return;
    int flow = (self.level - dst.level) / 2;
    if (flow <= 0) {
      // a single unit cannot split further: it evaporates instead
      if (self.level == MINIMUM_LIQUID_LEVEL) self.map.setLevel(self.x, self.y, 0);
      return;
    }
    transferLevel(self, nx, ny, flow);
  }

  /** Different liquids touching each other: lava reacts against water (→
   * stone); otherwise the thinner liquid converts into the thicker one, so
   * different liquids never just sit layered next to each other. */
  private void findInteractions() {
    for (int i = activeCount - 1; i >= 0; i--) {
      Cell self = cellAt(i);
      if (self == null || self.type == 0 || self.level <= 0) continue;
      interactSide(self, self.x - 1, self.y);
      interactSide(self, self.x + 1, self.y);
      interactSide(self, self.x, self.y - 1);
      interactSide(self, self.x, self.y + 1);
    }
  }

  private void interactSide(Cell self, int nx, int ny) {
    Cell dst = cellOf(nx, ny, cellB);
    if (dst == null || dst.type == 0 || dst.type == self.type) return;

    boolean selfLava = self.type == Liquids.LAVA.id();
    if (selfLava || dst.type == Liquids.LAVA.id()) {
      // the reaction fires from the lava tile's perspective and is
      // idempotent: it either solidifies (clearing the tiles) or no-ops
      Liquids.LAVA.onTouch(LiquidStack.EMPTY, LiquidStack.EMPTY, level,
          selfLava ? self.x : nx, selfLava ? self.y : ny, nx, ny);
      dst = cellOf(nx, ny, cellB);
      if (dst == null || dst.type == 0) return;
      self = cellOf(self.x, self.y, cellA);
      if (self == null || self.type == 0) return;
      if (dst.type == self.type) return;
    }

    // conversion: the thinner liquid becomes the thicker one
    if (self.level > dst.level) {
      dst.map.setType(nx, ny, self.type);
      join(nx, ny);
    } else {
      self.map.setType(self.x, self.y, dst.type);
      join(self.x, self.y);
    }
  }

  // -- transfers ------------------------------------------------------------

  /** Moves liquid from the source cell into the tile at {@code (dx, dy)},
   * re-joining both tiles for the next tick. */
  private void transferLevel(Cell src, int dx, int dy, int amount) {
    if (amount <= 0 || src.level <= 0) return;
    Cell dst = cellOf(dx, dy, cellB);
    if (dst == null) return;
    if (dst.type != 0 && dst.type != src.type) return;
    amount = Math.min(amount, src.level);
    src.level -= amount;
    dst.level += amount;
    src.map.setLevel(src.x, src.y, src.level);
    dst.map.setLevel(dx, dy, dst.level);
    dst.map.setType(dx, dy, src.type);
    join(src.x, src.y);
    join(dx, dy);
  }

  // -- cells ----------------------------------------------------------------

  private static final class Cell {
    LiquidMap map;
    int x;
    int y;
    int level;
    byte type;
  }

  private Cell cellAt(int i) {
    return cellOf((int) active[i], (int) (active[i] >>> 32), cellA);
  }

  /** Fills {@code out} with the tile's data, or {@code null} when the tile
   * is impassable (solid block or unloaded chunk). */
  private @Nullable Cell cellOf(int wx, int wy, Cell out) {
    Chunk chunk = chunkAt(wx, wy);
    if (chunk == null || isSolid(chunk, wx, wy)) return null;
    LiquidMap lm = chunk.liquidMap();
    out.map = lm;
    out.x = wx;
    out.y = wy;
    out.level = lm.level(wx, wy);
    out.type = lm.liquidType(wx, wy);
    return out;
  }

  private @Nullable Chunk chunkAt(int wx, int wy) {
    return level.getChunk(new ChunkPos(Math.floorDiv(wx, ChunkPos.SIZE), Math.floorDiv(wy, ChunkPos.SIZE)));
  }

  private boolean isSolid(Chunk chunk, int wx, int wy) {
    BlockState state = chunk.getBlock(wx, wy);
    return state.block().isSolid(state);
  }
}
