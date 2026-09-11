/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 */
package io.viki.momentum.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the observable behavior of {@link KeyBinding}. */
public final class KeyBindingTest {
  @Test
  void combinesKeyMatchesWithOrAndCanBeRebound() {
    InputSnapshot snapshot = new InputSnapshot();
    Key a = snapshot.key(KeyCode.A);
    Key b = snapshot.key(KeyCode.B);
    Key c = snapshot.key(KeyCode.C);
    KeyBinding binding = new KeyBinding("ui.action", KeyMatch.of(a, b), KeyMatch.of(c));

    snapshot.applyKey(KeyCode.A, KeyAction.PRESS, InputModifiers.NONE);
    assertTrue(a.transitioned());
    assertFalse(binding.isDown());
    assertFalse(binding.transitioned());

    snapshot.applyKey(KeyCode.B, KeyAction.PRESS, InputModifiers.NONE);
    assertTrue(binding.isDown());
    assertTrue(binding.transitioned());
    snapshot.clearFrameState();

    snapshot.applyKey(KeyCode.C, KeyAction.PRESS, InputModifiers.NONE);
    assertTrue(binding.transitioned());

    binding.rebind(KeyMatch.of(snapshot.key(KeyCode.SPACE)));
    assertFalse(binding.isDown());
    snapshot.applyKey(KeyCode.SPACE, KeyAction.PRESS, InputModifiers.NONE);
    assertTrue(binding.transitioned());
  }

  @Test
  void modifierChordRequiresItsExactModifierSet() {
    InputSnapshot snapshot = new InputSnapshot();
    Key save = snapshot.key(KeyCode.S);
    KeyBinding binding = new KeyBinding("ui.save", KeyMatch.of(InputModifiers.CONTROL, save));

    snapshot.applyKey(KeyCode.S, KeyAction.PRESS, InputModifiers.NONE);
    assertFalse(binding.isDown());
    snapshot.applyKey(KeyCode.S, KeyAction.RELEASE, InputModifiers.NONE);
    snapshot.applyKey(KeyCode.LEFT_CONTROL, KeyAction.PRESS, InputModifiers.CONTROL);
    snapshot.applyKey(KeyCode.S, KeyAction.PRESS, InputModifiers.CONTROL);

    assertTrue(binding.isDown());
    assertTrue(binding.transitioned());
  }
}
