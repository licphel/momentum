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
import net.fmhi.world.block.Block;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.BlockStateHolder;
import net.fmhi.world.util.BlockPos;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;

/**
 * Simple flat terrain with grass → dirt → stone layers.
 */
@NullMarked
public final class FlatTerrainGenerator implements ChunkGenerator {

  private final int groundY;

  public FlatTerrainGenerator(int groundY) {
    this.groundY = groundY;
  }

  @Override
  public void generate(Chunk chunk, long seed) {
    var palette = BlockStateHolder.BLOCK_STATE_PROPERTY_PALETTE;
    var air = state(Registries.AIR, palette);
    var grass = state(Registries.GRASS, palette);
    var dirt = state(Registries.DIRT, palette);
    var stone = state(Registries.STONE, palette);
    var slopeR = state(Registries.SLOPE_RIGHT, palette);
    var slopeL = state(Registries.SLOPE_LEFT, palette);
    var plat = state(Registries.PLATFORM, palette);
    var wall = state(Registries.WALL, palette);
    var pole = state(Registries.COLORFUL, palette);

    ChunkPos cp = chunk.chunkPos;
    for (int lx = 0; lx < ChunkPos.SIZE; lx++) {
      for (int ly = 0; ly < ChunkPos.SIZE; ly++) {
        int wx = cp.x() * ChunkPos.SIZE + lx;
        int wy = cp.y() * ChunkPos.SIZE + ly;
        var pos = new BlockPos(wx, wy);

        BlockState s = null;

        // light sources: WALL at (2, groundY-2), POLE at plateau
        if (wx == 2 && wy == groundY - 2) s = wall;
        if (wx == 15 && wy == groundY - 8) s = pole;

        if (wx <= 0) {
          if (wy == groundY - 5) {
            s = plat;
          }
        }

        // ↗ uphill: surface rises from (5,groundY) to (12,groundY-7)
        if (wx >= 5 && wx <= 12) {
          int surfaceY = groundY - (wx - 5);
          if (wy == surfaceY) s = slopeR;
          else if (wy > surfaceY && wy <= groundY + 4) s = dirt;
          else if (wy > groundY + 4) s = stone;
          else s = air;
        }
        // plateau between ramps
        else if (wx >= 13 && wx <= 17) {
          int surfaceY = groundY - 7;
          if (wy == surfaceY) s = grass;
          else if (wy > surfaceY && wy <= groundY + 4) s = dirt;
          else if (wy > groundY + 4) s = stone;
          else s = air;
        }
        // ↘ downhill: surface descends from (18,groundY-7) to (25,groundY)
        else if (wx >= 18 && wx <= 24) {
          int surfaceY = groundY - 7 + (wx - 18);
          if (wy == surfaceY) s = slopeL;
          else if (wy > surfaceY && wy <= groundY + 4) s = dirt;
          else if (wy > groundY + 4) s = stone;
          else s = air;
        }
        // default flat terrain
        if (s == null) {
          if (wy < groundY) s = air;
          else if (wy == groundY) s = grass;
          else if (wy < groundY + 5) s = dirt;
          else s = stone;
        }

        chunk.setBlock(pos, s);

        // background walls behind all underground tiles
        if (wy > groundY && s != air) {
          chunk.setWall(pos, stone);
        }
      }
    }
    chunk.setLoaded(true);
  }

  private static BlockState state(Block block,
      net.fmhi.collection.Palette<BlockState> palette) {
    int id = block.propertyDef().defaultMap().identity();
    return palette.get(id);
  }
}
