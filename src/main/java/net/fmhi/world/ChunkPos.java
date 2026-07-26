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

package net.fmhi.world;

/**
 * Immutable integer position identifying a chunk in the world grid.
 *
 * <p>A chunk spans {@code [x * SIZE, (x+1) * SIZE) × [y * SIZE,
 * (y+1) * SIZE)} in block coordinates. The default chunk size is
 * {@value #SIZE} blocks per edge.
 *
 * @param x the X chunk coordinate
 * @param y the Y chunk coordinate
 * @see BlockPos
 * @see Pos
 */
public record ChunkPos(int x, int y) {
  /** Default number of blocks along each chunk edge. */
  public static final int SIZE = 16;
  /** Chunk at the origin. */
  public static final ChunkPos ZERO = new ChunkPos(0, 0);

  /**
   * Unpacks a {@code long} produced by {@link #asLong()}.
   *
   * @param packed the packed position
   * @return the chunk position
   */
  public static ChunkPos fromLong(long packed) {
    return new ChunkPos((int) (packed >> 32), (int) packed);
  }

  /**
   * Returns an offset chunk position.
   *
   * @param dx the X offset in chunks
   * @param dy the Y offset in chunks
   * @return {@code (x + dx, y + dy)}
   */
  public ChunkPos offset(int dx, int dy) {
    return new ChunkPos(x + dx, y + dy);
  }

  /**
   * Returns the minimum block position in this chunk.
   *
   * @return the block at the chunk's minimum corner
   */
  public BlockPos minBlockPos() {
    return minBlockPos(SIZE);
  }

  /**
   * Returns the minimum block position in this chunk.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return the block at the chunk's minimum corner
   */
  public BlockPos minBlockPos(int chunkSize) {
    return new BlockPos(x * chunkSize, y * chunkSize);
  }

  /**
   * Returns the maximum inclusive block position in this chunk.
   *
   * @return the block at the chunk's far corner (exclusive bound minus one)
   */
  public BlockPos maxBlockPos() {
    return maxBlockPos(SIZE);
  }

  /**
   * Returns the maximum inclusive block position in this chunk.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return the block at the chunk's far corner (exclusive bound minus one)
   */
  public BlockPos maxBlockPos(int chunkSize) {
    return new BlockPos((x + 1) * chunkSize - 1, (y + 1) * chunkSize - 1);
  }

  /**
   * Returns the block position at the given local coordinates within this
   * chunk.
   *
   * @param localX the X offset within the chunk ({@code [0, SIZE)})
   * @param localY the Y offset within the chunk ({@code [0, SIZE)})
   * @return the world block position
   */
  public BlockPos blockAt(int localX, int localY) {
    return blockAt(localX, localY, SIZE);
  }

  /**
   * Returns the block position at the given local coordinates within this
   * chunk.
   *
   * @param localX    the X offset within the chunk ({@code [0, chunkSize)})
   * @param localY    the Y offset within the chunk ({@code [0, chunkSize)})
   * @param chunkSize the chunk edge size in blocks
   * @return the world block position
   */
  public BlockPos blockAt(int localX, int localY, int chunkSize) {
    return new BlockPos(x * chunkSize + localX, y * chunkSize + localY);
  }

  /**
   * Returns whether the given block position lies within this chunk.
   *
   * @param pos the block position to test
   * @return {@code true} if the block is inside this chunk
   */
  public boolean contains(BlockPos pos) {
    return contains(pos, SIZE);
  }

  /**
   * Returns whether the given block position lies within this chunk.
   *
   * @param pos       the block position to test
   * @param chunkSize the chunk edge size in blocks
   * @return {@code true} if the block is inside this chunk
   */
  public boolean contains(BlockPos pos, int chunkSize) {
    int minX = x * chunkSize;
    int minY = y * chunkSize;
    return pos.x() >= minX && pos.x() < minX + chunkSize
        && pos.y() >= minY && pos.y() < minY + chunkSize;
  }

  /**
   * Returns whether the given world position lies within this chunk.
   *
   * @param pos the world position to test
   * @return {@code true} if the position is inside this chunk
   */
  public boolean contains(Pos pos) {
    return contains(pos.toBlockPos());
  }

  /**
   * Returns whether the given world position lies within this chunk.
   *
   * @param pos       the world position to test
   * @param chunkSize the chunk edge size in blocks
   * @return {@code true} if the position is inside this chunk
   */
  public boolean contains(Pos pos, int chunkSize) {
    return contains(pos.toBlockPos(), chunkSize);
  }

  /**
   * Returns the squared Euclidean distance to another chunk position.
   *
   * @param o the other chunk position
   * @return {@code dx² + dy²}
   */
  public int distanceSquared(ChunkPos o) {
    int dx = x - o.x;
    int dy = y - o.y;
    return dx * dx + dy * dy;
  }

  /**
   * Returns the world position of this chunk's minimum corner.
   *
   * @return {@code (x * SIZE, y * SIZE)}
   */
  public Pos minPos() {
    return minPos(SIZE);
  }

  /**
   * Returns the world position of this chunk's minimum corner.
   *
   * @param chunkSize the chunk edge size in blocks
   * @return {@code (x * chunkSize, y * chunkSize)}
   */
  public Pos minPos(int chunkSize) {
    return new Pos((double) x * chunkSize, (double) y * chunkSize);
  }

  /**
   * Packs this chunk position into a {@code long}. The upper 32 bits hold
   * {@code x}, the lower 32 bits hold {@code y}.
   *
   * @return the packed position
   */
  public long asLong() {
    return ((long) x << 32) | (y & 0xFFFFFFFFL);
  }
}
