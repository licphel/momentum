package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.tint.Gradient;
import org.jspecify.annotations.Nullable;

/**
 * Provides immutable formatting operations for a text component.
 *
 * <p>Each operation returns a formatted value without modifying its receiver. Rendering follows
 * the fonts' threading requirements.
 *
 * @param <T> the concrete component type returned by fluent methods
 */
public interface FormattableText<T extends FormattableText<T>> extends Text {
  /**
   * Replaces the font used to render this component.
   *
   * @param font the replacement font
   * @return a component with its font
   */
  T font(FontInsta font);

  /**
   * Replaces the gradient used to tint this component.
   *
   * @param gradient the replacement text gradient
   * @return a component with its tint
   */
  T tint(Gradient gradient);

  /**
   * Replaces the font size used to render this component.
   *
   * @param size the replacement font size in logical drawing units
   * @return a component with its size
   */
  T size(float size);

  /**
   * Replaces the font style flags used to render this component.
   *
   * @param flags the replacement style flags from {@link Font}
   * @return a component with its style
   */
  T style(int flags);

  /**
   * Replaces the font shadow style used to render this component.
   *
   * @param shadow the replacement shadow info
   * @return a component with its new shadow
   */
  T shadow(TextShadow shadow);

  /**
   * Replaces the component font.
   *
   * @param font the replacement font
   * @return a component with its font
   */
  default T with(FontInsta font) {
    return font(font);
  }

  /**
   * Replaces the component tint gradient.
   *
   * @param gradient the replacement text gradient
   * @return a component with its tint
   */
  default T with(Gradient gradient) {
    return tint(gradient);
  }

  /**
   * Replaces the component font size.
   *
   * @param size the replacement font size in logical drawing units
   * @return a component with its size
   */
  default T with(float size) {
    return size(size);
  }

  /**
   * Adds style flags to the component's current style bitmask.
   *
   * @param flags the style flags to add from {@link Font}
   * @return a component with its style
   */
  default T with(int flags) {
    return style(format().fontStyle() | flags);
  }

  /**
   * Adds a series of metas to the component.
   *
   * @param values the metas to add
   * @return a component with new metas
   */
  T with(Meta @Nullable ... values);

  /**
   * Copies all formatting attributes from a fixed template into this component.
   *
   * @param template the format whose attributes should be copied
   * @return a component with its formatting
   */
  default T with(TextFormat template) {
    return font(template.font()).tint(template.tint()).style(template.fontStyle()).shadow(template.shadow());
  }

  /**
   * Returns the current formatting attributes as an immutable value.
   *
   * @return the component's current text format
   */
  TextFormat format();
}
