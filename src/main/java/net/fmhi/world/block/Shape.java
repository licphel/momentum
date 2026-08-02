package net.fmhi.world.block;

/**
 * How a block fills its tile: determines collision, light blocking and
 * liquid containment.
 */
public enum Shape {
  /** Empty tile: no collision shape, light passes freely. */
  VACUUM,
  /** Partially filled tile (platforms, slopes): collides but lets light
   * and liquid through. */
  PARTIAL,
  /** Translucent tile: light is attenuated but not fully blocked. */
  TRANSLUCENT,
  /** Fully solid tile: blocks light, liquid and movement. */
  SOLID
}
