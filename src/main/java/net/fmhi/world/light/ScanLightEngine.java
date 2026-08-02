package net.fmhi.world.light;

import net.fmhi.math.Box2D;
import net.fmhi.util.Profiler;
import net.fmhi.world.block.BlockState;
import net.fmhi.world.entity.Entity;
import net.fmhi.world.fluid.Liquids;
import net.fmhi.world.level.Chunk;
import net.fmhi.world.level.ChunkCache;
import net.fmhi.world.level.Level;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Light engine that recomputes its whole window on a background virtual
 * thread, spreading light with four relaxation passes in alternating
 * directions.
 *
 * <p>The window size adapts to the camera (see {@link LightEngine}); resizes
 * copy the overlapping region so the renderer never sees a blank window. The
 * computed buffer is swapped into the front only once finished, so the
 * renderer always reads a complete, consistent frame.
 *
 * @see LightEngine
 */
public class ScanLightEngine extends LightEngine {
  /**
   * Creates a light engine for the given level.
   *
   * @param level the level to light
   */
  public ScanLightEngine(Level level) {
    super(level);
  }

  /**
   * Computes the next frame: seeds block and entity light, spreads it with
   * four alternating passes, then populates the per-vertex values.
   *
   * @param cam the camera bounds to compute light for
   */
  @Override
  protected void calculate(Box2D cam) {
    float spd = MAX_VALUE_GENERAL / UNIT;
    int x0 = (int) (cam.minX() - spd);
    int y0 = (int) (cam.minY() - spd);
    int x1 = (int) (cam.maxX() + spd);
    int y1 = (int) (cam.maxY() + spd);

    // guard: the window must contain the computation range
    if (x1 - x0 + 1 > sizeX || y1 - y0 + 1 > sizeY) {
      return; // window too small for this camera — next tick will resize
    }

    // clear the window — additive composition formulas would accumulate
    // leftover values from the previous frame otherwise
    Arrays.fill(back, 0F);

    cc = new ChunkCache(level, x0 - 1, y0 - 1, x1 + 1, y1 + 1);

    // Phase 1: seed
    try (Profiler.Scope _ = Profiler.scope("lighting:seed_block")) {
      for (int x = x0 - 1; x <= x1 + 1; x++) {
        for (int y = y0 - 1; y <= y1 + 1; y++) {
          seed(cc, sunlight, x, y);
        }
      }
    }

    // Phase 2: entity lights
    try (Profiler.Scope _ = Profiler.scope("lighting:seed_entity")) {
      for (Chunk c : level.loadedChunks()) {
        for (Entity e : c.entities()) {
          seed(e);
        }
      }
    }

    // Phase 3: 4-pass spread
    try (Profiler.Scope _ = Profiler.scope("lighting:spread")) {
      channelDispatch(channel -> {
        for (int x = x1; x >= x0; x--) {
          for (int y = y1; y >= y0; y--) {
            spread(x, y, channel);
          }
        }
        for (int x = x0; x <= x1; x++) {
          for (int y = y0; y <= y1; y++) {
            spread(x, y, channel);
          }
        }
        for (int x = x0; x <= x1; x++) {
          for (int y = y1; y >= y0; y--) {
            spread(x, y, channel);
          }
        }
        for (int x = x1; x >= x0; x--) {
          for (int y = y0; y <= y1; y++) {
            spread(x, y, channel);
          }
        }
      });
    }

    // Beam light: max-blend into raw channels after spread (spread is
    // isotropic and would wash out the cone if the beam went through it)
    mergeBeam();

    // Phase 4: populate vertex lights
    try (Profiler.Scope _ = Profiler.scope("lighting:populate")) {
      for (int y = y0; y <= y1; y++) {
        for (int x = x0; x <= x1; x++) {
          int o = backBufferIndex(x, y);
          if (o < 0) {
            continue;
          }
          populateAO(o, cc, x, y);
        }
      }

      channelDispatch(channel -> {
        for (int y = y0; y <= y1; y++) {
          for (int x = x0; x <= x1; x++) {
            int o = backBufferIndex(x, y);
            if (o < 0) {
              continue;
            }
            populateSmoothedLightVerticesByChannel(o, channel, x, y);
          }
        }
      });
    }
  }

  /**
   * Relaxes the light of a tile toward the brightest of its four neighbors.
   */
  private void spread(int x, int y, byte channel) {
    if (!cc.isLoaded(x, y)) {
      return;
    }
    int o = backBufferIndex(x, y);
    if (o < 0) {
      return;
    }
    spreadOnChannel(o, channel, x, y);
  }

  /**
   * Relaxes one channel of a tile toward the brightest of its four neighbors,
   * applying the block's light filter; values at or below
   * {@link #DARK_LUMINANCE} are stored as darkness.
   *
   * @param o       the working buffer offset of the tile
   * @param channel the color channel to spread
   * @param x       the tile X coordinate
   * @param y       the tile Y coordinate
   */
  private void spreadOnChannel(int o, byte channel, int x, int y) {
    BlockState state = cc.getBlock(x, y);
    float l1 = getChannelValue(x - 1, y, channel);
    float l2 = getChannelValue(x + 1, y, channel);
    float l3 = getChannelValue(x, y - 1, channel);
    float l4 = getChannelValue(x, y + 1, channel);
    float max = Math.max(l1, Math.max(l2, Math.max(l3, l4)));
    float f = formula.blend(back[o + channel], max);
    f = filter(cc, channel, x, y, f);
    back[o + channel] = f <= DARK_LUMINANCE ? 0F : f;
  }

  private void channelDispatch(Consumer<Byte> fn) {
    Thread[] threads = new Thread[Channel.CHANNELS.length];
    for (int i = 0; i < threads.length; i++) {
      byte channel = Channel.CHANNELS[i];
      threads[i] = Thread.startVirtualThread(() -> fn.accept(channel));
    }

    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
