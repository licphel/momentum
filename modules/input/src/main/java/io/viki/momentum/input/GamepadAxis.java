package io.viki.momentum.input;

/**
 * Stick axes use [-1,1], with positive Y upwards; triggers use [0,1].
 */
public enum GamepadAxis {
  /** Horizontal axis of the left stick. */
  LEFT_X,
  /** Vertical axis of the left stick. */
  LEFT_Y,
  /** Horizontal axis of the right stick. */
  RIGHT_X,
  /** Vertical axis of the right stick. */
  RIGHT_Y,
  /** Left analog trigger. */
  LEFT_TRIGGER,
  /** Right analog trigger. */
  RIGHT_TRIGGER
}
