/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.input;

/**
 * A pollable input state with press and repeat transitions.
 *
 * <p>Thread safety is determined by each implementation.
 */
public interface Transitable {
  /**
   * Reports whether the input is currently held.
   *
   * @return {@code true} if the input is held
   */
  boolean isDown();

  /**
   * Reports whether the input transitioned to held during the current frame.
   *
   * @return {@code true} if the input transitioned to held
   */
  boolean transitioned();

  /**
   * Reports whether the input transitioned to held or repeated during the current frame.
   *
   * @return {@code true} if the input transitioned to held or repeated
   */
  boolean transitionedOrRepeated();
}
