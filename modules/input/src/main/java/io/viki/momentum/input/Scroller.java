package io.viki.momentum.input;

/**
 * A two-dimensional relative or persistent input source. Thread safety belongs to the implementation.
 * Deltas are changes accumulated since the frame boundary, never velocities.
 */
public interface Scroller {
  /**
   * Returns the accumulated horizontal change for this frame.
   *
   * @return horizontal delta
   */
  double deltaX();

  /**
   * Returns the accumulated vertical change for this frame.
   *
   * @return vertical delta
   */
  double deltaY();

  /**
   * Returns the retained horizontal position, or zero for a relative-only source.
   *
   * @return horizontal position
   */
  double x();

  /**
   * Returns the retained vertical position, or zero for a relative-only source.
   *
   * @return vertical position
   */
  double y();

  /**
   * Reports whether positions persist across frames;
   * consumers integrate these with elapsed time when scrolling.
   *
   * @return whether positions persist
   */
  boolean persistent();
}
