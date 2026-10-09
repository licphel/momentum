package io.viki.momentum.gfx.text;

import io.viki.momentum.gfx.tint.Gradient;

/**
 * Provides fluent mutation of the formatting attributes for a text component.
 *
 * <p>Implementations are confined to their owning UI thread and are not thread-safe. Each
 * mutator returns the concrete component type so several formatting changes can be chained.
 *
 * @param <T> the concrete component type returned by fluent methods
 */
public interface FormattableText<T extends FormattableText<T>> extends Text {
  /**
   * Replaces the font used to render this component.
   *
   * @param font the replacement font
   * @return this component after updating its font
   */
  T font(FontInsta font);

  /**
   * Replaces the gradient used to tint this component.
   *
   * @param gradient the replacement text gradient
   * @return this component after updating its tint
   */
  T tint(Gradient gradient);

  /**
   * Replaces the font size used to render this component.
   *
   * @param size the replacement font size in logical drawing units
   * @return this component after updating its size
   */
  T size(float size);

  /**
   * Replaces the font style flags used to render this component.
   *
   * @param flags the replacement style flags from {@link Font}
   * @return this component after updating its style
   */
  T style(int flags);

  /**
   * Replaces the component font.
   *
   * @param font the replacement font
   * @return this component after updating its font
   */
  default T with(FontInsta font) {
    return font(font);
  }

  /**
   * Replaces the component tint gradient.
   *
   * @param gradient the replacement text gradient
   * @return this component after updating its tint
   */
  default T with(Gradient gradient) {
    return tint(gradient);
  }

  /**
   * Replaces the component font size.
   *
   * @param size the replacement font size in logical drawing units
   * @return this component after updating its size
   */
  default T with(float size) {
    return size(size);
  }

  /**
   * Adds style flags to the component's current style bitmask.
   *
   * @param flags the style flags to add from {@link Font}
   * @return this component after updating its style
   */
  default T with(int flags) {
    return style(format().fontStyle() | flags);
  }

  /**
   * Copies all formatting attributes from a fixed template into this component.
   *
   * @param template the format whose attributes should be copied
   * @return this component after updating its formatting
   */
  default T with(TextFormat template) {
    font(template.font());
    tint(template.tint());
    return style(template.fontStyle());
  }

  /**
   * Returns the current formatting attributes as an immutable value.
   *
   * @return the component's current text format
   */
  TextFormat format();
}
