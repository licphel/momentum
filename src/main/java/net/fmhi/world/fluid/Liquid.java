package net.fmhi.world.fluid;

import net.fmhi.math.Color;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.LightBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * A liquid type. Liquids live in a layer separate from blocks and walls:
 * every tile stores a liquid id and a level (see {@link LiquidMap}).
 */
@NullMarked
public abstract class Liquid {

  private final byte id;

  protected Liquid(byte id) {
    this.id = id;
  }

  /** The id stored per tile, equal to the index in {@link Liquids#ALL}. */
  public final byte id() {
    return id;
  }

  public abstract String name();

  /** The color used to render this liquid. */
  public abstract Color color();

  /**
   * Reaction hook invoked when a tile of this liquid touches a different
   * liquid (the lava/water reaction is implemented here).
   *
   * @param src the liquid of the tile being processed
   * @param dst the touching liquid of the neighbor tile
   */
  public void onTouch(LiquidStack src, LiquidStack dst, Level level, int x, int y, int nx, int ny) {
  }

  // -- light --------------------------------------------------------------

  /** How much light the liquid lets through at the given level. Like a
   * block's filter, but liquids never emit beams. */
  public float filterLight(float in, byte channel, int level) {
    // thicker liquid absorbs a bit more
    return in * 0.98F - level / (float) FluidEngine.FULL * 0.01F;
  }

  /** Light emission; false = none (liquids never emit beams). */
  public boolean getLight(LightBuffer buf) {
    return false;
  }

  // -- physics ------------------------------------------------------------

  /** How strongly the liquid drags entities. */
  public float viscosity() {
    return 1F;
  }

  /** Density relative to entities: a denser liquid buoys entities up. */
  public float density() {
    return 1F;
  }

  /** Temperature in degrees; hot liquid produces a thermal updraft. */
  public float temperature() {
    return 20F;
  }
}
