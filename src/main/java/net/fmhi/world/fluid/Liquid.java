package net.fmhi.world.fluid;

import net.fmhi.math.Color;
import net.fmhi.world.level.Level;
import net.fmhi.world.light.Beam;
import net.fmhi.world.light.LightEmitter;

import java.util.Collections;
import java.util.List;

/**
 * A liquid type. Liquids live in a layer separate from blocks and walls:
 * every tile stores a liquid id and a level (see {@link LiquidMap}).
 */
public abstract class Liquid implements LightEmitter.Liquid {

  private final byte id;

  protected Liquid(byte id) {
    this.id = id;
  }

  /** The id stored per tile, equal to the index in {@link Liquids#ALL}. */
  public final byte id() {
    return id;
  }

  public abstract String name();

  /** The gradient used to render this liquid. */
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

  @Override
  public float filterLight(int x, int y, int amount, float in, byte channel) {
    return in * 0.98F - amount / (float) FluidEngine.FULL * 0.01F;
  }

  /** The ambient light emitted by this liquid on one channel, or {@code 0}. */
  @Override
  public float emitAmbient(int x, int y, int amount, byte channel) {
    return 0F;
  }

  /** The directional beams emitted by this liquid; the caller draws and
   * recycles them. */
  @Override
  public java.util.Collection<Beam> emitBeams(int x, int y, int amount) {
    return Collections.emptySet();
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
