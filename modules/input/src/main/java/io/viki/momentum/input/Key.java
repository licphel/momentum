/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.viki.momentum.input;

/**
 * A pollable input state with press and repeat transitions.
 *
 * <p>Thread safety is determined by each implementation.
 */
public interface Key extends Input {
  /**
   * Returns the strongest current control value.
   *
   * @return current action strength
   */
  float value();

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
   * Reports whether the input generated a repeat event during the current frame.
   *
   * @return {@code true} if the input repeated
   */
  boolean repeated();

  /**
   * Reports whether the input transitioned to held or repeated during the current frame.
   *
   * @return {@code true} if the input transitioned to held or repeated
   */
  default boolean transitionedOrRepeated() {
    return transitioned() || repeated();
  }

  /**
   * Reports a transition to released; stateless input views may leave this unsupported.
   *
   * @return {@code true} if the input transitioned to released
   */
  boolean released();
}
