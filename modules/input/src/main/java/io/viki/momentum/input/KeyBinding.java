/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.input;

import java.util.List;

/**
 * A named action whose {@linkplain #matches() key matches} are combined with logical OR.
 *
 * <p>For example, matches {@code [A+B, C]} represent {@code (A & B) | C}. The stable string
 * {@linkplain #key() key} identifies the action in configuration, while {@link #rebind(List)}
 * replaces its physical shortcuts. Instances are mutable and not thread-safe.
 */
public final class KeyBinding implements Transitable {
  private final String key;
  private List<KeyMatch> matches;

  /**
   * Creates a named action with the supplied shortcuts.
   *
   * @param key the stable configuration key for the action, not blank
   * @param matches the shortcuts that activate the action
   * @throws IllegalArgumentException if {@code key} is blank
   */
  public KeyBinding(String key, List<KeyMatch> matches) {
    if (key.isBlank()) {
      throw new IllegalArgumentException("Key binding key must not be blank");
    }
    this.key = key;
    this.matches = List.copyOf(matches);
  }

  /**
   * Creates a named action with the supplied shortcuts.
   *
   * @param key the stable configuration key for the action, not blank
   * @param matches the shortcuts that activate the action
   * @throws IllegalArgumentException if {@code key} is blank
   */
  public KeyBinding(String key, KeyMatch... matches) {
    this(key, List.of(matches));
  }

  /**
   * Returns this action's stable configuration key.
   *
   * @return the stable configuration key
   */
  public String key() {
    return key;
  }

  /**
   * Returns the shortcuts that activate this action.
   *
   * @return the current shortcuts
   */
  public List<KeyMatch> matches() {
    return matches;
  }

  /**
   * Replaces the shortcuts that activate this action.
   *
   * @param value the replacement shortcuts
   */
  public void rebind(List<KeyMatch> value) {
    matches = List.copyOf(value);
  }

  /**
   * Replaces the shortcuts that activate this action.
   *
   * @param value the replacement shortcuts
   */
  public void rebind(KeyMatch... value) {
    rebind(List.of(value));
  }

  @Override
  public boolean isDown() {
    for (KeyMatch match : matches) {
      if (match.isDown()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean transitioned() {
    for (KeyMatch match : matches) {
      if (match.transitioned()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean transitionedOrRepeated() {
    for (KeyMatch match : matches) {
      if (match.transitionedOrRepeated()) {
        return true;
      }
    }
    return false;
  }
}
