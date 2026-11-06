/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.input.binding;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable and thread-safe binding definition.
 *
 * <p>Controls form a chord; an empty list explicitly unbinds it.
 *
 * @param id           stable binding identifier
 * @param controls     physical controls that form the binding
 * @param pressPoint   analog value at which the binding becomes active
 * @param releasePoint analog value below which the binding becomes inactive
 * @param modifiers    modifier matching policy
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
   * @param id           stable binding identifier
   * @param controls     physical control paths
   * @param pressPoint   analog value at which the binding becomes active
   * @param releasePoint analog value below which the binding becomes inactive
   */
  public BindingDefinition(String id, List<String> controls, float pressPoint, float releasePoint) {
    this(id, controls, pressPoint, releasePoint, InputModifiers.ANY);
  }

  /**
   * Validates and copies the binding definition.
   *
   * @param id           stable binding identifier
   * @param controls     physical controls that form the binding
   * @param pressPoint   analog value at which the binding becomes active
   * @param releasePoint analog value below which the binding becomes inactive
   * @param modifiers    modifier matching policy
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
   * @param id       stable binding identifier
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
