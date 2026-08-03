package net.fmhi.world.light;

import net.fmhi.gfx.Device;
import net.fmhi.gfx.brush.tint.QuadGradient;
import net.fmhi.gfx.math.Camera2D;
import net.fmhi.gfx.brush.BatchedGraphics2D;
import net.fmhi.gfx.pass.RenderPass;
import net.fmhi.gfx.pass.RenderTarget;
import net.fmhi.gfx.pass.RenderTargetDesc;
import net.fmhi.gfx.texture.Sampler;
import net.fmhi.gfx.texture.SamplerDesc;
import net.fmhi.gfx.texture.TextureFilter;
import net.fmhi.gfx.texture.TextureWrap;
import net.fmhi.math.Box2D;
import net.fmhi.math.Color;
import net.fmhi.math.FastTrigonometric;
import net.fmhi.math.Vector2;
import net.fmhi.util.Profiler;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.Liquid;
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
  public static final float WALL_MULTIPLIER = 0.7F;
  /** Darkening applied per solid neighbor when computing ambient occlusion. */
  public static final float AO_STRENGTH = 0.12F;
  /** Maximum light value in normalized units. */
  public static final float MAX_VALUE_GENERAL = 1F;
  /** Light level of a single discrete step of {@link #MAX_VALUE_GENERAL}. */
  public static final float UNIT = MAX_VALUE_GENERAL / 16F;
  /** Tiles of light travel (ln(0.05)/ln(0.92) ≈ 36) plus slack. */
  protected static final int SPREAD_MARGIN = 40;
  /** Smallest allowed window side, in tiles. */
  protected static final int MIN_SIZE = 64;
  /** Largest allowed window side, in tiles. */
  protected static final int MAX_SIZE = 512;
  /** Sunlight gradient used to seed sky illumination; injected externally each tick. */
  public final LightBuffer sunlight = SimpleLightBuffer.pooled();
  protected final Level level;
  /** Completed buffer, read by the renderer. */
  protected volatile float[] front;
  /** Buffer being computed by the worker thread. */
  protected volatile float[] back;
  /** Current window size in tiles. */
  protected volatile int sizeX = MIN_SIZE;
  protected volatile int sizeY = MIN_SIZE;
  protected volatile int oriX;
  protected volatile int oriY;
  /** Window origin of the current front buffer. */
  protected volatile int frontOriX;
  protected volatile int frontOriY;
  protected volatile boolean done;
  protected Thread worker;
  protected ChunkCache cc;
  /** How light values combine everywhere (seed, draw, beam merge, spread). */
  protected CompositionFormula formula = CompositionFormula.MAX;
  /** Pending window resize, applied when the worker is idle. */
  private int pendingW = -1;
  private int pendingH = -1;
  private RenderTarget wallLightRT;
  private RenderTarget frontLightRT;
  private Sampler lmSampler;
  /** Beam layer: raw RGB per tile, merged into {@code back} after spread. */
  private float[] beamLayer;
  /** Whether to enable lighting; false will make lightmap full bright. */
  public boolean enabled = true;
  /** Debug switch: skip the light computation and draw the lightmaps as
   * plain white rectangles, to isolate the lightmap rendering cost. */
  public boolean fullBright;
  /** Set when world content changed since the last computation; the compute
   * pass is skipped entirely while this is clear. */
  private volatile boolean worldDirty = true;
  /** The sunlight values the last computation was seeded with. */
  private volatile float lastSunR;
  private volatile float lastSunG;
  private volatile float lastSunB;

  /**
   * Creates a light engine for the given level, allocating its initial front,
   * back and beam buffers.
   *
   * @param level the level to light
   */
  protected LightEngine(Level level) {
    this.level = level;
    // seed white until the day phase (CelestialUtil) is injected externally
    sunlight.r(1F);
    sunlight.g(1F);
    sunlight.b(1F);
    int len = sizeX * sizeY * STRIDE;
    this.front = new float[len];
    this.back = new float[len];
    this.beamLayer = new float[len];
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
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
  public void skyEmit(LightBuffer skyLight, int x, int y, LightBuffer out) {
    out.copy(skyLight);
    out.mul(CelestialUtil.backEmissionStrength(level, y));
  }

  /**
   * Sets the composition formula used by every light mix in the engine.
   *
   * @param formula the composition formula to use
   */
  public void setCompositionFormula(CompositionFormula formula) {
    this.formula = formula;
  }

  /**
   * Returns the current composition formula.
   *
   * @return the composition formula
   */
  public CompositionFormula compositionFormula() {
    return formula;
  }

  /**
   * Blends RGB light into the raw channels of the tile at the given offset,
   * using the current composition formula.
   *
   * @param o the tile offset in the working buffer
   * @param r the red light to blend
   * @param g the green light to blend
   * @param b the blue light to blend
   */
  protected void blendWrite(int o, float r, float g, float b) {
    back[o] = formula.blend(back[o], r);
    back[o + 1] = formula.blend(back[o + 1], g);
    back[o + 2] = formula.blend(back[o + 2], b);
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
  public float r(float[] data, int off, int c) {
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
  public float g(float[] data, int off, int c) {
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
  public float b(float[] data, int off, int c) {
    return data[off + 9 + c * 3];
  }

  /**
   * Seeds the raw RGB channels of a tile from its block light and sky light,
   * combining the two per channel, and emits any beam carried by the block.
   *
   * @param cc       the chunk cache
   * @param skyLight the sky light falling on the tile
   * @param x        the tile X coordinate
   * @param y        the tile Y coordinate
   */
  public void seed(ChunkCache cc, LightBuffer skyLight, int x, int y) {
    int o = backBufferIndex(x, y);
    if (o < 0) {
      return;
    }

    LightBuffer A = SimpleLightBuffer.pooled();
    LightBuffer B = SimpleLightBuffer.pooled();
    LightBuffer C = SimpleLightBuffer.pooled();

    BlockState block = cc.getBlock(x, y);
    BlockState wall = cc.getWall(x, y);
    Liquid liq = cc.getLiquid(x, y);
    int liqAmt = cc.getLiquidAmount(x, y);

    float br = 0;
    float bg = 0;
    float bb = 0;

    if (block.getLight(A)) {
      br = A.r() * AMPLIFIER;
      bg = A.g() * AMPLIFIER;
      bb = A.b() * AMPLIFIER;
    }
    Beam[] beams;
    if ((beams = block.getBeam()) != null) {
      for (Beam b : beams) {
        drawBeam(x + 0.5F, y + 0.5F, b);
      }
    }

    if (liq != null && liq.getLight(A)) {
      br = Math.max(br, A.r() * AMPLIFIER);
      bg = Math.max(bg, A.g() * AMPLIFIER);
      bb = Math.max(bb, A.b() * AMPLIFIER);
    }

    skyEmit(skyLight, x, y, B);
    wall.getLight(C);
    wall.filterSkylight(B);
    B.blend(C, formula);

    blendWrite(o, formula.blend(br, B.r() * AMPLIFIER),
        formula.blend(bg, B.g() * AMPLIFIER),
        formula.blend(bb, B.b() * AMPLIFIER));

    A.recycle();
    B.recycle();
    C.recycle();
  }

  /**
   * Seeds the raw RGB channels of an entity.
   *
   * @param e the entity to seed
   */
  public void seed(Entity e) {
    LightBuffer A = SimpleLightBuffer.pooled();

    if (e.getLight(A)) {
      drawInterpolated(e.center().xf(), e.center().yf(), A.r(), A.g(), A.b());
    }
    Beam[] beams;
    if ((beams = e.getBeam()) != null) {
      for (Beam b : beams) {
        drawBeam(e.center().xf(), e.center().yf(), b);
      }
    }
  }

  /**
   * Gets the filtered channel light from a position.
   *
   * @param cc      the chunk cache
   * @param channel the channel (R/G/B)
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   * @param v       value to filter
   * @return        the filtered value
   */
  public float filter(ChunkCache cc, byte channel, int x, int y, float v) {
    BlockState block = cc.getBlock(x, y);
    Liquid liq = cc.getLiquid(x, y);
    int liqAmt = cc.getLiquidAmount(x, y);

    v = block.filterLight(v, channel);
    if (liq != null) {
      v = liq.filterLight(v, channel, liqAmt);
    }

    return v;
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
    int oldW = sizeX;
    int oldH = sizeY;
    float[] oldFront = front;
    float[] oldBack = back;
    int oldFOX = frontOriX;
    int oldFOY = frontOriY;
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
    int length = (x1 - x0) * STRIDE;
    for (int y = y0; y < y1; y++) {
      int sRow = ((y - soy) * sw + (x0 - sox)) * STRIDE;
      int dRow = ((y - doy) * sizeX + (x0 - dox)) * STRIDE;
      System.arraycopy(src, sRow, dst, dRow, length);
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
   * Combines light into a tile's current value, using the composition formula.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @param r the red light to add
   * @param g the green light to add
   * @param b the blue light to add
   */
  public void draw(int x, int y, float r, float g, float b) {
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }
    int o = backBufferIndex(x, y);
    if (o >= 0) {
      blendWrite(o, r * AMPLIFIER, g * AMPLIFIER, b * AMPLIFIER);
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
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
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
   * Draws a directional beam into the beam layer. Beam light is kept separate
   * from the spread buffer: the cellular spread is isotropic and would wash
   * out the cone. The beam layer is blended into the raw channels after
   * spreading (see {@link #mergeBeam()}), so the cone shape survives.
   *
   * @param x    the light source X coordinate
   * @param y    the light source Y coordinate
   * @param beam the beam to draw
   */
  public void drawBeam(float x, float y, Beam beam) {
    float r = beam.r();
    float g = beam.g();
    float b = beam.b();
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }

    float range = maxIntensity * beam.range();
    float perBlockAir = 1F / beam.range();
    float[] bdsc = FastTrigonometric.sincos(beam.direction());
    float bdx = bdsc[1];
    float bdy = bdsc[0];
    float beamK = beam.strength() / (1F - FastTrigonometric.cos(beam.halfAngle()));
    float ambi = beam.ambience();

    int lx = (int) Math.floor(x);
    int ly = (int) Math.floor(y);
    int radius = (int) Math.ceil(range);
    for (int tx = lx - radius; tx <= lx + radius; tx++) {
      for (int ty = ly - radius; ty <= ly + radius; ty++) {
        float dx = tx + 0.5F - x;
        float dy = ty + 0.5F - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist == 0F) {
          int o0 = backBufferIndex(tx, ty);
          if (o0 >= 0) {
            beamLayer[o0] = Math.max(beamLayer[o0], r * AMPLIFIER);
            beamLayer[o0 + 1] = Math.max(beamLayer[o0 + 1], g * AMPLIFIER);
            beamLayer[o0 + 2] = Math.max(beamLayer[o0 + 2], b * AMPLIFIER);
          }
          continue;
        }

        float attenuation = dist * perBlockAir;
        if (beamK > 0F) {
          float dot = (dx * bdx + dy * bdy) / dist;
          attenuation += (1F - ambi) * Math.min(1F, Math.max(0F, beamK * (1F - dot)));
        }
        if (attenuation >= 1F) {
          continue;
        }
        float remaining = maxIntensity - attenuation;
        if (remaining <= 0F) {
          continue;
        }
        float factor = remaining / maxIntensity;

        int o = backBufferIndex(tx, ty);
        if (o < 0) {
          continue;
        }
        beamLayer[o] = Math.max(beamLayer[o], r * factor * AMPLIFIER);
        beamLayer[o + 1] = Math.max(beamLayer[o + 1], g * factor * AMPLIFIER);
        beamLayer[o + 2] = Math.max(beamLayer[o + 2], b * factor * AMPLIFIER);
      }
    }
  }

  /**
   * Blends the beam layer into the raw channels of {@code back} using the
   * composition formula, then clears it for the next frame. Call after
   * spreading, before populating vertices.
   */
  protected void mergeBeam() {
    if (beamLayer == null) {
      return;
    }
    int len = sizeX * sizeY * STRIDE;
    for (int i = 0; i < len; i += STRIDE) {
      if (beamLayer[i] > 0F || beamLayer[i + 1] > 0F || beamLayer[i + 2] > 0F) {
        blendWrite(i, beamLayer[i], beamLayer[i + 1], beamLayer[i + 2]);
      }
      beamLayer[i] = beamLayer[i + 1] = beamLayer[i + 2] = 0F;
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
      Vector2 cp = cam.center();
      float vw = cam.width() / cam.zoom();
      float vh = cam.height() / cam.zoom();
      int cs = ChunkPos.SIZE;
      int minCX = (int) Math.floor((cp.x() - vw / 2F) / cs);
      int maxCX = (int) Math.floor((cp.x() + vw / 2F) / cs);
      int minCY = (int) Math.floor((cp.y() - vh / 2F) / cs);
      int maxCY = (int) Math.floor((cp.y() + vh / 2F) / cs);

      if (fullBright) {
        // one full-view rectangle per lightmap: no per-tile work at all
        g.begin(RenderPass.of(frontLightRT, Color.BLACK));
        g.setCamera(cam);
        g.setColor(Color.WHITE);
        g.drawRectangle(cp.x() - vw / 2F, cp.y() - vh / 2F, vw, vh);
        g.end();

        g.begin(RenderPass.of(wallLightRT, Color.BLACK));
        g.setCamera(cam);
        g.setColor(Color.WHITE);
        g.drawRectangle(cp.x() - vw / 2F, cp.y() - vh / 2F, vw, vh);
        g.end();
        return;
      }

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
    QuadGradient tg = new QuadGradient();

    for (int cx = minCX; cx <= maxCX; cx++) {
      for (int cy = minCY; cy <= maxCY; cy++) {
        Chunk chunk = level.getOrLoadChunk(new ChunkPos(cx, cy));
        for (int lx = 0; lx < ChunkPos.SIZE; lx++) {
          for (int ly = 0; ly < ChunkPos.SIZE; ly++) {
            int wx = cx * ChunkPos.SIZE + lx;
            int wy = cy * ChunkPos.SIZE + ly;
            BlockState block = chunk.getBlock(lx, ly);
            BlockState wallS = chunk.getWall(lx, ly);
            boolean hasWall = wallS != BlockState.EMPTY;
            float m = wall && hasWall ? WALL_MULTIPLIER : 1F;

            int off = bufferIndex(wx, wy);
            if (off < 0) {
              continue;
            }
            float[] buf = buffer();

            tg.setColors(
                Color.pack(r(buf, off, 0) * m, g(buf, off, 0) * m, b(buf, off, 0) * m, 1F),
                Color.pack(r(buf, off, 1) * m, g(buf, off, 1) * m, b(buf, off, 1) * m, 1F),
                Color.pack(r(buf, off, 2) * m, g(buf, off, 2) * m, b(buf, off, 2) * m, 1F),
                Color.pack(r(buf, off, 3) * m, g(buf, off, 3) * m, b(buf, off, 3) * m, 1F)
            );

            g.setGradient(tg);
            g.drawRectangle(wx, wy, 1, 1);
          }
        }
      }
    }
  }

  /**
   * Computes the ambient-occlusion factors of a tile from the solidity of its
   * neighbors and stores them in the tile's AO slots.
   *
   * @param o  the tile offset in the working buffer
   * @param cc the chunk cache for neighbor lookups
   * @param x  the tile X coordinate
   * @param y  the tile Y coordinate
   */
  protected void populateAO(int o, ChunkCache cc, int x, int y) {
    if (cc.isFrontSolid(x, y)) {
      float v = 1F - AO_STRENGTH * 1.5F;
      back[o + 3] = v;
      back[o + 4] = v;
      back[o + 5] = v;
      back[o + 6] = v;
    } else {
      boolean s0 = cc.isFrontSolid(x - 1, y - 1);
      boolean s1 = cc.isFrontSolid(x - 1, y);
      boolean s2 = cc.isFrontSolid(x - 1, y + 1);
      boolean s3 = cc.isFrontSolid(x, y - 1);
      boolean s4 = cc.isFrontSolid(x, y + 1);
      boolean s5 = cc.isFrontSolid(x + 1, y - 1);
      boolean s6 = cc.isFrontSolid(x + 1, y);
      boolean s7 = cc.isFrontSolid(x + 1, y + 1);
      back[o + 3] = 1F - ((s0 ? 1 : 0) + (s1 ? 1 : 0) + (s3 ? 1 : 0)) * AO_STRENGTH;
      back[o + 4] = 1F - ((s1 ? 1 : 0) + (s2 ? 1 : 0) + (s4 ? 1 : 0)) * AO_STRENGTH;
      back[o + 5] = 1F - ((s4 ? 1 : 0) + (s6 ? 1 : 0) + (s7 ? 1 : 0)) * AO_STRENGTH;
      back[o + 6] = 1F - ((s3 ? 1 : 0) + (s5 ? 1 : 0) + (s6 ? 1 : 0)) * AO_STRENGTH;
    }
  }

  /**
   * Smoothes one channel of a tile's light against its neighbors and writes
   * the four per-vertex values, darkened by the tile's ambient occlusion.
   *
   * @param o       the tile offset in the working buffer
   * @param channel the gradient channel to populate
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   */
  protected void populateSmoothedLightVerticesByChannel(int o, byte channel, int x, int y) {
    if (!enabled) {
      back[o + channel + 7] = 1;
      back[o + channel + 10] = 1;
      back[o + channel + 13] = 1;
      back[o + channel + 16] = 1;
      return;
    }

    float l1 = getChannelValue(x - 1, y, channel);
    float l2 = getChannelValue(x + 1, y, channel);
    float l3 = getChannelValue(x, y - 1, channel);
    float l4 = getChannelValue(x, y + 1, channel);
    float l5 = getChannelValue(x - 1, y - 1, channel);
    float l6 = getChannelValue(x + 1, y + 1, channel);
    float l7 = getChannelValue(x - 1, y + 1, channel);
    float l8 = getChannelValue(x + 1, y - 1, channel);
    float l0 = back[o + channel];
    float v0 = (l0 + l1 + l3 + l5) / 4F;
    float v1 = (l0 + l1 + l4 + l7) / 4F;
    float v2 = (l0 + l2 + l4 + l6) / 4F;
    float v3 = (l0 + l2 + l3 + l8) / 4F;
    float aoTL = back[o + 3];
    float aoBL = back[o + 4];
    float aoBR = back[o + 5];
    float aoTR = back[o + 6];
    back[o + channel + 7] = v0 * aoTL;
    back[o + channel + 10] = v3 * aoTR;
    back[o + channel + 13] = v2 * aoBR;
    back[o + channel + 16] = v1 * aoBL;
  }

  /**
   * Marks the light stale after world content changed (block, wall or
   * liquid edits); the next {@link #tick(Box2D)} recomputes it.
   */
  public void requestRecalc() {
    worldDirty = true;
  }

  /**
   * Rebuilds the light window around the given camera bounds.
   *
   * <p>The full-window computation is skipped while nothing changed: the
   * world is untouched, the sunlight is within the previous values, and the
   * camera is still inside the computed window (the window is the view plus
   * {@link #SPREAD_MARGIN} on every side, so normal movement rides along).
   *
   * @param cam the camera bounds to cover
   */
  public void tick(Box2D cam) {
    if (fullBright) {
      return; // no light data needed in full-bright mode
    }
    // adapt window to the visible area + spread margin
    requestSize((int) Math.ceil(cam.width()) + 2 * SPREAD_MARGIN + 4,
        (int) Math.ceil(cam.height()) + 2 * SPREAD_MARGIN + 4);

    if (worker != null && worker.isAlive()) {
      return;
    }
    if (done || worker == null) {
      boolean skyDirty = Math.abs(sunlight.r() - lastSunR) > 0.01F
          || Math.abs(sunlight.g() - lastSunG) > 0.01F
          || Math.abs(sunlight.b() - lastSunB) > 0.01F;
      boolean camDirty = windowMissesCamera(cam);
      if (!worldDirty && !skyDirty && !camDirty) {
        return; // nothing changed — keep the current front buffer
      }
      lastSunR = sunlight.r();
      lastSunG = sunlight.g();
      lastSunB = sunlight.b();
      // resize only while idle. the worker must not see the array change
      if (applyPendingResize()) {
        onResized();
      }
      swap();
      worker = Thread.ofVirtual().start(() -> {
        calculate(cam);
        worldDirty = false;
        done = true;
      });
    }
    oriX = (int) Math.floor(cam.centralX()) - sizeX / 2;
    oriY = (int) Math.floor(cam.centralY()) - sizeY / 2;
  }

  /**
   * Returns whether the requested window (view + spread margin) no longer
   * fits inside the current light window, i.e. the camera moved so far that
   * the cached light no longer covers it.
   */
  private boolean windowMissesCamera(Box2D cam) {
    int nw = clamp((int) Math.ceil(cam.width()) + 2 * SPREAD_MARGIN + 4, MIN_SIZE, MAX_SIZE);
    int nh = clamp((int) Math.ceil(cam.height()) + 2 * SPREAD_MARGIN + 4, MIN_SIZE, MAX_SIZE);
    int rx = (int) Math.floor(cam.centralX()) - nw / 2;
    int ry = (int) Math.floor(cam.centralY()) - nh / 2;
    return nw != sizeX || nh != sizeY
        || rx < oriX || rx + nw > oriX + sizeX
        || ry < oriY || ry + nh > oriY + sizeY;
  }

  /**
   * Rebuilds engine state that depends on the window size after a resize
   * takes effect.
   */
  protected void onResized() {
    beamLayer = new float[sizeX * sizeY * STRIDE];
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
   * @param channel the gradient channel to read
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
   * Returns the buffer offset of a tile in the working window.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @return the tile offset, or -1 if the tile is outside the window
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
