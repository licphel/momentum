package io.viki.momentum.input;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores request-local input states without depending on a window or frame simulation.
 * Instances are confined to their owning thread.
 */
public final class HeadlessInputMapping implements InputMapping {
  public static final Input EMPTY = new Empty();

  final Map<Action, Input> inputs = new LinkedHashMap<>();

  /**
   * Copies a supported input value into an independent immutable state value.
   *
   * @param input input state to copy
   * @return immutable key or wheel state copy
   * @throws IllegalArgumentException if {@code input} is not a supported input type
   */
  static Input copy(Input input) {
    if (input instanceof Key key) {
      return new KeyState(key.value(), key.isDown(), key.transitioned(), key.repeated(), key.released());
    }
    if (input instanceof Wheel wheel) {
      return new WheelState(wheel.x(), wheel.y(), wheel.deltaX(), wheel.deltaY(), wheel.persistent());
    }
    throw new IllegalArgumentException("Unsupported input type: " + input.getClass().getName());
  }

  /**
   * Returns the state associated with an action, or an inert state when it is unset.
   *
   * @param action logical action to query
   * @param <T> expected input subtype
   * @return stored input state or an inert default state
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T extends Input> T of(Action action) {
    return (T) inputs.getOrDefault(action, EMPTY);
  }

  /**
   * Associates an input state with an action.
   *
   * @param action logical action to update
   * @param input state supplied for the action
   * @return this mapping
   */
  public HeadlessInputMapping set(Action action, Input input) {
    inputs.put(action, input);
    return this;
  }

  /**
   * Immutable key state captured for a headless input mapping.
   *
   * @param value        strongest current key value
   * @param isDown       whether the key is held
   * @param transitioned whether the key was pressed this frame
   * @param repeated     whether the key repeated this frame
   * @param released     whether the key was released this frame
   */
  public record KeyState(float value, boolean isDown, boolean transitioned,
                         boolean repeated, boolean released) implements Key {
  }

  /**
   * Immutable wheel state captured for a headless input mapping.
   *
   * @param x          retained horizontal position
   * @param y          retained vertical position
   * @param deltaX     horizontal change for the frame
   * @param deltaY     vertical change for the frame
   * @param persistent whether the position persists between frames
   */
  public record WheelState(double x, double y, double deltaX, double deltaY,
                           boolean persistent) implements Wheel {
  }

  private static final class Empty implements Key, Wheel {
    @Override
    public float value() {
      return 0;
    }

    @Override
    public boolean isDown() {
      return false;
    }

    @Override
    public boolean transitioned() {
      return false;
    }

    @Override
    public boolean repeated() {
      return false;
    }

    @Override
    public boolean released() {
      return false;
    }

    @Override
    public double deltaX() {
      return 0;
    }

    @Override
    public double deltaY() {
      return 0;
    }

    @Override
    public double x() {
      return 0;
    }

    @Override
    public double y() {
      return 0;
    }

    @Override
    public boolean persistent() {
      return false;
    }
  }
}
