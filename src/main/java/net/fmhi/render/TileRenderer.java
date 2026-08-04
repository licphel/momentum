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

package net.fmhi.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fmhi.Registries;
import net.fmhi.gfx.Device;
import net.fmhi.gfx.brush.ZeroCopyVertexStore;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.mesh.Mesh;
import net.fmhi.gfx.brush.MeshGraphics2D;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.Color;
import net.fmhi.world.block.Block;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.block.Shape;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.ChunkPos;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Tile renderer: block bodies from a 64x64 full-texture random sample,
 * borders from Enchant's {@code BlockRenderBuffer.MakeBorderData}
 * translated verbatim (all numbers untouched — the UVs are the Enchant
 * sheet coordinates and are expected to be adjusted for the actual
 * material sheet layout).
 *
 * <p>Drawing order: all block bodies first, then all border pieces, so a
 * border always covers the body of the tile it is drawn on (Enchant
 * border meshes). Same-material neighbours connect seamlessly (no
 * borders between them).
 */
@NullMarked
public class TileRenderer {

  /** The two material sheets currently in use. */
  public enum Material {
    DIRT, STONE
  }

  // -- texture layout (pixel units) ----------------------------------------

  private static final int CELL = 8;
  /** Full block texture, sampled 8x8 per tile. */
  private static final int FULL_X = 0;
  private static final int FULL_Y = 0;
  /** Slope faces: 8x8 cells. Left-down (↗ high right), right-down
   * (↖ high left), left-up and right-up (ceilings). */
  private static final int SLOPE_LEFT_DOWN_X = 33;
  private static final int SLOPE_LEFT_DOWN_Y = 22;
  private static final int SLOPE_RIGHT_DOWN_X = 42;
  private static final int SLOPE_RIGHT_DOWN_Y = 22;
  private static final int SLOPE_LEFT_UP_X = 51;
  private static final int SLOPE_LEFT_UP_Y = 22;
  private static final int SLOPE_RIGHT_UP_X = 60;
  private static final int SLOPE_RIGHT_UP_Y = 22;

  private final TexturePart dirt;
  private final TexturePart stone;
  private final Device dev;
  /** Per-chunk retained block meshes, rebuilt when the block layer is dirty. */
  private final Long2ObjectMap<ChunkMesh> meshes = new Long2ObjectOpenHashMap<>();
  /** Per-chunk retained wall meshes, rebuilt when the wall layer is dirty. */
  private final Long2ObjectMap<ChunkMesh> wallMeshes = new Long2ObjectOpenHashMap<>();
  private final MeshGraphics2D meshG;

  private TileRenderer(Device dev) {
    this.dev = dev;
    this.dirt = GlobalAtlas.get("dirt");
    this.stone = GlobalAtlas.get("stone");
    meshG = new MeshGraphics2D(new ZeroCopyVertexStore(), dev);
  }

  /** Initializes the global atlas and creates the renderer. */
  public static TileRenderer create(Device dev) {
    try {
      GlobalAtlas.init(dev);
      return new TileRenderer(dev);
    } catch (Exception e) {
      return null;
    }
  }

  /** Releases all chunk meshes, the shared mesh builder and the global atlas. */
  public void close() {
    for (ChunkMesh cm : meshes.values()) cm.close();
    for (ChunkMesh cm : wallMeshes.values()) cm.close();
    meshes.clear();
    wallMeshes.clear();
    meshG.close();
    GlobalAtlas.dispose();
  }

  /** Releases the retained meshes of one chunk (on chunk unload); a
   * reloaded chunk must not reuse geometry of the previous instance. */
  public void unloadChunk(ChunkPos pos) {
    ChunkMesh cm = meshes.remove(pos.asLong());
    if (cm != null) cm.close();
    ChunkMesh wm = wallMeshes.remove(pos.asLong());
    if (wm != null) wm.close();
  }

  // -- material mapping -----------------------------------------------------

  /** Maps every block to DIRT or STONE; slopes become STONE slope
   * variants. */
  private static Material materialOf(Block b) {
    if (b == Registries.DIRT || b == Registries.GRASS) return Material.DIRT;
    return Material.STONE;
  }

  private @Nullable TexturePart sheet(Material m) {
    return m == Material.DIRT ? dirt : stone;
  }

  /** Enchant Dropper.GetPlaceHash, verbatim. */
  private static long placeHash(int x, int y) {
    long hash = (long) x * 374761393 + (long) y * 668265263;
    hash = (hash ^ (hash >> 15)) * 2246822519L;
    hash = (hash ^ (hash >> 13)) * 3266489917L;
    hash ^= (hash >> 16);
    return hash;
  }

  /** Enchant BlockRenderMode.PerfectVoxel: a fully solid block (not a
   * platform or other partial shape). */
  private static boolean isPerfectVoxel(BlockState s) {
    return s.shape() == Shape.SOLID;
  }

  /** Neighbour directions, in the neighbour's own frame. */
  private static final int DIR_LEFT = 0;
  private static final int DIR_UP = 1;
  private static final int DIR_RIGHT = 2;
  private static final int DIR_DOWN = 3;

  /**
   * Whether the block draws its own edge on the given side (the
   * neighbour's direction, mirrored): solid blocks draw on all sides,
   * slopes only on their two flat faces. A neighbour that draws its own
   * edge must not trigger ours, or the edge would be drawn twice; on the
   * slope's slanted face nobody draws, so the neighbour draws its own.
   */
  private static boolean drawsOwnEdges(BlockState s, int dir) {
    if (isPerfectVoxel(s)) return true;
    if (s.block() == Registries.SLOPE_RIGHT)
      return dir == DIR_DOWN || dir == DIR_RIGHT;
    if (s.block() == Registries.SLOPE_LEFT)
      return dir == DIR_DOWN || dir == DIR_LEFT;
    return false;
  }

  /** Enchant IsConnectable: the neighbour is the same block kind. */
  private static boolean connectable(BlockState neighbor, BlockState self) {
    return neighbor.block() == self.block();
  }

  // -- rendering -----------------------------------------------------------

  /**
   * Renders the visible chunks from their retained meshes (rebuilt when
   * dirty); animated blocks are drawn every frame in immediate mode.
   * Borders are part of the chunk mesh and drawn after the bodies.
   */
  public void render(BatchedGraphics2D g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    g.setColor(Color.WHITE);

    int k = 0;
    int sc = 0;
    // pass 1: draw every chunk body (animated block bodies too)
    for (int cx = (int) Math.floor((cp.x() - vw / 2F) / cs); cx <= (int) Math.floor((cp.x() + vw / 2F) / cs); cx++)
      for (int cy = (int) Math.floor((cp.y() - vh / 2F) / cs); cy <= (int) Math.floor((cp.y() + vh / 2F) / cs); cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getOrLoadChunk(pos);
        ChunkMesh cm = ensureFrontMesh(level, pos);
        g.drawMesh(cm.body());
        k++;
        sc += cm.body().sections().size();

        for (int ly = 0; ly < cs; ly++)
          for (int lx = 0; lx < cs; lx++) {
            BlockState s = ck.getBlock(lx, ly);
            if (!s.isAnimatedRendering()) continue;
            Material m = materialOf(s.block());
            drawBody(g, level, cx * cs + lx, cy * cs + ly, m, isSlope(s));
          }
      }
    System.out.println("Meshes: " + k + ", Sections: " + sc);

    // pass 2: all borders on top, so a neighbouring chunk's body never
    // occludes this chunk's border at chunk boundaries
    for (int cx = (int) Math.floor((cp.x() - vw / 2F) / cs); cx <= (int) Math.floor((cp.x() + vw / 2F) / cs); cx++)
      for (int cy = (int) Math.floor((cp.y() - vh / 2F) / cs); cy <= (int) Math.floor((cp.y() + vh / 2F) / cs); cy++) {
        ChunkPos pos = new ChunkPos(cx, cy);
        Chunk ck = level.getOrLoadChunk(pos);
        ChunkMesh cm = meshes.get(pos.asLong());
        if (cm == null) continue;
        g.drawMesh(cm.border());
        for (int ly = 0; ly < cs; ly++)
          for (int lx = 0; lx < cs; lx++) {
            BlockState s = ck.getBlock(lx, ly);
            if (s == null || s.block() == Registries.AIR || !s.isAnimatedRendering()) continue;
            Material m = materialOf(s.block());
            if (!isSlope(s)) drawEdges(g, level, cx * cs + lx, cy * cs + ly, m, false, Level::getBlock);
          }
      }
  }

  private static boolean isSlope(BlockState s) {
    return s.block() == Registries.SLOPE_RIGHT || s.block() == Registries.SLOPE_LEFT;
  }

  /** Returns the retained block mesh of a chunk, rebuilding it when the
   * block layer is dirty. */
  private ChunkMesh ensureFrontMesh(Level level, ChunkPos pos) {
    Chunk ck = level.getOrLoadChunk(pos);
    ChunkMesh cm = meshes.get(pos.asLong());
    if (cm == null || ck.frontDirty) {
      if (cm != null) cm.close();
      cm = buildChunkMesh(level, pos);
      meshes.put(pos.asLong(), cm);
      ck.frontDirty = false;
    }
    return cm;
  }

  /** Returns the retained wall mesh of a chunk, rebuilding it when the
   * wall layer is dirty. */
  private ChunkMesh ensureBackMesh(Level level, ChunkPos pos) {
    Chunk ck = level.getOrLoadChunk(pos);
    ChunkMesh cm = wallMeshes.get(pos.asLong());
    if (cm == null || ck.backDirty) {
      if (cm != null) cm.close();
      cm = buildWallMesh(level, pos);
      wallMeshes.put(pos.asLong(), cm);
      ck.backDirty = false;
    }
    return cm;
  }

  /** Builds the retained block mesh of a chunk: bodies then borders
   * (Enchant ChunkMesh), skipping animated blocks. */
  private ChunkMesh buildChunkMesh(Level level, ChunkPos pos) {
    Chunk chunk = level.getOrLoadChunk(pos);
    int cs = ChunkPos.SIZE;
    int x0 = pos.x() * cs;
    int y0 = pos.y() * cs;

    meshG.begin();
    meshG.setColor(Color.WHITE);
    for (int ly = 0; ly < cs; ly++)
      for (int lx = 0; lx < cs; lx++) {
        BlockState s = chunk.getBlock(lx, ly);
        if (s == null || s.block() == Registries.AIR || s.isAnimatedRendering()) continue;
        Material m = materialOf(s.block());
        drawBody(meshG, level, x0 + lx, y0 + ly, m, isSlope(s));
      }
    Mesh body = meshG.bake(dev);
    meshG.end();

    meshG.begin();
    meshG.setColor(Color.WHITE);
    for (int ly = 0; ly < cs; ly++)
      for (int lx = 0; lx < cs; lx++) {
        BlockState s = chunk.getBlock(lx, ly);
        if (s == null || s.block() == Registries.AIR || s.isAnimatedRendering()) continue;
        Material m = materialOf(s.block());
        drawEdges(meshG, level, x0 + lx, y0 + ly, m, isSlope(s), Level::getBlock);
      }
    Mesh border = meshG.bake(dev);
    meshG.end();

    return new ChunkMesh(body, border);
  }

  /** Builds the retained wall mesh of a chunk: the same body + border
   * logic as blocks, queried from the wall layer. */
  private ChunkMesh buildWallMesh(Level level, ChunkPos pos) {
    Chunk chunk = level.getOrLoadChunk(pos);
    int cs = ChunkPos.SIZE;
    int x0 = pos.x() * cs;
    int y0 = pos.y() * cs;

    meshG.begin();
    meshG.setColor(Color.WHITE);
    for (int ly = 0; ly < cs; ly++)
      for (int lx = 0; lx < cs; lx++) {
        BlockState w = chunk.getWall(lx, ly);
        if (w == null || w.block() == Registries.AIR) continue;
        drawBody(meshG, level, x0 + lx, y0 + ly, materialOf(w.block()), false);
      }
    Mesh body = meshG.bake(dev);
    meshG.end();

    meshG.begin();
    meshG.setColor(Color.WHITE);
    for (int ly = 0; ly < cs; ly++)
      for (int lx = 0; lx < cs; lx++) {
        BlockState w = chunk.getWall(lx, ly);
        if (w == null || w.block() == Registries.AIR) continue;
        drawEdges(meshG, level, x0 + lx, y0 + ly, materialOf(w.block()), false, Level::getWall);
      }
    Mesh border = meshG.bake(dev);
    meshG.end();

    return new ChunkMesh(body, border);
  }

  /**
   * Renders the wall layer from the retained meshes, in the wall render
   * pass. All wall bodies first, then all borders (same chunk-boundary
   * rule as blocks).
   */
  public void renderWalls(BatchedGraphics2D g, Level level, Camera2D cam) {
    var cp = cam.center();
    float vw = cam.width() / cam.zoom();
    float vh = cam.height() / cam.zoom();
    int cs = ChunkPos.SIZE;
    g.setColor(Color.WHITE);
    for (int cx = (int) Math.floor((cp.x() - vw / 2F) / cs); cx <= (int) Math.floor((cp.x() + vw / 2F) / cs); cx++)
      for (int cy = (int) Math.floor((cp.y() - vh / 2F) / cs); cy <= (int) Math.floor((cp.y() + vh / 2F) / cs); cy++) {
        ChunkMesh cm = ensureBackMesh(level, new ChunkPos(cx, cy));
        g.drawMesh(cm.body());
      }
    for (int cx = (int) Math.floor((cp.x() - vw / 2F) / cs); cx <= (int) Math.floor((cp.x() + vw / 2F) / cs); cx++)
      for (int cy = (int) Math.floor((cp.y() - vh / 2F) / cs); cy <= (int) Math.floor((cp.y() + vh / 2F) / cs); cy++) {
        ChunkMesh cm = wallMeshes.get(new ChunkPos(cx, cy).asLong());
        if (cm != null) g.drawMesh(cm.border());
      }
  }

  private void drawBody(BatchedGraphics2D g, Level level, int x, int y, Material m, boolean slope) {
    if (slope) {
      // the slope face replaces the body
      BlockState s = level.getBlock(x, y);
      if (s.block() == Registries.SLOPE_RIGHT)
        g.drawTexture(sheet(m), x, y, 1F, 1F, SLOPE_LEFT_DOWN_X, SLOPE_LEFT_DOWN_Y, CELL, CELL);
      else
        g.drawTexture(sheet(m), x, y, 1F, 1F, SLOPE_RIGHT_DOWN_X, SLOPE_RIGHT_DOWN_Y, CELL, CELL);
      return;
    }
    long h = placeHash(x, y);
    // sample an 8x8 cell from the 64x64 full texture
    int u = FULL_X + (int) (Math.abs(h) & 3) * CELL;
    int v = FULL_Y + (int) ((Math.abs(h) >> 3) & 3) * CELL;
    g.drawTexture(sheet(m), x, y, 1F, 1F, u, v, CELL, CELL);
  }

  /** Tile lookup used by the shared edge logic: the block layer or the
   * wall layer. */
  @FunctionalInterface
  private interface BlockLookup {
    BlockState get(Level level, int x, int y);
  }

  /**
   * Enchant BlockRenderBuffer.MakeBorderData, translated verbatim (data
   * untouched; the UVs target the material sheet and are expected to be
   * adjusted for the actual texture layout). The layer is queried through
   * {@code lookup} so blocks and walls share the same border logic.
   */
  private void drawEdges(BatchedGraphics2D g, Level level, int x, int y, Material m, boolean slope,
                         BlockLookup lookup) {
    BlockState self = lookup.get(level, x, y);
    boolean selfPv = isPerfectVoxel(self);
    // slopes are PARTIAL (liquids fill the gaps) but still draw edges on
    // their two flat faces
    if (!selfPv && !slope) return;

    // slope: only the two flat faces get edges — the bottom and the
    // vertical side (↗ SLOPE_RIGHT: down+right; ↖ SLOPE_LEFT: down+left).
    // The slanted face has none.
    boolean edgeLeft = !slope || self.block() == Registries.SLOPE_LEFT;
    boolean edgeUp = !slope;
    boolean edgeRight = !slope || self.block() == Registries.SLOPE_RIGHT;

    BlockState upBlock = lookup.get(level, x, y + 1);
    BlockState downBlock = lookup.get(level, x, y - 1);
    BlockState leftBlock = lookup.get(level, x - 1, y);
    BlockState rightBlock = lookup.get(level, x + 1, y);
    BlockState leftupBlock = lookup.get(level, x - 1, y + 1);
    BlockState leftdownBlock = lookup.get(level, x - 1, y - 1);
    BlockState rightupBlock = lookup.get(level, x + 1, y + 1);
    BlockState rightdownBlock = lookup.get(level, x + 1, y - 1);

    boolean up = connectable(upBlock, self);
    boolean down = connectable(downBlock, self);
    boolean left = connectable(leftBlock, self);
    boolean right = connectable(rightBlock, self);
    boolean upl = connectable(leftupBlock, self);
    boolean downl = connectable(leftdownBlock, self);
    boolean upr = connectable(rightupBlock, self);
    boolean downr = connectable(rightdownBlock, self);

    long hash = placeHash(x, y);
    int rdu = (int) (Math.abs(hash) % 4) * 13;
    int rdu2 = (int) (Math.abs(hash + 1) % 4) * 9;

    int pid = self.block().registryIndex();

    final float Od = 0.002F;
    final float Od4 = Od * 4;

    TexturePart tex = sheet(m);

    if (edgeLeft && !left && (leftBlock.block().registryIndex() <= pid || !drawsOwnEdges(leftBlock, DIR_RIGHT))) {
      //0 0 0
      //0 1 0
      //? 0 0
      if (downl)
        g.drawTexture(tex, x - 0.5F - Od, y - Od, 0.5F + Od4, 0.5F + Od4, rdu2 + 37, 17, 4, 4);
      else
        g.drawTexture(tex, x - 0.25F - Od, y - Od, 0.25F + Od4, 0.5F + Od4, rdu + 33, 6, 2, 4);

      //? 0 0
      //0 1 0
      //0 0 0
      if (upl)
        g.drawTexture(tex, x - 0.5F - Od, y + 0.5F + Od, 0.5F + Od4, 0.5F + Od4, rdu2 + 37, 13, 4, 4);
      else
        g.drawTexture(tex, x - 0.25F - Od, y + 0.5F + Od, 0.25F + Od4, 0.5F + Od4, rdu + 33, 2, 2, 4);
    }

    if (edgeUp && !up && (upBlock.block().registryIndex() <= pid || !drawsOwnEdges(upBlock, DIR_DOWN))) {
      //? 0 0
      //0 1 0
      //0 0 0
      if (upl)
        g.drawTexture(tex, x - Od, y + 1 - Od, 0.5F + Od4, 0.5F + Od4, rdu2 + 33, 17, 4, 4);
      else
        g.drawTexture(tex, x - Od, y + 1 - Od, 0.5F + Od4, 0.25F + Od4, rdu + 35, 0, 4, 2);

      //0 0 ?
      //0 1 0
      //0 0 0
      if (!upr)
        g.drawTexture(tex, x + 0.5F + Od, y + 1 - Od, 0.5F + Od4, 0.25F + Od4, rdu + 39, 0, 4, 2);
    }

    if (edgeRight && !right && (rightBlock.block().registryIndex() <= pid || !drawsOwnEdges(rightBlock, DIR_LEFT))) {
      //0 0 0
      //0 1 0
      //0 0 ?
      if (!downr)
        g.drawTexture(tex, x + 1 - Od, y - Od, 0.25F + Od4, 0.5F + Od4, rdu + 43, 6, 2, 4);

      //0 0 ?
      //0 1 0
      //0 0 0
      if (upr)
        g.drawTexture(tex, x + 1 - Od, y + 0.5F + Od, 0.5F + Od4, 0.5F + Od4, rdu2 + 33, 13, 4, 4);
      else
        g.drawTexture(tex, x + 1 - Od, y + 0.5F + Od, 0.25F + Od4, 0.5F + Od4, rdu + 43, 2, 2, 4);
    }

    if (!down && (downBlock.block().registryIndex() <= pid || !drawsOwnEdges(downBlock, DIR_UP))) {
      //0 0 0
      //0 1 0
      //? 0 0
      if (!downl)
        g.drawTexture(tex, x - Od, y - 0.25F - Od, 0.5F + Od4, 0.25F + Od4, rdu + 35, 10, 4, 2);

      //0 0 0
      //0 1 0
      //0 0 ?
      if (!downr)
        g.drawTexture(tex, x + 0.5F + Od, y - 0.25F - Od, 0.5F + Od4, 0.25F + Od4, rdu + 39, 10, 4, 2);
    }
  }
}
