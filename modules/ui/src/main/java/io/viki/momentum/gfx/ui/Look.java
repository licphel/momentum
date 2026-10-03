package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.render.ElementRenderer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the visual policy used to render a UI element tree.
 *
 * <p>A look maps element types to renderers and resolves registrations from the most specific
 * runtime type toward its superclasses. A look is mutable and is intended to be configured and
 * read from one owning UI thread.
 */
public class Look {
  private final Map<Class<?>, ElementRenderer> RENDERERS = new HashMap<>();

  /**
   * Creates an empty rendering policy.
   */
  public Look() {
  }

  /**
   * Creates a rendering policy containing the registrations of another policy.
   *
   * @param source policy whose current registrations are copied
   */
  public Look(Look source) {
    RENDERERS.putAll(source.RENDERERS);
  }

  /**
   * Associates an element type with the renderer that supplies its appearance.
   *
   * @param type element type to style
   * @param renderer renderer used for the type
   * @return this policy for fluent configuration
   */
  public Look register(Class<? extends Element> type, ElementRenderer renderer) {
    RENDERERS.put(type, renderer);
    return this;
  }

  /**
   * Finds the renderer applicable to an element type.
   *
   * @param type runtime element type to resolve
   * @return the matching renderer, or {@code null} when no registration applies
   */
  public @Nullable ElementRenderer renderer(Class<? extends Element> type) {
    for (@Nullable Class<?> current = type; current != null; current = current.getSuperclass()) {
      ElementRenderer renderer = RENDERERS.get(current);
      if (renderer != null) {
        return renderer;
      }
    }
    return null;
  }
}
