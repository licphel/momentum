package net.fmhi.world.light;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.mesh.BatchedGraphics2D;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.TextureFilter;
import net.fmhi.gfx.texture.TextureWrap;
import net.fmhi.math.Box2D;
import net.fmhi.math.Color;
import net.fmhi.math.Vector2;
import net.fmhi.util.Profiler;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.ChunkCache;
import net.fmhi.world.level.Level;
import net.fmhi.world.util.ChunkPos;

/**
 * Base class for engines that maintain a sliding window of per-tile light
 * data.
 *
 * <p>Each tile stores 19 floats: raw RGB, four ambient-occlusion factors and
 * twelve per-vertex RGB values. The window is double-buffered so the renderer
 * always reads a complete frame while the next one is computed.
 *
 * <p>The window size adapts to the camera: it covers the visible tiles plus a
 * light-travel margin. Resizing copies the overlap of the old buffers into the
 * new ones so the renderer never sees a blank window.
 *
 * <p>Lightmaps (wall + front) are rendered from the front buffer; the wall
 * lightmap bakes in {@link #WALL_MULTIPLIER}.
 */
public abstract class LightEngine implements AutoCloseable {
  /** Multiplier applied to light values when seeding and drawing. */
  public static final float AMPLIFIER = 1.35F;
  /** Luminance below which light is treated as darkness. */
  public static final float DARK_LUMINANCE = 0.05F;
  /** Number of floats per tile: raw RGB, AO factors and per-vertex RGB. */
  public static final int STRIDE = 19;
  /** Dimming factor applied to light on walls. */
  public static final float WALL_MULTIPLIER = 0.75F;
  /** Darkening applied per solid neighbor when computing ambient occlusion. */
  public static final float AO_STRENGTH = 0.08F;
  /** Maximum light value in normalized units. */
  public static final float MAX_VALUE_GENERAL = 1F;
  /** Light level of a single discrete step of {@link #MAX_VALUE_GENERAL}. */
  public static final float UNIT = MAX_VALUE_GENERAL / 16F;
  /** Tiles of light travel (ln(0.05)/ln(0.92) ≈ 36) plus slack. */
  protected static final int SPREAD_MARGIN = 40;
  /** Smallest allowed window side, in tiles. */
  protected static final int MIN_SIZE = 64;
  /** Largest allowed window side, in tiles. */
  protected static final int MAX_SIZE = 2048;

  /** Sunlight color used to seed sky illumination. */
  public final LightBuffer sunlight = new SimpleLightBuffer();
  protected final Level level;
  /** Completed buffer, read by the renderer. */
  protected volatile float[] front;
  /** Buffer being computed by the worker thread. */
  protected volatile float[] back;
  /** Current window size in tiles. */
  protected volatile int sizeX = MIN_SIZE;
  protected volatile int sizeY = MIN_SIZE;
  protected int oriX;
  protected int oriY;
  /** Window origin of the current front buffer. */
  protected int frontOriX;
  protected int frontOriY;
  protected volatile boolean done;
  protected Thread worker;
  protected ChunkCache cc;
  /** Pending window resize, applied when the worker is idle. */
  private int pendingW = -1, pendingH = -1;
  private RenderTarget wallLightRT;
  private RenderTarget frontLightRT;
  private Sampler lmSampler;

  /**
   * Creates a light engine for the given level, allocating its initial front
   * and back buffers.
   *
   * @param level the level to light
   */
  protected LightEngine(Level level) {
    this.level = level;
    int len = sizeX * sizeY * STRIDE;
    this.front = new float[len];
    this.back = new float[len];
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  /**
   * Returns the red component of a per-vertex light value in a raw tile
   * buffer.
   *
   * @param data the tile buffer
   * @param off  the tile offset
   * @param c    the vertex index in {@code 0..3}
   * @return the red value of the vertex
   */
  public static float r(float[] data, int off, int c) {
    return data[off + 7 + c * 3];
  }

  /**
   * Returns the green component of a per-vertex light value in a raw tile
   * buffer.
   *
   * @param data the tile buffer
   * @param off  the tile offset
   * @param c    the vertex index in {@code 0..3}
   * @return the green value of the vertex
   */
  public static float g(float[] data, int off, int c) {
    return data[off + 8 + c * 3];
  }

  /**
   * Returns the blue component of a per-vertex light value in a raw tile
   * buffer.
   *
   * @param data the tile buffer
   * @param off  the tile offset
   * @param c    the vertex index in {@code 0..3}
   * @return the blue value of the vertex
   */
  public static float b(float[] data, int off, int c) {
    return data[off + 9 + c * 3];
  }

  /**
   * Writes the sky light falling on a tile into the given buffer. At or above
   * a height of 60 tiles no sky light is emitted.
   *
   * @param skyLight the light of the sky above the tile
   * @param x        the tile X coordinate
   * @param y        the tile Y coordinate
   * @param out      the buffer to receive the sky light
   */
  public static void skyEmit(LightBuffer skyLight, int x, int y, LightBuffer out) {
    if (y >= 60) {
      out.r(0);
      out.g(0);
      out.b(0);
      return;
    }
    out.copy(skyLight);
  }

  /**
   * Seeds the raw RGB channels of a tile from its block light and sky light,
   * keeping the brighter of the two per channel.
   *
   * @param buf       the tile buffer
   * @param off       the tile offset
   * @param block     the block at the tile
   * @param wall      the wall at the tile
   * @param skyLight  the sky light falling on the tile
   * @param x         the tile X coordinate
   * @param y         the tile Y coordinate
   * @param lb        a reusable buffer for intermediate light values
   * @param amplifier the multiplier applied to seeded light
   */
  public static void seed(float[] buf, int off,
                          BlockState block, BlockState wall, LightBuffer skyLight,
                          int x, int y, LightBuffer lb, float amplifier) {
    float br = 0, bg = 0, bb = 0;
    if (block.block().getLight(block, lb)) {
      br = lb.r() * amplifier;
      bg = lb.g() * amplifier;
      bb = lb.b() * amplifier;
    }
    LightBuffer gb = new SimpleLightBuffer();
    LightBuffer wb = new SimpleLightBuffer();
    skyEmit(skyLight, x, y, gb);
    wall.block().getLight(wall, wb);
    wall.block().filterLight(wall, gb);
    gb.max(wb);

    buf[off] = Math.max(br, gb.r() * amplifier);
    buf[off + 1] = Math.max(bg, gb.g() * amplifier);
    buf[off + 2] = Math.max(bb, gb.b() * amplifier);
  }

  /**
   * Computes the ambient-occlusion factors of a tile from the solidity of its
   * neighbors and stores them in the tile's AO slots.
   *
   * @param cc   the chunk cache for neighbor lookups
   * @param data the tile buffer
   * @param off  the tile offset
   * @param x    the tile X coordinate
   * @param y    the tile Y coordinate
   */
  private static void populateAO(ChunkCache cc, float[] data, int off, int x, int y) {
    if (cc.isFrontSolid(x, y)) {
      float v = 1F - AO_STRENGTH * 1.5F;
      data[off + 3] = v;
      data[off + 4] = v;
      data[off + 5] = v;
      data[off + 6] = v;
    } else {
      boolean s0 = cc.isFrontSolid(x - 1, y - 1);
      boolean s1 = cc.isFrontSolid(x - 1, y);
      boolean s2 = cc.isFrontSolid(x - 1, y + 1);
      boolean s3 = cc.isFrontSolid(x, y - 1);
      boolean s4 = cc.isFrontSolid(x, y + 1);
      boolean s5 = cc.isFrontSolid(x + 1, y - 1);
      boolean s6 = cc.isFrontSolid(x + 1, y);
      boolean s7 = cc.isFrontSolid(x + 1, y + 1);
      data[off + 3] = 1F - ((s0 ? 1 : 0) + (s1 ? 1 : 0) + (s3 ? 1 : 0)) * AO_STRENGTH;
      data[off + 4] = 1F - ((s1 ? 1 : 0) + (s2 ? 1 : 0) + (s4 ? 1 : 0)) * AO_STRENGTH;
      data[off + 5] = 1F - ((s4 ? 1 : 0) + (s6 ? 1 : 0) + (s7 ? 1 : 0)) * AO_STRENGTH;
      data[off + 6] = 1F - ((s3 ? 1 : 0) + (s5 ? 1 : 0) + (s6 ? 1 : 0)) * AO_STRENGTH;
    }
  }

  /**
   * Returns the current window width in tiles.
   *
   * @return the window width
   */
  public int sizeX() {
    return sizeX;
  }

  /**
   * Returns the current window height in tiles.
   *
   * @return the window height
   */
  public int sizeY() {
    return sizeY;
  }

  /**
   * Requests a window resize, rounded up to a multiple of 16 and clamped to
   * {@link #MIN_SIZE}..{@link #MAX_SIZE}. Applied by
   * {@link #applyPendingResize()} while the worker is idle.
   *
   * @param w the desired window width in tiles
   * @param h the desired window height in tiles
   */
  protected void requestSize(int w, int h) {
    int nw = clamp((w + 15) & ~15, MIN_SIZE, MAX_SIZE);
    int nh = clamp((h + 15) & ~15, MIN_SIZE, MAX_SIZE);
    if (nw != sizeX || nh != sizeY) {
      pendingW = nw;
      pendingH = nh;
    }
  }

  /**
   * Applies a pending resize, copying the overlapping region of the old
   * buffers so no part of the visible window becomes blank.
   *
   * @return {@code true} if a resize was applied
   */
  protected boolean applyPendingResize() {
    if (pendingW <= 0) {
      return false;
    }
    int oldW = sizeX, oldH = sizeY;
    float[] oldFront = front, oldBack = back;
    int oldFOX = frontOriX, oldFOY = frontOriY;
    sizeX = pendingW;
    sizeY = pendingH;
    pendingW = pendingH = -1;
    front = new float[sizeX * sizeY * STRIDE];
    back = new float[sizeX * sizeY * STRIDE];
    copyOverlap(oldFront, oldW, oldH, oldFOX, oldFOY, front, frontOriX, frontOriY);
    copyOverlap(oldBack, oldW, oldH, oriX, oriY, back, oriX, oriY);
    return true;
  }

  /**
   * Copies the overlapping region of two windows into the destination buffer.
   *
   * @param src the source buffer
   * @param sw  the source window width in tiles
   * @param sh  the source window height in tiles
   * @param sox the source window origin X
   * @param soy the source window origin Y
   * @param dst the destination buffer
   * @param dox the destination window origin X
   * @param doy the destination window origin Y
   */
  private void copyOverlap(float[] src, int sw, int sh, int sox, int soy,
                           float[] dst, int dox, int doy) {
    int x0 = Math.max(sox, dox);
    int x1 = Math.min(sox + sw, dox + sizeX);
    int y0 = Math.max(soy, doy);
    int y1 = Math.min(soy + sh, doy + sizeY);
    for (int y = y0; y < y1; y++) {
      int sRow = ((y - soy) * sw + (x0 - sox)) * STRIDE;
      int dRow = ((y - doy) * sizeX + (x0 - dox)) * STRIDE;
      for (int x = x0; x < x1; x++) {
        System.arraycopy(src, sRow, dst, dRow, STRIDE);
        sRow += STRIDE;
        dRow += STRIDE;
      }
    }
  }

  /**
   * Swaps the front and back buffers, making the back buffer the current one
   * and updating the front window origin.
   */
  protected void swap() {
    float[] tmp = front;
    front = back;
    back = tmp;
    frontOriX = oriX;
    frontOriY = oriY;
    done = false;
  }

  /**
   * Creates the two off-screen lightmap render targets and their shared
   * sampler.
   *
   * @param dev    the graphics device
   * @param width  the lightmap width in pixels
   * @param height the lightmap height in pixels
   */
  public void initLightmaps(Device dev, int width, int height) {
    wallLightRT = dev.getRenderTarget(RenderTargetDesc.offscreen(width, height));
    frontLightRT = dev.getRenderTarget(RenderTargetDesc.offscreen(width, height));
    lmSampler = dev.getSampler(new SamplerDesc.Builder()
        .minFilter(TextureFilter.NEAREST)
        .magFilter(TextureFilter.NEAREST)
        .wrapX(TextureWrap.CLAMP_TO_EDGE)
        .wrapY(TextureWrap.CLAMP_TO_EDGE)
        .build());
  }

  /**
   * Returns the render target holding the wall lightmap.
   *
   * @return the wall lightmap target
   */
  public RenderTarget backLightmap() {
    return wallLightRT;
  }

  /**
   * Returns the render target holding the front lightmap.
   *
   * @return the front lightmap target
   */
  public RenderTarget frontLightmap() {
    return frontLightRT;
  }

  /**
   * Returns the sampler used when sampling the lightmaps.
   *
   * @return the lightmap sampler
   */
  public Sampler sampler() {
    return lmSampler;
  }

  /**
   * Adds light to a tile, keeping the brightest value per channel.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @param r the red light to add
   * @param g the green light to add
   * @param b the blue light to add
   */
  public void draw(int x, int y, float r, float g, float b) {
    int o = backBufferIndex(x, y);
    if (o >= 0) {
      back[o] = Math.max(back[o], r * AMPLIFIER);
      back[o + 1] = Math.max(back[o + 1], g * AMPLIFIER);
      back[o + 2] = Math.max(back[o + 2], b * AMPLIFIER);
    }
  }

  /**
   * Adds light from a source at fractional coordinates, spreading it over the
   * surrounding tiles with an inverse-square falloff. Tiles at distance less
   * than one tile receive the full value; sources whose channels are all at
   * or below {@link #DARK_LUMINANCE} are ignored.
   *
   * @param x the light source X coordinate
   * @param y the light source Y coordinate
   * @param r the red light of the source
   * @param g the green light of the source
   * @param b the blue light of the source
   */
  public void drawInterpolated(float x, float y, float r, float g, float b) {
    if (r <= DARK_LUMINANCE && g <= DARK_LUMINANCE && b <= DARK_LUMINANCE) {
      return;
    }
    int lx = (int) Math.floor(x);
    int ly = (int) Math.floor(y);
    for (int tx = lx - 3; tx < lx + 3; tx++) {
      for (int ty = ly - 3; ty < ly + 3; ty++) {
        float d2 = (float) (Math.pow(tx - x, 2) + Math.pow(ty - y, 2));
        if (d2 < 1F) {
          d2 = 1F;
        }
        draw(tx, ty, r / d2, g / d2, b / d2);
      }
    }
  }

  /**
   * Renders the wall and front lightmaps from the front buffer.
   *
   * <p>The wall lightmap covers every wall tile and bakes in
   * {@link #WALL_MULTIPLIER}; the front lightmap covers all tiles at full
   * brightness so blocks, sky and entities always receive light.
   *
   * @param g   the batched renderer to draw with
   * @param cam the camera defining the visible area
   */
  public void generateLightmaps(BatchedGraphics2D g, Camera2D cam) {
    try (Profiler.Scope _ = Profiler.scope("rendering:lightmap")) {
      Vector2 cp = cam.position();
      float vw = cam.width() / cam.zoom();
      float vh = cam.height() / cam.zoom();
      int cs = ChunkPos.SIZE;
      int minCX = (int) Math.floor((cp.x() - vw / 2F) / cs);
      int maxCX = (int) Math.floor((cp.x() + vw / 2F) / cs);
      int minCY = (int) Math.floor((cp.y() - vh / 2F) / cs);
      int maxCY = (int) Math.floor((cp.y() + vh / 2F) / cs);

      // front lightmap: blocks, sky, entities — full brightness
      g.begin(RenderPass.of(frontLightRT, Color.BLACK));
      g.setCamera(cam);
      drawTiles(g, minCX, maxCX, minCY, maxCY, false);
      g.end();

      // wall lightmap: every wall tile — baked WALL_MULTIPLIER
      g.begin(RenderPass.of(wallLightRT, Color.BLACK));
      g.setCamera(cam);
      drawTiles(g, minCX, maxCX, minCY, maxCY, true);
      g.end();
    }
  }

  /**
   * Draws one lightmap layer (front or wall) over the visible chunk range.
   *
   * @param g     the batched renderer to draw with
   * @param minCX the first chunk column
   * @param maxCX the last chunk column
   * @param minCY the first chunk row
   * @param maxCY the last chunk row
   * @param wall  whether to draw the wall layer
   */
  private void drawTiles(BatchedGraphics2D g,
                         int minCX, int maxCX, int minCY, int maxCY, boolean wall) {
    int cs = ChunkPos.SIZE;
    g.drawRectangle(0, 0, 0, 0); // prime primitive

    for (int cx = minCX; cx <= maxCX; cx++) {
      for (int cy = minCY; cy <= maxCY; cy++) {
        Chunk chunk = level.getOrLoadChunk(new ChunkPos(cx, cy));
        for (int lx = 0; lx < cs; lx++) {
          for (int ly = 0; ly < cs; ly++) {
            int wx = cx * cs + lx, wy = cy * cs + ly;
            BlockState block = chunk.getBlock(lx, ly);
            BlockState wallS = chunk.getWall(lx, ly);
            boolean hasWall = wallS != BlockState.EMPTY;
            // wall lightmap: every wall tile — walls are always visible
            // even behind blocks (blocks don't necessarily occlude walls)
            if (wall && !hasWall) {
              continue;
            }
            // front lightmap: ALL tiles — blocks, sky, entities always
            // receive front light; the wall layer is a separate composite
            // so pure-wall tiles aren't double-lit

            int off = bufferIndex(wx, wy);
            if (off < 0) {
              continue;
            }
            float[] buf = buffer();
            float m = wall ? WALL_MULTIPLIER : 1F;
            int base = g.vertexCount();
            g.putPosColor(wx, wy, 0, Color.pack(
                r(buf, off, 0) * m,
                g(buf, off, 0) * m,
                b(buf, off, 0) * m,
                1F));
            g.putPosColor(wx + 1, wy, 0, Color.pack(
                r(buf, off, 1) * m,
                g(buf, off, 1) * m,
                b(buf, off, 1) * m,
                1F));
            g.putPosColor(wx + 1, wy + 1, 0, Color.pack(
                r(buf, off, 2) * m,
                g(buf, off, 2) * m,
                b(buf, off, 2) * m,
                1F));
            g.putPosColor(wx, wy + 1, 0, Color.pack(
                r(buf, off, 3) * m,
                g(buf, off, 3) * m,
                b(buf, off, 3) * m,
                1F));
            g.putQuadIndices(base);
            g.addVertex(4);
            g.addIndex(6);
          }
        }
      }
    }
  }

  /**
   * Computes the per-tile auxiliary values of a tile: its ambient-occlusion
   * factors and the smoothed per-vertex light colors.
   *
   * @param cc   the chunk cache for neighbor lookups
   * @param data the tile buffer
   * @param off  the tile offset
   * @param x    the tile X coordinate
   * @param y    the tile Y coordinate
   */
  public void populate(ChunkCache cc, float[] data, int off, int x, int y) {
    populateAO(cc, data, off, x, y);
    populateSmoothedLightVerticesByChannel(data, off, Channel.RED, x, y);
    populateSmoothedLightVerticesByChannel(data, off, Channel.GREEN, x, y);
    populateSmoothedLightVerticesByChannel(data, off, Channel.BLUE, x, y);
  }

  /**
   * Smoothes one channel of a tile's light against its neighbors and writes
   * the four per-vertex values, darkened by the tile's ambient occlusion.
   *
   * @param data    the tile buffer
   * @param off     the tile offset
   * @param channel the color channel to populate
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   */
  private void populateSmoothedLightVerticesByChannel(
      float[] data, int off, byte channel, int x, int y) {
    float l1 = getChannelValue(x - 1, y, channel);
    float l2 = getChannelValue(x + 1, y, channel);
    float l3 = getChannelValue(x, y - 1, channel);
    float l4 = getChannelValue(x, y + 1, channel);
    float l5 = getChannelValue(x - 1, y - 1, channel);
    float l6 = getChannelValue(x + 1, y + 1, channel);
    float l7 = getChannelValue(x - 1, y + 1, channel);
    float l8 = getChannelValue(x + 1, y - 1, channel);
    float l0 = data[off + channel];
    float v0 = (l0 + l1 + l3 + l5) / 4F;
    float v1 = (l0 + l1 + l4 + l7) / 4F;
    float v2 = (l0 + l2 + l4 + l6) / 4F;
    float v3 = (l0 + l2 + l3 + l8) / 4F;
    float aoTL = data[off + 3];
    float aoBL = data[off + 4];
    float aoBR = data[off + 5];
    float aoTR = data[off + 6];
    data[off + channel + 7] = v0 * aoTL;
    data[off + channel + 10] = v3 * aoTR;
    data[off + channel + 13] = v2 * aoBR;
    data[off + channel + 16] = v1 * aoBL;
  }

  /**
   * Rebuilds the light window around the given camera bounds.
   *
   * @param cam the camera bounds to cover
   */
  public void tick(Box2D cam) {
    // adapt window to the visible area + spread margin
    requestSize((int) Math.ceil(cam.width()) + 2 * SPREAD_MARGIN + 4,
        (int) Math.ceil(cam.height()) + 2 * SPREAD_MARGIN + 4);

    if (worker != null && worker.isAlive()) {
      return;
    }
    if (done || worker == null) {
      // resize only while idle. the worker must not see the array change
      if (applyPendingResize()) {
        onResized();
      }
      swap();
      worker = Thread.ofVirtual().start(() -> {
        calculate(cam);
        done = true;
      });
    }
    oriX = (int) Math.floor(cam.centralX()) - sizeX / 2;
    oriY = (int) Math.floor(cam.centralY()) - sizeY / 2;

    // TODO: sunlight variance
    sunlight.r(1F);
    sunlight.g(1F);
    sunlight.b(1F);
  }

  /* Called on #applyPendingResize returns true. */
  protected void onResized() {
  }

  /**
   * Computes the next frame into the back buffer.
   *
   * @param cam the camera bounds to compute light for
   */
  protected abstract void calculate(Box2D cam);

  /**
   * Returns the light value of one channel of a tile.
   *
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   * @param channel the color channel to read
   * @return the channel light value, or zero if the tile is outside the
   * window
   */
  protected float getChannelValue(int x, int y, int channel) {
    int o = backBufferIndex(x, y);
    return o < 0 ? 0F : back[o + channel];
  }

  /**
   * Returns the front buffer, holding the last completed light computation.
   *
   * @return the front buffer
   */
  public float[] buffer() {
    return front;
  }

  /**
   * Returns the offset of a tile in the front buffer.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @return the tile offset, or -1 if the tile is outside the window
   */
  public int bufferIndex(int x, int y) {
    x -= frontOriX;
    y -= frontOriY;
    if (x < 0 || x >= sizeX || y < 0 || y >= sizeY) {
      return -1;
    }
    return (x + y * sizeX) * STRIDE;
  }

  /**
   * Returns the buffer offset of a tile in the working window, or -1 if the
   * tile is outside it.
   */
  protected int backBufferIndex(int x, int y) {
    x -= oriX;
    y -= oriY;
    if (x < 0 || x >= sizeX || y < 0 || y >= sizeY) {
      return -1;
    }
    return (x + y * sizeX) * STRIDE;
  }

  @Override
  public void close() {
    if (wallLightRT != null) {
      wallLightRT.close();
    }
    if (frontLightRT != null) {
      frontLightRT.close();
    }
    if (lmSampler != null) {
      lmSampler.close();
    }
  }
}
