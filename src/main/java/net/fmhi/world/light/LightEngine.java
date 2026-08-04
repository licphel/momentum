package net.fmhi.world.light;

import net.fmhi.math.Box2D;
import net.fmhi.math.FastTrigonometric;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.Liquid;
import net.fmhi.world.level.ChunkCache;
import net.fmhi.world.level.Level;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Base class for engines that maintain a sliding window of per-tile light data.
 *
 * <p>Each tile stores 19 floats: raw RGB, four ambient-occlusion factors, and twelve
 * per-vertex RGB values. The window is double-buffered so the renderer always reads a
 * complete frame while the next one is computed.
 *
 * <p>The window size adapts to the camera: it covers the visible tiles plus a light-travel
 * margin. Resizing copies the overlap of the old buffers into the new ones so the renderer
 * never sees a blank window.
 *
 * <p>Lightmaps (wall + front) are rendered from the front buffer; the wall lightmap bakes
 * in {@link #WALL_MULTIPLIER}.
 */
public abstract class LightEngine implements AutoCloseable {
  /** Scales light values when they are seeded and drawn. */
  public static final float AMPLIFIER = 1.35F;
  /** Luminance threshold below which light counts as darkness. */
  public static final float DARK_LUMINANCE = 0.05F;
  /** Floats per tile: raw RGB, AO factors, and per-vertex RGB. */
  public static final int STRIDE = 19;
  /** Factor by which light on walls is dimmed. */
  public static final float WALL_MULTIPLIER = 0.7F;
  /** Darkening applied for each solid neighbor during ambient occlusion. */
  public static final float AO_STRENGTH = 0.12F;
  /** Maximum normalized light value. */
  public static final float MAX_VALUE_GENERAL = 1F;
  /** Light level of one discrete step of {@link #MAX_VALUE_GENERAL}. */
  public static final float UNIT = MAX_VALUE_GENERAL / 16F;
  /** Window margin in tiles: light travel distance plus slack. */
  protected static final int SPREAD_MARGIN = 40;
  /** Smallest allowed window side, in tiles. */
  protected static final int MIN_SIZE = 64;
  /** Largest allowed window side, in tiles. */
  protected static final int MAX_SIZE = 512;

  /** Sunlight gradient seeding sky illumination, refreshed externally each tick. */
  public final float[] sunlight = new float[Channel.CHANNELS.length];
  protected final Level level;
  /**
   * Bumped whenever the front buffer changes (recompute or resize), so
   * consumers like {@link LightMapRenderer} can rebuild their caches.
   */
  private final AtomicLong version = new AtomicLong();
  /** Scratch space for one tile's merged ambient light; worker-thread only. */
  private final float[] ambientScratch = new float[3];
  /** When {@code false}, lightmaps render full bright. */
  public boolean enabled = true;
  /** The completed buffer, read by the renderer. */
  protected volatile float[] front;
  /** The buffer currently being computed. */
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
  /**
   * How light values combine everywhere (seed, draw, beam merge, spread).
   * Must stay {@link CompositionFormula#MAX}: the iterative spread passes
   * blend every pass, and an additive formula would inflate the values.
   */
  protected CompositionFormula formula = CompositionFormula.ADDITIVE_CAP;
  /** Pending window resize, applied when the worker is idle. */
  private int pendingW = -1;
  private int pendingH = -1;
  /** Raw RGB per tile for beam light, merged into {@code back} after spreading. */
  private float[] beamLayer;
  /**
   * Set when world content changed since the last computation; the compute
   * pass is skipped while clear.
   */
  private volatile boolean worldDirty = true;
  /** The sunlight values the last computation was seeded with. */
  private volatile float lastSunR;
  private volatile float lastSunG;
  private volatile float lastSunB;
  /** Light worker pool. */
  protected final ExecutorService executor = Executors.newFixedThreadPool(
      Channel.CHANNELS.length,
      Thread.ofVirtual().name("LightWorker-", 0).factory()
  );

  /**
   * Creates a light engine for the given level, allocating its initial front, back, and
   * beam buffers.
   *
   * @param level the level to light
   */
  protected LightEngine(Level level) {
    this.level = level;
    this.cc = new ChunkCache(level); // created once, grown via checkLoss
    int len = sizeX * sizeY * STRIDE;
    this.front = new float[len];
    this.back = new float[len];
    this.beamLayer = new float[len];
  }

  /**
   * Sets how light values are combined throughout the engine.
   *
   * @param formula the composition formula to use
   */
  public void setCompositionFormula(CompositionFormula formula) {
    this.formula = formula;
  }

  /**
   * Returns the composition formula in use.
   *
   * @return the composition formula
   */
  public CompositionFormula compositionFormula() {
    return formula;
  }

  /**
   * Mixes RGB light into the raw channels of the tile at the given offset, using the
   * current composition formula.
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
   * Returns the red component of a per-vertex light value in a raw tile buffer.
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
   * Returns the green component of a per-vertex light value in a raw tile buffer.
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
   * Returns the blue component of a per-vertex light value in a raw tile buffer.
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
   * Computes the merged ambient light of a tile: sky light filtered through the wall,
   * combined per channel with the ambient emission of the block, wall, and liquid via
   * the composition formula.
   *
   * @param cc  the chunk cache
   * @param x   the tile X coordinate
   * @param y   the tile Y coordinate
   * @param out receives the three channel values
   */
  protected void tileAmbient(ChunkCache cc, int x, int y, float[] out) {
    BlockState block = cc.getBlock(x, y);
    BlockState wall = cc.getWall(x, y);
    Liquid liq = cc.getLiquid(x, y);

    float s = CelestialUtil.backEmissionStrength(level, y);
    float r = wall.filterSkylight(x, y, sunlight[0] * s, Channel.RED) * AMPLIFIER;
    float g = wall.filterSkylight(x, y, sunlight[1] * s, Channel.GREEN) * AMPLIFIER;
    float b = wall.filterSkylight(x, y, sunlight[2] * s, Channel.BLUE) * AMPLIFIER;
    r = formula.blend(r, block.emitAmbient(x, y, Channel.RED) * AMPLIFIER);
    g = formula.blend(g, block.emitAmbient(x, y, Channel.GREEN) * AMPLIFIER);
    b = formula.blend(b, block.emitAmbient(x, y, Channel.BLUE) * AMPLIFIER);
    r = formula.blend(r, wall.emitAmbient(x, y, Channel.RED) * AMPLIFIER);
    g = formula.blend(g, wall.emitAmbient(x, y, Channel.GREEN) * AMPLIFIER);
    b = formula.blend(b, wall.emitAmbient(x, y, Channel.BLUE) * AMPLIFIER);
    if (liq != null) {
      int liqAmt = cc.getLiquidAmount(x, y);
      if (liqAmt > 0) {
        r = formula.blend(r, liq.emitAmbient(x, y, liqAmt, Channel.RED) * AMPLIFIER);
        g = formula.blend(g, liq.emitAmbient(x, y, liqAmt, Channel.GREEN) * AMPLIFIER);
        b = formula.blend(b, liq.emitAmbient(x, y, liqAmt, Channel.BLUE) * AMPLIFIER);
      }
    }
    out[0] = r;
    out[1] = g;
    out[2] = b;
  }

  /**
   * Draws every directional beam emitted by the tile into the beam layer and recycles the
   * pooled instances.
   *
   * @param cc the chunk cache
   * @param x  the tile X coordinate
   * @param y  the tile Y coordinate
   */
  protected void tileBeams(ChunkCache cc, int x, int y) {
    for (Beam bm : cc.getBlock(x, y).emitBeams(x, y)) {
      drawBeam(x + 0.5F, y + 0.5F, bm);
      bm.recycle();
    }
    for (Beam bm : cc.getWall(x, y).emitBeams(x, y)) {
      drawBeam(x + 0.5F, y + 0.5F, bm);
      bm.recycle();
    }
    Liquid liq = cc.getLiquid(x, y);
    if (liq != null) {
      int liqAmt = cc.getLiquidAmount(x, y);
      if (liqAmt > 0) {
        for (Beam bm : liq.emitBeams(x, y, liqAmt)) {
          drawBeam(x + 0.5F, y + 0.5F, bm);
          bm.recycle();
        }
      }
    }
  }

  /**
   * Seeds the raw RGB channels of a tile from its merged ambient light, and draws the
   * tile's beams into the beam layer.
   *
   * @param cc the chunk cache
   * @param x  the tile X coordinate
   * @param y  the tile Y coordinate
   */
  public void seed(ChunkCache cc, int x, int y) {
    tileAmbient(cc, x, y, ambientScratch);
    draw(x, y, ambientScratch[0], ambientScratch[1], ambientScratch[2]);
    tileBeams(cc, x, y);
  }

  /**
   * Seeds the light of an entity: its ambient light is drawn interpolated around its
   * position, and its beams into the beam layer.
   *
   * @param e the entity to seed
   */
  public void seed(Entity e) {
    float er = e.emitAmbient(Channel.RED) * AMPLIFIER;
    float eg = e.emitAmbient(Channel.GREEN) * AMPLIFIER;
    float eb = e.emitAmbient(Channel.BLUE) * AMPLIFIER;
    if (er > DARK_LUMINANCE || eg > DARK_LUMINANCE || eb > DARK_LUMINANCE) {
      drawInterpolated(e.center().xf(), e.center().yf(), er, eg, eb);
    }
    for (Beam bm : e.emitBeams()) {
      drawBeam(e.center().xf(), e.center().yf(), bm);
      bm.recycle();
    }
  }

  /**
   * Applies the tile's channel filter to an incoming light value.
   *
   * @param cc      the chunk cache
   * @param channel the channel (R/G/B)
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   * @param v       the value to filter
   * @return the filtered value
   */
  public float filter(ChunkCache cc, byte channel, int x, int y, float v) {
    BlockState block = cc.getBlock(x, y);
    Liquid liq = cc.getLiquid(x, y);
    int liqAmt = cc.getLiquidAmount(x, y);

    v = block.filterLight(x, y, v, channel);
    if (liq != null) {
      v = liq.filterLight(x, y, liqAmt, v, channel);
    }

    return v;
  }

  /**
   * Runs the given function concurrently for every color channel, one task per
   * channel, and waits for all of them to finish.
   *
   * @param fn the per-channel work
   */
  protected void channelDispatch(Consumer<Byte> fn) {
    CountDownLatch latch = new CountDownLatch(Channel.CHANNELS.length);

    for (byte channel : Channel.CHANNELS) {
      executor.submit(() -> {
        try {
          fn.accept(channel);
        } finally {
          latch.countDown();
        }
      });
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Filters all three channels of an incoming light in one chunk-cache pass, in place
   * (the spread hot path — a per-channel filter would triple the cache lookups).
   *
   * @param cc    the chunk cache
   * @param x     the tile X coordinate
   * @param y     the tile Y coordinate
   * @param inout the three channel values to filter
   */
  protected void filter3(ChunkCache cc, int x, int y, float[] inout) {
    BlockState block = cc.getBlock(x, y);
    Liquid liq = cc.getLiquid(x, y);
    int liqAmt = cc.getLiquidAmount(x, y);
    inout[0] = block.filterLight(x, y, inout[0], Channel.RED);
    inout[1] = block.filterLight(x, y, inout[1], Channel.GREEN);
    inout[2] = block.filterLight(x, y, inout[2], Channel.BLUE);
    if (liq != null) {
      inout[0] = liq.filterLight(x, y, liqAmt, inout[0], Channel.RED);
      inout[1] = liq.filterLight(x, y, liqAmt, inout[1], Channel.GREEN);
      inout[2] = liq.filterLight(x, y, liqAmt, inout[2], Channel.BLUE);
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
   * Returns the window origin of the current front buffer, in tiles.
   *
   * @return the front window origin X
   */
  public int frontOriginX() {
    return frontOriX;
  }

  /**
   * Returns the window origin of the current front buffer, in tiles.
   *
   * @return the front window origin Y
   */
  public int frontOriginY() {
    return frontOriY;
  }

  /**
   * Returns the number of completed front-buffer changes, bumped whenever the light data
   * or the window changes.
   *
   * @return the light version
   */
  public long lightVersion() {
    return version.get();
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
   * {@link #MIN_SIZE}..{@link #MAX_SIZE}. Applied by {@link #applyPendingResize()}
   * while the worker is idle.
   *
   * @param w the desired window width in tiles
   * @param h the desired window height in tiles
   */
  protected void requestSize(int w, int h) {
    int nw = Math.clamp((w + 15) & ~15, MIN_SIZE, MAX_SIZE);
    int nh = Math.clamp((h + 15) & ~15, MIN_SIZE, MAX_SIZE);
    if (nw != sizeX || nh != sizeY) {
      pendingW = nw;
      pendingH = nh;
    }
  }

  /**
   * Applies a pending resize, copying the overlapping region of the old buffers so no
   * part of the visible window becomes blank.
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
   * Copies the region shared by two windows into the destination buffer.
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
   * Swaps the front and back buffers, publishing the freshly computed buffer and
   * updating the front window origin.
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
   * Mixes light into a tile's current value, using the composition formula. Light whose
   * channels are all at or below {@link #DARK_LUMINANCE} is ignored.
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
    blendWrite(o, r, g, b);
  }

  /**
   * Adds light from a source at fractional coordinates, spreading it over the surrounding
   * tiles with an inverse-square falloff. Tiles closer than one tile receive the full
   * value; sources whose channels are all at or below {@link #DARK_LUMINANCE} are ignored.
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
   * Draws a directional beam into the beam layer. Beam light is kept separate from the
   * spread buffer: the cellular spread is isotropic and would wash out the cone. The beam
   * layer is blended into the raw channels after spreading (see {@link #mergeBeam()}), so
   * the cone shape survives.
   *
   * @param x    the light source X coordinate
   * @param y    the light source Y coordinate
   * @param beam the beam to draw
   */
  public void drawBeam(float x, float y, Beam beam) {
    float r = beam.r;
    float g = beam.g;
    float b = beam.b;
    float maxIntensity = Math.max(r, Math.max(g, b));
    if (maxIntensity <= DARK_LUMINANCE) {
      return;
    }

    float range = maxIntensity * beam.range;
    float perBlockAir = 1F / beam.range;
    float[] bdsc = FastTrigonometric.sincos(beam.direction);
    float bdx = bdsc[1];
    float bdy = bdsc[0];
    float beamK = beam.strength / (1F - FastTrigonometric.cos(beam.halfAngle));
    float ambi = beam.ambience;

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
            beamLayer[o0] = formula.blend(beamLayer[o0], r * AMPLIFIER);
            beamLayer[o0 + 1] = formula.blend(beamLayer[o0 + 1], g * AMPLIFIER);
            beamLayer[o0 + 2] = formula.blend(beamLayer[o0 + 2], b * AMPLIFIER);
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
        
        beamLayer[o] = formula.blend(beamLayer[o], r * factor * AMPLIFIER);
        beamLayer[o + 1] = formula.blend(beamLayer[o + 1], g * factor * AMPLIFIER);
        beamLayer[o + 2] = formula.blend(beamLayer[o + 2], b * factor * AMPLIFIER);
      }
    }
  }

  /**
   * Blends the beam layer into the raw channels of {@code back} using the composition
   * formula, then clears it for the next frame. Call after spreading, before populating
   * vertices.
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
   * Computes the ambient-occlusion factors of a tile from the solidity of its neighbors
   * and stores them in the tile's AO slots.
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
   * Smoothes one channel of a tile's light against its neighbors and writes the four
   * per-vertex values, darkened by the tile's ambient occlusion.
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
   * Marks the light stale after world content changed (block, wall, or liquid edits);
   * the next {@link #tick(Box2D)} recomputes it.
   */
  public void requestRecalc() {
    worldDirty = true;
  }

  /**
   * Rebuilds the light window around the given camera bounds.
   *
   * <p>The full-window computation is skipped while nothing changed: the world is
   * untouched, the sunlight is within the previous values, and the camera is still
   * inside the computed window (the window is the view plus {@link #SPREAD_MARGIN} on
   * every side, so normal movement rides along).
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
      boolean skyDirty = Math.abs(sunlight[0] - lastSunR) > 0.01F
          || Math.abs(sunlight[1] - lastSunG) > 0.01F
          || Math.abs(sunlight[2] - lastSunB) > 0.01F;
      boolean camDirty = windowMissesCamera(cam);
      if (!worldDirty && !skyDirty && !camDirty) {
        return; // nothing changed — keep the current front buffer
      }
      lastSunR = sunlight[0];
      lastSunG = sunlight[1];
      lastSunB = sunlight[2];
      // resize only while idle. the worker must not see the array change
      if (applyPendingResize()) {
        version.incrementAndGet();
        onResized();
      }
      swap();
      worker = Thread.ofVirtual().start(() -> {
        calculate(cam);
        worldDirty = false;
        done = true;
        version.incrementAndGet();
      });
    }
    oriX = (int) Math.floor(cam.centralX()) - sizeX / 2;
    oriY = (int) Math.floor(cam.centralY()) - sizeY / 2;
  }

  /**
   * Returns whether the requested window (view + spread margin) no longer fits inside
   * the current light window, i.e. the camera moved so far that the cached light no
   * longer covers it.
   */
  private boolean windowMissesCamera(Box2D cam) {
    int nw = Math.clamp((int) Math.ceil(cam.width()) + 2 * SPREAD_MARGIN + 4, MIN_SIZE, MAX_SIZE);
    int nh = Math.clamp((int) Math.ceil(cam.height()) + 2 * SPREAD_MARGIN + 4, MIN_SIZE, MAX_SIZE);
    int rx = (int) Math.floor(cam.centralX()) - nw / 2;
    int ry = (int) Math.floor(cam.centralY()) - nh / 2;
    return nw != sizeX || nh != sizeY
        || rx < oriX || rx + nw > oriX + sizeX
        || ry < oriY || ry + nh > oriY + sizeY;
  }

  /**
   * Rebuilds engine state that depends on the window size after a resize takes effect.
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
   * Returns the front buffer with the last completed light computation.
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
    return (x + y * sizeX) * STRIDE;
  }

  /**
   * Returns the offset of a tile in the working window.
   *
   * @param x the tile X coordinate
   * @param y the tile Y coordinate
   * @return the tile offset, or -1 if the tile is outside the window
   */
  protected int backBufferIndex(int x, int y) {
    x -= oriX;
    y -= oriY;
    return (x + y * sizeX) * STRIDE;
  }

  /**
   * No-op: the engine owns no GPU resources; the {@link LightMapRenderer} releases the
   * lightmap targets and meshes.
   */
  @Override
  public void close() {
  }
}
