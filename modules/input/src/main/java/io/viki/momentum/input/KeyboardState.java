package io.viki.momentum.input;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Keyboard physical state and transitions; confined to the owning snapshot thread.
 */
public final class KeyboardState {
  private static final int LOCK_MODS = InputModifiers.CAPS_LOCK | InputModifiers.NUM_LOCK;
  private final Runnable changed;
  private final EnumMap<KeyCode, KeyAction> states = new EnumMap<>(KeyCode.class);
  private final EnumSet<KeyCode> pressed = EnumSet.noneOf(KeyCode.class);
  private final EnumSet<KeyCode> released = EnumSet.noneOf(KeyCode.class);
  private final EnumSet<KeyCode> repeated = EnumSet.noneOf(KeyCode.class);
  private int lockMods;

  KeyboardState(Runnable changed) {
    this.changed = changed;
  }

  /**
   * Returns the current action state of a key.
   *
   * @param key key to query
   * @return current key action, defaulting to {@link KeyAction#RELEASE}
   */
  public KeyAction get(KeyCode key) {
    return states.getOrDefault(key, KeyAction.RELEASE);
  }

  /**
   * Tests whether a key is held.
   *
   * @param key key to query
   * @return whether the key is held
   */
  public boolean isDown(KeyCode key) {
    return get(key) != KeyAction.RELEASE;
  }

  /**
   * Tests whether a key was pressed during the current frame.
   *
   * @param key key to query
   * @return whether the key transitioned to held
   */
  public boolean isTransitioned(KeyCode key) {
    return pressed.contains(key);
  }

  /**
   * Tests whether a key was released during the current frame.
   *
   * @param key key to query
   * @return whether the key transitioned to released
   */
  public boolean isReleased(KeyCode key) {
    return released.contains(key);
  }

  /**
   * Tests whether a key generated a repeat event during the current frame.
   *
   * @param key key to query
   * @return whether the key repeated
   */
  public boolean isRepeated(KeyCode key) {
    return repeated.contains(key);
  }

  /**
   * Returns the current modifier bitmask.
   *
   * @return modifier flags from {@link InputModifiers}
   */
  public int mods() {
    int value = lockMods;
    if (isDown(KeyCode.LEFT_SHIFT) || isDown(KeyCode.RIGHT_SHIFT)) {
      value |= InputModifiers.SHIFT;
    }
    if (isDown(KeyCode.LEFT_CONTROL) || isDown(KeyCode.RIGHT_CONTROL)) {
      value |= InputModifiers.CONTROL;
    }
    if (isDown(KeyCode.LEFT_ALT) || isDown(KeyCode.RIGHT_ALT)) {
      value |= InputModifiers.ALT;
    }
    if (isDown(KeyCode.LEFT_SUPER) || isDown(KeyCode.RIGHT_SUPER)) {
      value |= InputModifiers.SUPER;
    }
    return value;
  }

  /**
   * Applies a platform key event.
   *
   * @param key changed key
   * @param action event action
   * @param modifiers modifier bitmask reported with the event
   */
  public void onKeyChange(KeyCode key, KeyAction action, int modifiers) {
    boolean wasDown = isDown(key);
    if (action == KeyAction.PRESS && !wasDown) {
      pressed.add(key);
    }
    if (action == KeyAction.RELEASE && wasDown) {
      released.add(key);
    }
    if (action == KeyAction.REPEAT) {
      repeated.add(key);
    }
    states.put(key, action);
    updateLockMods(modifiers);
    changed.run();
  }

  void updateLockMods(int modifiers) {
    lockMods = modifiers & LOCK_MODS;
  }

  /** Clears transient press, release, and repeat state for the next frame. */
  public void clearFrameState() {
    pressed.clear();
    released.clear();
    repeated.clear();
  }

  /** Releases all held keys and clears lock modifier state. */
  public void reset() {
    for (var entry : states.entrySet()) {
      if (entry.getValue() != KeyAction.RELEASE) {
        released.add(entry.getKey());
      }
    }
    states.clear();
    lockMods = 0;
    changed.run();
  }
}
