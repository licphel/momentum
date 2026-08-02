package net.fmhi.world.physics;

/**
 * How a block collides with physics bodies.
 */
public enum CollisionKind {
  /** No collision (air). */
  NONE,
  /** One-way platform: collides only from above. */
  PLATFORM,
  /** Full block collision. */
  BLOCK
}
