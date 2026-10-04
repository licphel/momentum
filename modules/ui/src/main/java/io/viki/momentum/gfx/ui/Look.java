package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.Element;
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
  private static final TooltipRenderer AUTO_TOOLTIP =
      io.viki.momentum.gfx.ui.look.auto.TooltipRenderer::render;
  private final Map<Class<?>, ElementRenderer> renderers = new HashMap<>();
  private @Nullable TooltipRenderer tooltipRenderer;

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
    this.renderers.putAll(source.renderers);
    tooltipRenderer = source.tooltipRenderer;
  }

  /**
   * Associates an element type with the renderer that supplies its appearance.
   *
   * @param type     element type to style
   * @param renderer renderer used for the type
   * @return this policy for fluent configuration
   */
  public Look register(Class<? extends Element> type, ElementRenderer renderer) {
    this.renderers.put(type, renderer);
    return this;
  }

  /**
   * Assigns the renderer used for tooltips in this look's element tree.
   *
   * @param renderer tooltip presentation to use
   * @return this look for further configuration
   */
  public Look setTooltipRenderer(TooltipRenderer renderer) {
    tooltipRenderer = renderer;
    return this;
  }

  /**
   * Resolves this look's tooltip renderer with global and automatic fallbacks.
   *
   * @return the configured renderer, the global renderer, or the automatic renderer
   */
  public TooltipRenderer tooltipRenderer() {
    if (tooltipRenderer != null) {
      return tooltipRenderer;
    }
    Look global = io.viki.momentum.gfx.ui.look.AutoLook.get();
    return global.tooltipRenderer == null ? AUTO_TOOLTIP : global.tooltipRenderer;
  }

  /**
   * Finds the renderer applicable to an element type.
   *
   * @param type runtime element type to resolve
   * @return the matching renderer, or {@code null} when no registration applies
   */
  public @Nullable ElementRenderer renderer(Class<? extends Element> type) {
    for (@Nullable Class<?> current = type; current != null; current = current.getSuperclass()) {
      ElementRenderer renderer = this.renderers.get(current);
      if (renderer != null) {
        return renderer;
      }
    }
    return null;
  }
}
