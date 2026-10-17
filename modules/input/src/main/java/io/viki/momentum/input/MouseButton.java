package io.viki.momentum.input;

/**
 * Physical mouse buttons in platform index order.
 */
public enum MouseButton {
  /** Primary mouse button. */
  LEFT,
  /** Secondary mouse button. */
  RIGHT,
  /** Middle mouse button. */
  MIDDLE,
  /** Fourth mouse button. */
  BUTTON_4,
  /** Fifth mouse button. */
  BUTTON_5,
  /** Sixth mouse button. */
  BUTTON_6,
  /** Seventh mouse button. */
  BUTTON_7,
  /** Eighth mouse button. */
  BUTTON_8;

  private static final MouseButton[] VALUES = values();

  /**
   * Resolves a platform button index.
   *
   * @param index zero-based platform button index
   * @return corresponding mouse button
   * @throws IllegalArgumentException if the index is outside the supported range
   */
  public static MouseButton fromIndex(int index) {
    if (index < 0 || index >= VALUES.length) {
      throw new IllegalArgumentException("Mouse button index out of range: " + index);
    }
    return VALUES[index];
  }
}
