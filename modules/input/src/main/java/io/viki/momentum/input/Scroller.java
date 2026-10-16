package io.viki.momentum.input;

/**
 * A two-dimensional relative or persistent input source. Thread safety belongs to the implementation.
 * Deltas are changes accumulated since the frame boundary, never velocities.
 */
public interface Scroller {
  /** Returns the accumulated horizontal change for this frame. */
  double deltaX();
  /** Returns the accumulated vertical change for this frame. */
  double deltaY();
  /** Returns the retained horizontal position, or zero for a relative-only source. */
  double x();
  /** Returns the retained vertical position, or zero for a relative-only source. */
  double y();
  /** Whether positions persist across frames; consumers integrate these with elapsed time when scrolling. */
  boolean persistent();
}
