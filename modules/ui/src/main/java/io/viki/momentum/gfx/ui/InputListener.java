/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.gfx.ui;

import io.viki.momentum.input.KeyAction;
import io.viki.momentum.input.KeyCode;

/**
 * Direct input callbacks for a UI element.
 *
 * <p>Coordinates are local to the receiving element. Implementations are mutable and are
 * expected to run on their owning UI thread.
 */
public interface InputListener {
  default boolean onMouseMove(float x, float y) {
    return false;
  }

  default boolean onMouseButton(float x, float y, KeyCode button, KeyAction action, int modifiers) {
    return false;
  }

  default boolean onScroll(float x, float y, double deltaX, double deltaY) {
    return false;
  }

  default boolean onKey(KeyCode key, KeyAction action, int modifiers) {
    return false;
  }

  default boolean onCharacter(int codepoint) {
    return false;
  }

  default void onPointerEnter() {
  }

  default void onPointerExit() {
  }

  default void onPointerCancel(KeyCode button) {
  }

  /** Called when a captured keyboard key is cancelled without a matching release. */
  default void onKeyCancel(KeyCode key) {
  }

  default boolean acceptsFocus() {
    return false;
  }

  default void onFocusChanged(boolean focused) {
  }
}
