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

import net.fmhi.Registries;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.fluid.FluidEngine;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.fluid.LiquidStack;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.light.LightEngine;
import net.fmhi.world.light.ScanLightEngine;
import net.fmhi.world.physics.Polygon;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A chunked 2D world.
 *
 * <p>Chunks are generated on demand via {@link ChunkGenerator}. The
 * level owns all chunks and provides tile and collision queries.
 */
@NullMarked
public class Level {

  private long ticks;
  private final ChunkGenerator generator;
  private final long seed;
  private final Map<Long, Chunk> chunks = new HashMap<>();
  private final LightEngine lightEngine = new ScanLightEngine(this);
  private final FluidEngine fluidEngine = new FluidEngine(this);

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
    ticks++;
    fluidEngine.tick(delta);
    for (Chunk chunk : Set.copyOf(chunks.values())) {
      chunk.tick(delta);
    }
  }

  public long getTicks() { return ticks; }

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
   * Removes the chunk from the world and its liquid cells from the fluid
   * engine (the chunk is regenerated on next access).
   */
  public void unloadChunk(ChunkPos pos) {
    chunks.remove(pos.asLong());
    fluidEngine.delChunk(pos);
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
  }

  public void setWall(BlockPos pos, BlockState state) {
    Chunk chunk = getOrLoadChunkByKey(ChunkPos.packBlockPosAsLong(pos.x(), pos.y()));
    chunk.setWall(pos.x(), pos.y(), state);
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
}
