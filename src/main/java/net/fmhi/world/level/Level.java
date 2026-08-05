/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fmhi.world.level;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.fmhi.Registries;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.LiquidStack;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.light.LightEngine;
import net.fmhi.world.light.RelaxationLightEngine;
import net.fmhi.world.object.ObjectConfig;
import net.fmhi.world.object.WorldObject;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A chunked 2D world.
 *
 * <p>Chunks are generated on demand via {@link ChunkGenerator}. The
 * level owns all chunks and provides tile and collision queries.
 */
public class Level {
  /** Length of a full day in game ticks (60 Hz, 20 s per game minute, 24 h). */
  public static final long TICKS_PER_DAY = 60L * 20 * 24;
  /** Chunks farther than this (in chunk units) from the focus are unloaded.
   * 41×41 chunks cover the widest light window (512 tiles = 32 chunks) plus
   * margin; was 32 → 4225 chunks resident. */
  public static final int UNLOAD_RADIUS_CHUNKS = 20;

  private long ticks;
  /** Game time within the current day, advanced at the day clock's pace. */
  private double dayTicks;
  /** Multiplier on the day clock; 1 is real time. */
  private float timeScale = 1F;
  private final ChunkGenerator generator;
  private final long seed;
  private final Long2ObjectMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
  private final LightEngine lightEngine = new RelaxationLightEngine(this);
  private final FluidEngine fluidEngine = new FluidEngine(this);
  /** Focus in world block coordinates; chunks farther than
   * {@link #UNLOAD_RADIUS_CHUNKS} are unloaded every tick. */
  private double focusX;
  private double focusY;
  /** Notified with each chunk position that unloads (e.g. to release meshes). */
  private @Nullable Consumer<ChunkPos> unloadListener;

  /**
   * Creates a level with the given chunk generator.
   *
   * @param generator the chunk generator
   * @param seed      the world seed
   */
  public Level(ChunkGenerator generator, long seed) {
    this.generator = generator;
    this.seed = seed;
  }

  public LightEngine lightEngine() { return lightEngine; }

  public FluidEngine fluidEngine() { return fluidEngine; }

  public Collection<Chunk> loadedChunks() { return chunks.values(); }

  // -- tick ----------------------------------------------------------------

  public void tick(double delta) {
    unloadFarChunks();
    ticks++;
    dayTicks += delta * 60.0 * timeScale;
    fluidEngine.tick(delta);
    for (Chunk chunk : Set.copyOf(chunks.values())) {
      chunk.tick(delta);
    }
  }

  public long getTicks() { return ticks; }

  /**
   * Returns the game time within the current day, in ticks (fractional).
   */
  public double ticksOfDay() { return dayTicks; }

  /**
   * Returns the multiplier on the day clock; 1 is real time.
   */
  public float timeScale() { return timeScale; }

  /**
   * Sets the multiplier on the day clock. Only the day clock is affected;
   * simulation ticks, fluids and entities keep their real-time pace.
   */
  public void setTimeScale(float timeScale) { this.timeScale = timeScale; }

  // -- chunks --------------------------------------------------------------

  /**
   * Returns the chunk at the given position, generating it if needed.
   */
  public Chunk getOrLoadChunk(ChunkPos pos) {
    long key = pos.asLong();
    var existing = chunks.get(key);
    if (existing != null && existing.isLoaded) return existing;

    var chunk = chunks.computeIfAbsent(key, k -> new Chunk(this, pos));
    if (!chunk.isLoaded) {
      generator.generate(chunk, seed);
    }
    return chunk;
  }

  /**
   * Returns the chunk at the given position, or {@code null} if not
   * loaded.
   */
  public @Nullable Chunk getChunk(ChunkPos pos) {
    return chunks.get(pos.asLong());
  }

  /**
   * Returns the chunk containing the given block position by its packed
   * map key, or {@code null} if not loaded. Allocation-free lookup.
   */
  public @Nullable Chunk getChunkByKey(long key) {
    return chunks.get(key);
  }

  /**
   * Returns the chunk containing the given block position, generating it
   * if needed. Allocation-free lookup.
   */
  public Chunk getOrLoadChunkByKey(long key) {
    var existing = chunks.get(key);
    if (existing != null && existing.isLoaded) return existing;
    var chunk = chunks.computeIfAbsent(key, k -> new Chunk(this, ChunkPos.fromLong(key)));
    if (!chunk.isLoaded) {
      generator.generate(chunk, seed);
    }
    return chunk;
  }

  public int loadedChunkCount() { return chunks.size(); }

  /**
   * Sets the streaming focus; chunks farther than {@link #UNLOAD_RADIUS_CHUNKS}
   * from it are unloaded every tick.
   */
  public void setFocus(double x, double y) {
    focusX = x;
    focusY = y;
  }

  /** Registers a callback invoked with each chunk position that unloads. */
  public void setUnloadListener(@Nullable Consumer<ChunkPos> listener) {
    this.unloadListener = listener;
  }

  /**
   * Unloads every chunk farther than {@link #UNLOAD_RADIUS_CHUNKS} from the
   * focus, so explored areas do not accumulate for the whole session.
   */
  private void unloadFarChunks() {
    long radiusSq = (long) UNLOAD_RADIUS_CHUNKS * UNLOAD_RADIUS_CHUNKS;
    int fcx = Math.floorDiv((int) focusX, ChunkPos.SIZE);
    int fcy = Math.floorDiv((int) focusY, ChunkPos.SIZE);
    var candidates = new ArrayList<ChunkPos>();
    for (var it = chunks.keySet().longIterator(); it.hasNext(); ) {
      long key = it.nextLong();
      long dx = (key >> 32) - fcx;
      long dy = ((int) key) - fcy;
      if (dx * dx + dy * dy > radiusSq) candidates.add(ChunkPos.fromLong(key));
    }
    for (ChunkPos pos : candidates) unloadChunk(pos);
  }

  /**
   * Removes the chunk from the world and its liquid cells from the fluid
   * engine (the chunk is regenerated on next access). Notifies the unload
   * listener so retained render meshes can be released.
   */
  public void unloadChunk(ChunkPos pos) {
    chunks.remove(pos.asLong());
    fluidEngine.delChunk(pos);
    if (unloadListener != null) unloadListener.accept(pos);
  }

  // -- tiles ---------------------------------------------------------------

  /**
   * Returns the block state at the given position, generating the
   * containing chunk if needed.
   */
  public BlockState getBlock(BlockPos pos) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y())).getBlock(pos.x(), pos.y());
  }

  public BlockState getBlock(int x, int y) {
    return getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).getBlock(x, y);
  }

  /**
   * Places a multi-tile world object at the given main tile (Starbound style: the
   * object is an entity floating above the tile grid — its spaces must be empty and,
   * when {@code rooting}, its anchors must be solid).
   *
   * @param config the object definition
   * @param x      the main tile X
   * @param y      the main tile Y
   * @return the placed object, or {@code null} if the placement was invalid
   */
  public @Nullable WorldObject placeObject(ObjectConfig config, int x, int y) {
    for (BlockPos s : config.spaces()) {
      if (getBlock(x + s.x(), y + s.y()).shape() == Shape.SOLID) {
        return null; // a space is occupied
      }
    }
    if (config.rooting()) {
      boolean anyValid = false;
      for (BlockPos a : config.anchors()) {
        if (getBlock(x + a.x(), y + a.y()).shape() == Shape.SOLID) {
          anyValid = true;
        } else if (!config.anchorAny()) {
          return null; // an anchor has no support
        }
      }
      if (config.anchorAny() && !anyValid) {
        return null;
      }
    }
    WorldObject obj = new WorldObject(config, x, y);
    obj.enterChunk(this);
    return obj;
  }

  public BlockState getWall(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getWall(x, y) : BlockState.EMPTY;
  }

  /**
   * Sets the block at the given position.
   */
  public void setBlock(BlockPos pos, BlockState state) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y()));
    chunk.setBlock(pos.x(), pos.y(), state);
    // a solid block replaces any liquid in its tile
    if (state.shape() == Shape.SOLID) {
      chunk.setLiquid(pos.x(), pos.y(), Liquids.EMPTY, 0);
    }
    markDirtyNeighbours(pos.x(), pos.y(), true);
  }

  public void setWall(BlockPos pos, BlockState state) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y()));
    chunk.setWall(pos.x(), pos.y(), state);
    markDirtyNeighbours(pos.x(), pos.y(), false);
  }

  /**
   * A tile on a chunk border changes the border pieces of the adjacent
   * chunk (its edges depend on this tile); a corner tile affects all four
   * surrounding chunks (Enchant NearDirty). {@code front} selects whether
   * the block or the wall layer of the neighbours is invalidated.
   */
  private void markDirtyNeighbours(int wx, int wy, boolean front) {
    int cs = ChunkPos.SIZE;
    int lx = Math.floorMod(wx, cs);
    int ly = Math.floorMod(wy, cs);
    int cx = Math.floorDiv(wx, cs);
    int cy = Math.floorDiv(wy, cs);
    if (lx == 0) dirtyChunk(cx - 1, cy, front);
    if (lx == cs - 1) dirtyChunk(cx + 1, cy, front);
    if (ly == 0) dirtyChunk(cx, cy - 1, front);
    if (ly == cs - 1) dirtyChunk(cx, cy + 1, front);
    if (lx == 0 && ly == 0) dirtyChunk(cx - 1, cy - 1, front);
    if (lx == cs - 1 && ly == 0) dirtyChunk(cx + 1, cy - 1, front);
    if (lx == 0 && ly == cs - 1) dirtyChunk(cx - 1, cy + 1, front);
    if (lx == cs - 1 && ly == cs - 1) dirtyChunk(cx + 1, cy + 1, front);
  }

  private void dirtyChunk(int cx, int cy, boolean front) {
    Chunk c = chunks.get(((long) cx << 32) | (cy & 0xFFFFFFFFL));
    if (c != null) {
      if (front) c.frontDirty = true;
      else c.backDirty = true;
    }
  }

  /**
   * Returns the physics collision polygon for the block at the given
   * position, or {@code null} if the block is air / non-solid.
   */
  public @Nullable Polygon getBlockPolygon(BlockPos pos) {
    BlockState state = getBlock(pos);
    if (state == null || state.block() == Registries.AIR) return null;
    Polygon shape = state.getPhysicsShape(pos, null);
    if (shape == null) return null;
    return shape.translate(pos.x(), pos.y());
  }

  public long seed() { return seed; }

  // -- liquids -------------------------------------------------------------

  /**
   * Sets the liquid of a tile, generating the containing chunk if needed
   * and joining the tile to the fluid engine. Levels are discrete tile
   * units: {@code 255} is a full tile, the minimum amount is 1.
   */
  public void setLiquid(int x, int y, Liquid liquid, int level) {
    getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(x, y)).setLiquid(x, y, liquid, level);
    if (level > 0) fluidEngine.join(x, y);
  }

  /**
   * Returns the liquid level of a tile, or {@code 0} if none or the chunk
   * is not loaded.
   */
  public int getLiquidLevel(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getLiquidLevel(x, y) : 0;
  }

  /** Returns the liquid id of a tile, or {@code 0} if none or unloaded. */
  public byte getLiquidType(int x, int y) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    return chunk != null ? chunk.getLiquidType(x, y) : 0;
  }

  /** Sets the liquid level of a tile (engine-internal writes). */
  public void setLiquidLevel(int x, int y, int level) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk != null) chunk.setLiquidLevel(x, y, level);
  }

  /** Sets the liquid type of a tile (engine-internal writes). */
  public void setLiquidType(int x, int y, byte id) {
    Chunk chunk = getChunkByKey(ChunkPos.packBlockPosAsLong(x, y));
    if (chunk != null) chunk.setLiquidType(x, y, id);
  }

  /**
   * Returns the liquid stack of a tile, or {@link LiquidStack#EMPTY} if
   * none or the chunk is not loaded.
   */
  public LiquidStack getLiquidStack(int x, int y) {
    int lv = getLiquidLevel(x, y);
    return lv <= 0 ? LiquidStack.EMPTY : new LiquidStack(Liquids.byId(getLiquidType(x, y)), lv);
  }

  public int getSeaLevel() {
    return 0;
  }

  public int getSpaceLevel() {
    return 256;
  }
}
