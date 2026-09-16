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
 * A logical key binding that wraps a physical {@link KeyCode} with persistent press tracking and modifier-aware
 * queries.
 *
 * <p>Acquire instances via {@link InputSnapshot#key(KeyCode)}.
 *
 * <p>This class is not thread-safe. All methods must be called from the
 * rendering thread.
 */
public final class Key implements Transitable {
  private final InputSnapshot owner;
  private final KeyCode code;
  private boolean down;
  private boolean pressTransitioning;
  private boolean repeating;
  private int pressMods;
  private long pressTime = -1;

  /**
   * Creates a key that tracks input from the specified snapshot.
   *
   * @param code the physical key code to bind
   * @param owner the snapshot that supplies this key's input state
   */
  Key(KeyCode code, InputSnapshot owner) {
    this.code = code;
    this.owner = owner;
  }

  /**
   * Returns the currently bound physical key code.
   *
   * @return the bound key code
   */
  public KeyCode code() {
    return code;
  }

  InputSnapshot owner() {
    return owner;
  }

  /**
   * Returns whether this key is currently held down.
   *
   * @return {@code true} if the key is pressed
   */
  @Override
  public boolean isDown() {
    return isDown(InputModifiers.ANY);
  }

  /**
   * Returns whether this key is currently held down.
   *
   * <p>Pass {@link InputModifiers#ANY} to match any modifier combination.
   * Pass {@link InputModifiers#NONE} to require no modifiers.
   *
   * <p>The modifiers are matched against the keys currently held, so a
   * modifier pressed after this key joins the chord immediately.
   *
   * @param mods the required modifier bitmask, or {@link InputModifiers#ANY}
   * @return {@code true} if the key is pressed
   */
  public boolean isDown(int mods) {
    return down && isModOK(mods);
  }

  /**
   * Returns whether this key was just pressed this frame, regardless of active modifier keys.
   *
   * <p>Equivalent to {@code transitioned(InputModifiers.ANY)}.
   *
   * @return {@code true} if the key transitioned to pressed this frame
   */
  @Override
  public boolean transitioned() {
    return transitioned(InputModifiers.ANY);
  }

  /**
   * Returns whether this key was just pressed this frame with matching modifiers.
   *
   * <p>Pass {@link InputModifiers#ANY} to match any modifier combination.
   * Pass {@link InputModifiers#NONE} to require no modifiers.
   *
   * <p>Unlike {@link #isDown(int)}, the modifiers are matched against those
   * held at the moment of the press, not those currently held.
   *
   * @param mods the required modifier bitmask, or {@link InputModifiers#ANY}
   * @return {@code true} if the key transitioned to pressed this frame with matching modifiers
   */
  public boolean transitioned(int mods) {
    return pressTransitioning && modifiersMatch(pressMods, mods);
  }

  /**
   * Returns whether this key was just pressed or repeated this frame, regardless of active modifier keys.
   *
   * <p>Equivalent to {@code transitionedOrRepeated(InputModifiers.ANY)}.
   *
   * @return {@code true} if the key transitioned or repeated this frame
   */
  @Override
  public boolean transitionedOrRepeated() {
    return transitionedOrRepeated(InputModifiers.ANY);
  }

  /**
   * Returns whether this key was just pressed or repeated this frame with matching modifiers.
   *
   * <p>Pass {@link InputModifiers#ANY} to match any modifier combination.
   * Pass {@link InputModifiers#NONE} to require no modifiers.
   *
   * <p>Unlike {@link #isDown(int)}, the modifiers are matched against those
   * held at the most recent press or repeat, not those currently held.
   *
   * @param mods the required modifier bitmask, or {@link InputModifiers#ANY}
   * @return {@code true} if the key transitioned or repeated this frame with matching modifiers
   */
  public boolean transitionedOrRepeated(int mods) {
    return (pressTransitioning || repeating) && modifiersMatch(pressMods, mods);
  }

  /**
   * Returns the timestamp of the most recent press, in nanoseconds as reported by {@link System#nanoTime()}.
   *
   * <p>Returns {@code -1} if the key has never been pressed.
   *
   * @return the press timestamp in nanoseconds, or {@code -1}
   */
  public long pressTime() {
    return pressTime;
  }

  private boolean isModOK(int mods) {
    return modifiersMatch(owner.mods(), mods);
  }

  private static boolean modifiersMatch(int actual, int expected) {
    return expected == InputModifiers.ANY || actual == expected;
  }

  void apply(KeyAction action, int mods) {
    switch (action) {
      case PRESS:
        if (!down) {
          pressTransitioning = true;
          pressTime = System.nanoTime();
        }
        down = true;
        pressMods = mods;
        break;
      case REPEAT:
        down = true;
        repeating = true;
        pressMods = mods;
        break;
      case RELEASE:
        down = false;
        pressTransitioning = false;
        repeating = false;
        break;
    }
  }

  void endFrame() {
    pressTransitioning = false;
    repeating = false;
  }

  @Override
  public int hashCode() {
    return code.ordinal();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Key && code.equals(((Key) obj).code);
  }
}
