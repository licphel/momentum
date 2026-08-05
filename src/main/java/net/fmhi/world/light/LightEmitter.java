package net.fmhi.world.light;

import java.util.List;

/**
 * Contract for objects that contribute light to the engine.
 *
 * <p>Three shapes of emitter exist: static tiles (blocks and walls), liquids, and dynamic
 * objects such as entities. Each contributes ambient light and directional beams, and may
 * filter the light passing through it.
 */
public interface LightEmitter {
  /**
   * A light emitter occupying a tile, such as a block or a wall.
   */
  interface Tile extends LightEmitter {
    /**
     * Filters the skylight reaching this tile.
     *
     * @param x       the tile X coordinate
     * @param y       the tile Y coordinate
     * @param in      the incoming skylight
     * @param channel the color channel
     * @return the filtered value
     */
    float filterSkylight(int x, int y, float in, byte channel);

    /**
     * Filters the light entering this tile.
     *
     * @param x       the tile X coordinate
     * @param y       the tile Y coordinate
     * @param in      the incoming light
     * @param channel the color channel
     * @return the filtered value
     */
    float filterLight(int x, int y, float in, byte channel);

    /**
     * Returns the ambient light emitted by this tile.
     *
     * @param x       the tile X coordinate
     * @param y       the tile Y coordinate
     * @param channel the color channel
     * @return the emitted light
     */
    float emitAmbient(int x, int y, byte channel);

    /**
     * Returns the directional beams emitted by this tile.
     *
     * @param x the tile X coordinate
     * @param y the tile Y coordinate
     * @return the emitted beams
     */
    List<Beam> emitBeams(int x, int y);
  }

  /**
   * A light emitter occupying a tile, such as a body of liquid.
   */
  interface Liquid extends LightEmitter {
    /**
     * Filters the light entering this liquid.
     *
     * @param x       the tile X coordinate
     * @param y       the tile Y coordinate
     * @param amount  the liquid amount in the tile
     * @param in      the incoming light
     * @param channel the color channel
     * @return the filtered value
     */
    float filterLight(int x, int y, int amount, float in, byte channel);

    /**
     * Returns the ambient light emitted by this liquid.
     *
     * @param x       the tile X coordinate
     * @param y       the tile Y coordinate
     * @param amount  the liquid amount in the tile
     * @param channel the color channel
     * @return the emitted light
     */
    float emitAmbient(int x, int y, int amount, byte channel);

    /**
     * Returns the directional beams emitted by this liquid.
     *
     * @param x      the tile X coordinate
     * @param y      the tile Y coordinate
     * @param amount the liquid amount in the tile
     * @return the emitted beams
     */
    List<Beam> emitBeams(int x, int y, int amount);
  }

  /**
   * A light emitter not bound to a tile, such as an entity.
   */
  interface Dynamic extends LightEmitter {
    /**
     * Returns the ambient light emitted by this object.
     *
     * @param channel the color channel
     * @return the emitted light
     */
    float emitAmbient(byte channel);

    /**
     * Returns the directional beams emitted by this object.
     *
     * @return the emitted beams
     */
    List<Beam> emitBeams();
  }
}
