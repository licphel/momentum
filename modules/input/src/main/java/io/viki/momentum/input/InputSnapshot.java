package io.viki.momentum.input;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Instance-owned devices and logical bindings.
 *
 * <p>All access belongs to the owning input thread.
 */
public final class InputSnapshot {
  /** Number of raw joystick slots maintained by this snapshot. */
  public static final int JOYSTICK_SLOTS = 16;
  private final JoystickState[] joysticks = new JoystickState[JOYSTICK_SLOTS];
  private final JoystickState emptyJoystick = new JoystickState();
  private final Map<String, InputBinding> actions = new LinkedHashMap<>();
  private final List<InputBinding> bindings = new ArrayList<>();
  private final Set<String> disabledGroups = new HashSet<>();
  private InputProfile profile = InputProfile.EMPTY;
  private int selectedJoystick = -1;
  private int activeJoystick = -1;
  private boolean resetting;
  private final KeyboardState keyboard = new KeyboardState(this::deviceChanged);
  private final MouseState mouse = new MouseState(keyboard, this::deviceChanged);
  private final GamepadState gamepad = new GamepadState(this::deviceChanged);

  /** Creates an input snapshot with empty device state and no bindings. */
  public InputSnapshot() {
    for (int i = 0; i < joysticks.length; i++) {
      joysticks[i] = new JoystickState(this::deviceChanged);
    }
  }

  private static void checkJoystickSlot(int slot) {
    if (slot < -1 || slot >= JOYSTICK_SLOTS) {
      throw new IllegalArgumentException("Joystick slot out of range: " + slot);
    }
  }

  /**
   * Returns the keyboard state.
   *
   * @return keyboard state owned by this snapshot
   */
  public KeyboardState keyboard() {
    return keyboard;
  }

  /**
   * Returns the mouse state.
   *
   * @return mouse state owned by this snapshot
   */
  public MouseState mouse() {
    return mouse;
  }

  /**
   * Returns the mapped gamepad state.
   *
   * @return gamepad state owned by this snapshot
   */
  public GamepadState gamepad() {
    return gamepad;
  }

  /**
   * Returns a raw joystick slot.
   *
   * @param slot joystick slot from {@code 0} through {@link #JOYSTICK_SLOTS} exclusive
   * @return joystick state for the slot
   */
  public JoystickState joystick(int slot) {
    return joysticks[slot];
  }

  /**
   * Returns the selected active joystick, or an empty state when none is active.
   *
   * @return active joystick state
   */
  public JoystickState joystick() {
    return activeJoystick < 0 ? emptyJoystick : joysticks[activeJoystick];
  }

  /**
   * Creates or retrieves a logical input binding.
   *
   * @param id logical action identifier
   * @param sources default physical alternatives for the action
   * @return existing or newly created binding
   * @throws IllegalArgumentException if the identifier or definitions conflict with existing bindings
   */
  public InputBinding binding(String id, BindingDefinition... sources) {
    InputBinding existing = actions.get(id);
    List<BindingDefinition> definitions = List.of(sources);
    if (existing != null) {
      if (!existing.defaultBindings().equals(definitions)) {
        throw new IllegalArgumentException("Conflicting input binding: " + id);
      }
      return existing;
    }
    var ids = new HashSet<String>();
    for (BindingDefinition definition : definitions) {
      if (!ids.add(definition.id())) {
        throw new IllegalArgumentException("Duplicate binding definition: " + definition.id());
      }
    }
    InputBinding binding = new InputBinding(this, id, definitions);
    binding.compile(profile);
    actions.put(id, binding);
    bindings.add(binding);
    return binding;
  }

  /**
   * Tests whether an action group is enabled.
   *
   * @param group group identifier
   * @return whether the group contributes input
   */
  public boolean isGroupEnabled(String group) {
    return !disabledGroups.contains(group);
  }

  /**
   * Enables or disables an action group.
   *
   * @param group group identifier
   * @param enabled whether the group should contribute input
   */
  public void setGroupEnabled(String group, boolean enabled) {
    if (enabled) {
      disabledGroups.remove(group);
    } else {
      disabledGroups.add(group);
    }
    updateBindings(0);
  }

  /**
   * Returns the currently applied profile.
   *
   * @return active input profile
   */
  public InputProfile profile() {
    return profile;
  }

  /**
   * Applies a profile to all registered bindings.
   *
   * @param profile profile to apply
   */
  public void applyProfile(InputProfile profile) {
    this.profile = profile;
    for (InputBinding binding : bindings) {
      binding.compile(profile);
    }
    updateBindings(0);
  }

  /**
   * Replaces all physical alternatives of a registered action.
   * An empty list unbinds the action; using its defaults removes the override.
   *
   * @param id registered action identifier
   * @param sources replacement physical alternatives
   */
  public void rebind(String id, BindingDefinition... sources) {
    InputBinding action = actions.get(id);
    if (action == null) throw new IllegalArgumentException("Unknown input action: " + id);
    List<BindingDefinition> definitions = List.of(sources);
    Map<String, List<BindingDefinition>> overrides = new LinkedHashMap<>(profile.bindings());
    if (action.defaultBindings().equals(definitions)) overrides.remove(id);
    else overrides.put(id, definitions);
    applyProfile(new InputProfile(overrides));
  }

  /**
   * Removes a profile override for a binding identifier.
   *
   * @param id binding identifier
   */
  public void resetBinding(String id) {
    Map<String, List<BindingDefinition>> overrides = new LinkedHashMap<>(profile.bindings());
    overrides.remove(id);
    applyProfile(new InputProfile(overrides));
  }

  /**
   * Loads and applies profile overrides from a file.
   *
   * @param file profile file
   * @throws IOException if the profile cannot be read or parsed
   */
  public void loadBindings(Path file) throws IOException {
    applyProfile(InputProfile.load(file));
  }

  /**
   * Saves the current profile to a file.
   *
   * @param file profile file
   * @throws IOException if the profile cannot be written
   */
  public void saveBindings(Path file) throws IOException {
    profile.save(file);
  }

  /**
   * Advances logical bindings and repeat timing without allocating.
   *
   * <p>Runs in {@code O(bindings + chord controls)} time.
   *
   * @param deltaSeconds elapsed time since the previous update
   * @throws IllegalArgumentException if {@code deltaSeconds} is negative
   */
  public void updateBindings(double deltaSeconds) {
    if (deltaSeconds < 0) {
      throw new IllegalArgumentException("Input delta must not be negative");
    }
    for (InputBinding binding : bindings) {
      binding.update(deltaSeconds);
    }
  }

  private void deviceChanged() {
    if (!resetting) {
      updateBindings(0);
    }
  }

  /**
   * Selects the raw joystick slot for future use.
   *
   * @param slot slot, or {@code -1} to select no slot
   * @throws IllegalArgumentException if the slot is outside the valid range
   */
  public void selectJoystick(int slot) {
    checkJoystickSlot(slot);
    selectedJoystick = slot;
  }

  /**
   * Returns the selected raw joystick slot.
   *
   * @return selected slot, or {@code -1} when none is selected
   */
  public int selectedJoystick() {
    return selectedJoystick;
  }

  /**
   * Returns the raw joystick slot currently used by bindings.
   *
   * @return active slot, or {@code -1} when no raw joystick is active
   */
  public int activeJoystick() {
    return activeJoystick;
  }

  /**
   * Sets the raw joystick slot used by bindings.
   *
   * @param slot slot, or {@code -1} to disable raw joystick input
   * @throws IllegalArgumentException if the slot is outside the valid range
   */
  public void setActiveJoystick(int slot) {
    checkJoystickSlot(slot);
    if (activeJoystick == slot) {
      return;
    }
    activeJoystick = slot;
    gamepad.reset();
    deviceChanged();
  }

  /** Clears transient press, release, and repeat state for the next frame. */
  public void clearFrameState() {
    for (InputBinding binding : bindings) {
      binding.endFrame();
    }
    keyboard.clearFrameState();
    mouse.clearFrameState();
    gamepad.clearFrameState();
    for (JoystickState joystick : joysticks) {
      joystick.clearFrameState();
    }
  }

  /** Clears all device state and disables the active raw joystick. */
  public void clearInputState() {
    resetting = true;
    try {
      keyboard.reset();
      mouse.reset();
      gamepad.reset();
      for (JoystickState joystick : joysticks) {
        joystick.reset();
      }
      activeJoystick = -1;
    } finally {
      resetting = false;
    }
    deviceChanged();
  }

  /**
   * Clears input state when the owning window loses focus.
   *
   * @param focused whether the input window is focused
   */
  public void onFocusChange(boolean focused) {
    if (!focused) {
      clearInputState();
    }
  }
}
