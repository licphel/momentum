/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.input;

import java.util.List;

/**
 * One conjunctive key chord within a {@link KeyBinding}.
 *
 * <p>Every key and the optional modifier chord must be held. A transition occurs when the full
 * chord is held and any of its keys transitions, so a match of {@code A+B} transitions when the
 * second key is pressed.
 *
 * <p>Instances are not thread-safe because they observe mutable {@link Key} state.
 *
 * @param keys the keys that must be held
 * @param modifiers the required modifier bitmask, or {@link InputModifiers#ANY}
 */
public record KeyMatch(List<Key> keys, int modifiers) implements Transitable {
  /**
   * Creates a key chord whose keys are owned by the same input snapshot.
   *
   * @param keys the keys that must be held, not empty
   * @param modifiers the required modifier bitmask, or {@link InputModifiers#ANY}
   * @throws IllegalArgumentException if {@code keys} is empty or belongs to multiple snapshots
   */
  public KeyMatch {
    keys = List.copyOf(keys);
    if (keys.isEmpty()) {
      throw new IllegalArgumentException("A key match must contain at least one key");
    }
    InputSnapshot owner = keys.getFirst().owner();
    for (Key key : keys) {
      if (key.owner() != owner) {
        throw new IllegalArgumentException("All keys in a match must belong to one Snapshot");
      }
    }
  }

  /**
   * Creates a chord that matches regardless of modifiers.
   *
   * @param keys the keys that must be held, not empty
   * @return the key chord
   * @throws IllegalArgumentException if {@code keys} is empty or belongs to multiple snapshots
   */
  public static KeyMatch of(Key... keys) {
    return new KeyMatch(List.of(keys), InputModifiers.ANY);
  }

  /**
   * Creates a chord with the required modifiers.
   *
   * @param modifiers the required modifier bitmask, or {@link InputModifiers#ANY}
   * @param keys the keys that must be held, not empty
   * @return the key chord
   * @throws IllegalArgumentException if {@code keys} is empty or belongs to multiple snapshots
   */
  public static KeyMatch of(int modifiers, Key... keys) {
    return new KeyMatch(List.of(keys), modifiers);
  }

  @Override
  public boolean isDown() {
    InputSnapshot owner = keys.getFirst().owner();
    if (!(modifiers == InputModifiers.ANY || owner.mods() == modifiers)) {
      return false;
    }
    for (Key key : keys) {
      if (!key.isDown()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean transitioned() {
    if (!isDown()) {
      return false;
    }
    for (Key key : keys) {
      if (key.transitioned()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean transitionedOrRepeated() {
    if (!isDown()) {
      return false;
    }
    for (Key key : keys) {
      if (key.transitionedOrRepeated()) {
        return true;
      }
    }
    return false;
  }
}
