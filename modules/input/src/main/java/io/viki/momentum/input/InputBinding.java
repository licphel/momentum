package io.viki.momentum.input;

import java.util.List;

/**
 * Logical input owned by one snapshot. Owner-thread only; queries never consume physical state.
 */
public final class InputBinding implements Transitable {
  private final InputSnapshot owner;
  private final String id;
  private final List<BindingDefinition> bindings;
  private boolean pendingDown;
  private boolean down;
  private boolean pressed;
  private boolean released;
  private boolean repeated;
  private float strength;
  private double heldTime;
  private double nextRepeat;
  private double repeatDelay = 0.4;
  private double repeatInterval = 0.08;
  private String group = "";
  private boolean enabled = true;
  private Alternative[] alternatives = new Alternative[0];

  InputBinding(InputSnapshot owner, String id, List<BindingDefinition> bindings) {
    if (id.isBlank()) {
      throw new IllegalArgumentException("Action ID must not be blank");
    }
    this.id = id;
    this.owner = owner;
    this.bindings = List.copyOf(bindings);
  }

  /**
   * Returns this action's stable identifier.
   *
   * @return action identifier
   */
  public String id() {
    return id;
  }

  /**
   * Returns the action group.
   *
   * @return group identifier, or the empty string when ungrouped
   */
  public String group() {
    return group;
  }

  /**
   * Assigns this action to a group.
   *
   * @param group group identifier
   * @return this binding
   */
  public InputBinding group(String group) {
    this.group = group;
    owner.updateBindings(0);
    return this;
  }

  /**
   * Returns whether this action is enabled, including its group state.
   *
   * @return whether this action can contribute input
   */
  public boolean enabled() {
    return enabled && owner.isGroupEnabled(group);
  }

  /**
   * Enables or disables this action.
   *
   * @param enabled whether the action should contribute input
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    owner.updateBindings(0);
  }

  /**
   * Enables or disables this action's group.
   *
   * @param enabled whether the group should contribute input
   */
  public void setGroupEnabled(boolean enabled) {
    owner.setGroupEnabled(group, enabled);
  }

  void compile(InputProfile profile) {
    List<BindingDefinition> definitions = profile.bindings().getOrDefault(id, bindings);
    alternatives = new Alternative[definitions.size()];
    for (int i = 0; i < alternatives.length; i++) {
      alternatives[i] = new Alternative(definitions.get(i));
    }
  }

  void update(double deltaSeconds) {
    beginUpdate();
    if (enabled()) {
      for (Alternative alternative : alternatives) {
        alternative.apply(owner, this);
      }
    } else {
      pressed = repeated = false;
      for (Alternative alternative : alternatives) {
        alternative.held = false;
      }
    }
    finishUpdate(deltaSeconds);
  }

  /**
   * Returns the immutable default alternatives for this action.
   *
   * @return default binding definitions
   */
  public List<BindingDefinition> defaultBindings() {
    return bindings;
  }

  /**
   * Returns the strongest current control value.
   *
   * @return current action strength
   */
  public float value() {
    return strength;
  }

  /**
   * Returns whether this action is currently held.
   *
   * @return whether the action is held
   */
  @Override
  public boolean isDown() {
    return down;
  }

  /**
   * Returns whether this action was pressed during the current frame.
   *
   * @return whether the action transitioned to held
   */
  @Override
  public boolean transitioned() {
    return pressed;
  }

  /**
   * Returns whether this action was pressed or repeated during the current frame.
   *
   * @return whether the action transitioned or repeated
   */
  @Override
  public boolean transitionedOrRepeated() {
    return pressed || repeated;
  }

  /**
   * Returns whether this action was released during the current frame.
   *
   * @return whether the action transitioned to released
   */
  @Override
  public boolean released() {
    return released;
  }

  /**
   * Configures repeated transition timing while the action is held.
   *
   * @param delaySeconds time before the first repeat
   * @param intervalSeconds time between subsequent repeats
   * @throws IllegalArgumentException if either timing value is invalid
   */
  public void setRepeat(double delaySeconds, double intervalSeconds) {
    if (delaySeconds < 0 || intervalSeconds <= 0) {
      throw new IllegalArgumentException("Invalid action repeat timing: " + id);
    }
    repeatDelay = delaySeconds;
    repeatInterval = intervalSeconds;
  }

  void beginUpdate() {
    strength = 0;
    pendingDown = false;
  }

  void contribute(boolean held, float value) {
    pendingDown |= held;
    if (Math.abs(value) > Math.abs(strength)) {
      strength = value;
    }
  }

  void endFrame() {
    pressed = released = repeated = false;
  }

  /** Applies edge, release, and repeat transitions after all alternatives contribute. */
  void finishUpdate(double deltaSeconds) {
    boolean justPressed = pendingDown && !down;
    pressed |= justPressed;
    released |= !pendingDown && down;
    if (justPressed) {
      heldTime = 0;
      nextRepeat = repeatDelay;
    } else if (pendingDown) {
      heldTime += deltaSeconds;
      if (heldTime >= nextRepeat) {
        repeated = true;
        nextRepeat += (Math.floor((heldTime - nextRepeat) / repeatInterval) + 1) * repeatInterval;
      }
    }
    down = pendingDown;
  }

  private static final class Alternative {
    private final BindingDefinition definition;
    private final ControlPath[] controls;
    private boolean held;

    private Alternative(BindingDefinition definition) {
      this.definition = definition;
      controls = definition.controls().stream().map(ControlPath::parse).toArray(ControlPath[]::new);
    }

    /** Applies this alternative's chord and hysteresis state to its logical binding. */
    private void apply(InputSnapshot input, InputBinding binding) {
      if (controls.length == 0) {
        return;
      }
      float threshold = held ? definition.releasePoint() : definition.pressPoint();
      boolean matches = definition.modifiers() == InputModifiers.ANY
          || input.keyboard().mods() == definition.modifiers();
      for (int i = 1; matches && i < controls.length; i++) {
        matches = controls[i].isDown(input, threshold);
      }
      held = matches && controls[0].isDown(input, threshold);
      if (matches) {
        binding.contribute(held, controls[0].value(input));
      }
    }
  }
}
