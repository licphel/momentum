package io.viki.momentum.input.binding;

import io.viki.momentum.codec.Codec;
import io.viki.momentum.input.*;

import java.util.*;

/**
 * Owns client bindings and their logical state.
 * All access belongs to the input thread.
 */
public final class InputBindings implements InputMapping {
  private static final Codec<BindingDefinition> DEFINITION_CODEC = Codec.<BindingDefinition>record()
      .field("id", Codec.STRING, BindingDefinition::id)
      .field("controls", Codec.stringList(), BindingDefinition::controls)
      .optionalField("pressPoint", Codec.FLOAT, BindingDefinition::pressPoint, 0.5F)
      .optionalField("releasePoint", Codec.FLOAT, BindingDefinition::releasePoint, 0.4F)
      .optionalField("modifiers", Codec.INT, BindingDefinition::modifiers, InputModifiers.ANY)
      .create(v -> new BindingDefinition(v.get("id"), v.get("controls"), v.get("pressPoint"),
          v.get("releasePoint"), v.get("modifiers")));
  private static final Codec<Map<String, List<BindingDefinition>>> DEFINITIONS =
      DEFINITION_CODEC.listOf().mapOf();
  /** Codec for persistent binding definitions and their action associations. */
  public static final Codec<InputBindings> CODEC = Codec.object(value -> {
    var tag = new io.viki.momentum.codec.nbt.CompoundNBT();
    Map<String, List<BindingDefinition>> definitions = new LinkedHashMap<>();
    value.bindings.forEach((action, bound) -> definitions.put(action.name(), bound.definitions));
    DEFINITIONS.writeField(tag, "bindings", definitions);
    return tag;
  }, tag -> {
    InputBindings result = new InputBindings();
    DEFINITIONS.readField(tag, "bindings").forEach((id, definitions) ->
        result.bind(new Action(id), definitions.toArray(BindingDefinition[]::new)));
    return result;
  });

  private final Map<Action, Bound> bindings = new LinkedHashMap<>();

  /**
   * Creates the live state representation for a set of definitions.
   *
   * @param definitions binding alternatives
   * @return key- or wheel-backed live state
   */
  private static Bound create(List<BindingDefinition> definitions) {
    Source[] sources = createSources0(definitions);
    return sources.length > 0 && sources[0].isWheel()
        ? new BoundWheel(definitions, sources) : new BoundKey(definitions, sources);
  }

  /**
   * Validates definitions and converts their controls into physical sources.
   *
   * @param definitions binding alternatives to compile
   * @return compiled physical sources
   * @throws IllegalArgumentException if definitions conflict or mix key and wheel controls
   */
  private static Source[] createSources0(List<BindingDefinition> definitions) {
    Set<String> ids = new HashSet<>();
    List<Source> result = new ArrayList<>();
    for (BindingDefinition definition : definitions) {
      if (!ids.add(definition.id())) {
        throw new IllegalArgumentException("Duplicate binding: " + definition.id());
      }
      if (definition.controls().isEmpty()) {
        continue;
      }
      Source source = new Source(definition);
      if (!result.isEmpty() && source.isWheel() != result.get(0).isWheel()) {
        throw new IllegalArgumentException("An action cannot combine Key and Wheel bindings");
      }
      result.add(source);
    }
    return result.toArray(Source[]::new);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Input> T of(Action action) {
    Bound bound = bindings.get(action);
    if (bound == null) {
      throw new IllegalArgumentException("Unbound action: " + action.name());
    }
    return (T) bound.input();
  }

  /**
   * Adds an action binding, combining controls within each definition and definitions as alternatives.
   *
   * @param action logical action to bind
   * @param definitions binding alternatives for the action
   * @param <T> expected input subtype
   * @return the action's live input state
   * @throws IllegalArgumentException if the action is already bound to a different definition
   */
  public <T extends Input> T bind(Action action, BindingDefinition... definitions) {
    List<BindingDefinition> values = List.of(definitions);
    Bound existing = bindings.get(action);
    if (existing != null) {
      if (!existing.definitions.equals(values)) {
        throw new IllegalArgumentException("Conflicting binding: " + action);
      }
      return of(action);
    }
    bindings.put(action, create(values));
    return of(action);
  }

  /**
   * Replaces an action's configuration while preserving its live input object.
   *
   * @param action logical action to rebind
   * @param definitions replacement binding definitions
   * @throws IllegalArgumentException if the action is unbound or changes between key and wheel input
   */
  public void rebind(Action action, BindingDefinition... definitions) {
    Bound existing = bindings.get(action);
    if (existing == null) {
      throw new IllegalArgumentException("Unbound action: " + action.name());
    }
    List<BindingDefinition> values = List.of(definitions);
    Source[] sources = createSources0(values);
    if (sources.length != 0 && (existing instanceof BoundWheel) != sources[0].isWheel()) {
      throw new IllegalArgumentException("Rebinding cannot change an action between Key and Wheel: " + action);
    }
    existing.definitions = values;
    existing.sources = sources;
    existing.clear();
  }

  /**
   * Applies decoded settings to registered actions while retaining unmentioned defaults.
   *
   * @param settings settings whose matching actions should replace their definitions
   */
  public void rebind(InputBindings settings) {
    settings.bindings.forEach((action, bound) -> {
      if (bindings.containsKey(action)) {
        rebind(action, bound.definitions.toArray(BindingDefinition[]::new));
      } else {
        bind(action, bound.definitions.toArray(BindingDefinition[]::new));
      }
    });
  }

  /**
   * Begins interactive capture for an action.
   *
   * @param action action whose binding is being replaced
   * @param snapshot current input state used to ignore controls already held
   * @return capture session whose result can be applied with {@link #rebind(Action, BindingDefinition[])}
   * @throws IllegalArgumentException if the action is unbound
   */
  public InputRebind rebind(Action action, InputSnapshot snapshot) {
    Bound bound = bindings.get(action);
    if (bound == null) {
      throw new IllegalArgumentException("Unbound action: " + action);
    }
    BindingDefinition original = bound.definitions.isEmpty()
        ? BindingDefinition.of(action.name()) : bound.definitions.get(0);
    return new InputRebind(original, snapshot);
  }

  /**
   * Advances every binding using the current input snapshot.
   *
   * @param snapshot current physical input state
   * @param deltaSeconds elapsed time since the previous update
   */
  public void update(InputSnapshot snapshot, double deltaSeconds) {
    for (Bound bound : bindings.values()) {
      bound.update(snapshot, deltaSeconds);
    }
  }

  /** Clears transient press, release, and repeat state for every binding. */
  public void clearFrameState() {
    for (Bound bound : bindings.values()) {
      bound.clearFrame();
    }
  }

  private abstract static class Bound {
    List<BindingDefinition> definitions;
    Source[] sources;

    Bound(List<BindingDefinition> definitions, Source[] sources) {
      this.definitions = definitions;
      this.sources = sources;
    }

    abstract Input input();

    abstract void update(InputSnapshot snapshot, double delta);

    abstract void clearFrame();

    abstract void clear();
  }

  private static final class BoundKey extends Bound implements Key {
    private static final double REPEAT_DELAY = 0.4;
    private static final double REPEAT_INTERVAL = 0.08;
    private boolean down, pressed, released, repeated;
    private float value;
    private double heldTime;
    private double nextRepeat;

    BoundKey(List<BindingDefinition> definitions, Source[] sources) {
      super(definitions, sources);
    }

    Input input() {
      return this;
    }

    void update(InputSnapshot snapshot, double delta) {
      boolean nextDown = false;
      value = 0;
      for (Source source : sources) {
        source.update(snapshot);
        if (!source.enabled) {
          continue;
        }
        nextDown |= source.held;
        if (Math.abs(source.value) > Math.abs(value)) {
          value = source.value;
        }
        pressed |= source.control.pressed(snapshot);
        released |= source.control.released(snapshot) && (down || pressed);
      }
      pressed |= nextDown && !down;
      released |= !nextDown && down;
      if (nextDown && !down) {
        heldTime = 0;
        nextRepeat = REPEAT_DELAY;
      } else if (nextDown) {
        heldTime += delta;
        if (heldTime >= nextRepeat) {
          repeated = true;
          nextRepeat += (Math.floor((heldTime - nextRepeat) / REPEAT_INTERVAL) + 1) * REPEAT_INTERVAL;
        }
      }
      down = nextDown;
    }

    void clearFrame() {
      pressed = released = repeated = false;
    }

    void clear() {
      clearFrame();
      down = false;
      value = 0;
      heldTime = nextRepeat = 0;
    }

    @Override
    public float value() {
      return value;
    }

    @Override
    public boolean isDown() {
      return down;
    }

    @Override
    public boolean transitioned() {
      return pressed;
    }

    @Override
    public boolean repeated() {
      return repeated;
    }

    @Override
    public boolean released() {
      return released;
    }
  }

  private static final class BoundWheel extends Bound implements Wheel {
    private double x, y, dx, dy;
    private boolean persistent;

    BoundWheel(List<BindingDefinition> definitions, Source[] sources) {
      super(definitions, sources);
    }

    Input input() {
      return this;
    }

    void update(InputSnapshot snapshot, double delta) {
      clear();
      for (Source source : sources) {
        source.update(snapshot);
        if (!source.enabled) {
          continue;
        }
        Wheel wheel = source.control.wheel(snapshot);
        x += wheel.x();
        y += wheel.y();
        dx += wheel.deltaX();
        dy += wheel.deltaY();
        persistent |= wheel.persistent();
      }
    }

    void clearFrame() {
      dx = dy = 0;
    }

    void clear() {
      x = y = dx = dy = 0;
      persistent = false;
    }

    @Override
    public double deltaX() {
      return dx;
    }

    @Override
    public double deltaY() {
      return dy;
    }

    @Override
    public double x() {
      return x;
    }

    @Override
    public double y() {
      return y;
    }

    @Override
    public boolean persistent() {
      return persistent;
    }
  }

  /** One physical source plus any required chord controls. It owns only source hysteresis. */
  private static final class Source {
    private final BindingDefinition definition;
    private final ControlPath control;
    private final ControlPath[] chord;
    private boolean enabled, held;
    private float value;

    Source(BindingDefinition definition) {
      this.definition = definition;
      List<String> paths = definition.controls();
      control = ControlPath.parse(paths.get(0));
      chord = paths.stream().skip(1).map(ControlPath::parse).toArray(ControlPath[]::new);
      for (ControlPath path : chord) {
        if (path.isWheel()) {
          throw new IllegalArgumentException("A Wheel must be the first control of its binding");
        }
      }
    }

    boolean isWheel() {
      return control.isWheel();
    }

    void update(InputSnapshot snapshot) {
      int required = definition.modifiers();
      int actual = snapshot.keyboard().mods();
      enabled = required == InputModifiers.ANY
          || (required == InputModifiers.NONE ? actual == 0 : (actual & required) == required);
      float threshold = held ? definition.releasePoint() : definition.pressPoint();
      for (ControlPath path : chord) {
        enabled &= path.isDown(snapshot, threshold);
      }
      held = enabled && !isWheel() && control.isDown(snapshot, threshold);
      value = enabled && !isWheel() ? control.value(snapshot) : 0;
    }
  }
}
