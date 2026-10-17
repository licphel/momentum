package io.viki.momentum.input;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable and thread-safe binding definition.
 *
 * <p>Controls form a chord; an empty list explicitly unbinds it.
 *
 * @param id stable binding identifier
 * @param controls physical controls that form the binding
 * @param pressPoint analog value at which the binding becomes active
 * @param releasePoint analog value below which the binding becomes inactive
 * @param modifiers modifier matching policy
 */
public record BindingDefinition(
    String id,
    List<String> controls,
    float pressPoint,
    float releasePoint,
    int modifiers
) {
  /**
   * Creates a binding using the default modifier policy.
   *
   * @param id stable binding identifier
   * @param controls physical control paths
   * @param pressPoint analog value at which the binding becomes active
   * @param releasePoint analog value below which the binding becomes inactive
   */
  public BindingDefinition(String id, List<String> controls, float pressPoint, float releasePoint) {
    this(id, controls, pressPoint, releasePoint, InputModifiers.ANY);
  }

  /**
   * Validates and copies the binding definition.
   *
   * @param id stable binding identifier
   * @param controls physical controls that form the binding
   * @param pressPoint analog value at which the binding becomes active
   * @param releasePoint analog value below which the binding becomes inactive
   * @param modifiers modifier matching policy
   */
  public BindingDefinition {
    if (id.isBlank()) {
      throw new IllegalArgumentException("Binding ID must not be blank");
    }
    controls = List.copyOf(controls);
    if (!(releasePoint >= 0 && releasePoint < pressPoint && pressPoint <= 1)) {
      throw new IllegalArgumentException("Binding thresholds require 0 <= release < press <= 1: " + id);
    }
    long distinct = controls.stream().map(path -> ControlPath.parse(path).channel()).distinct().count();
    if (distinct != controls.size()) {
      throw new IllegalArgumentException("Repeated physical channel in binding: " + id);
    }
  }

  /**
   * Creates a binding with the standard press and release thresholds.
   *
   * @param id stable binding identifier
   * @param controls physical control paths
   * @return a binding definition for the supplied controls
   */
  public static BindingDefinition of(String id, String... controls) {
    return new BindingDefinition(id, List.of(controls), 0.5F, 0.4F);
  }

  /**
   * Describes controls using caller-owned translations or glyph labels, never raw expression parsing.
   *
   * @param labels function that converts each parsed control path to display text
   * @return the control descriptions joined with {@code " + "}
   */
  public String displayText(Function<ControlPath, String> labels) {
    return controls.stream().map(ControlPath::parse).map(labels).collect(Collectors.joining(" + "));
  }
}
