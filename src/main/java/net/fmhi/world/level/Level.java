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

  public Collection<Chunk> loadedChunks() { return chunks.values(); }

  // -- tick ----------------------------------------------------------------

  public void tick(double delta) {
    ticks++;
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

  public int loadedChunkCount() { return chunks.size(); }

  // -- tiles ---------------------------------------------------------------

  /**
   * Returns the block state at the given position, generating the
   * containing chunk if needed.
   */
  public BlockState getBlock(BlockPos pos) {
    ChunkPos cp = pos.toChunkPos();
    Chunk chunk = getOrLoadChunk(cp);
    return chunk.getBlock(pos);
  }

  public BlockState getBlock(int x, int y) { return getBlock(new BlockPos(x, y)); }

  public BlockState getWall(int x, int y) {
    ChunkPos cp = new ChunkPos(Math.floorDiv(x, ChunkPos.SIZE), Math.floorDiv(y, ChunkPos.SIZE));
    Chunk chunk = getChunk(cp);
    return chunk != null ? chunk.getWall(x - cp.x() * ChunkPos.SIZE, y - cp.y() * ChunkPos.SIZE) : BlockState.EMPTY;
  }

  /**
   * Sets the block at the given position.
   */
  public void setBlock(BlockPos pos, BlockState state) {
    ChunkPos cp = pos.toChunkPos();
    Chunk chunk = getOrLoadChunk(cp);
    chunk.setBlock(pos, state);
  }

  public void setWall(BlockPos pos, BlockState state) {
    ChunkPos cp = pos.toChunkPos();
    Chunk chunk = getOrLoadChunk(cp);
    chunk.setWall(pos, state);
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
}
