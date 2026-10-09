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

package io.viki.momentum.gfx.ui.look;

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.present.TooltipRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.look.auto.AutoTooltipRenderer;
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
  private static final TooltipRenderer AUTO_TOOLTIP_RENDERER =
      AutoTooltipRenderer::render;
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
    Look global = AutoLook.get();
    return global.tooltipRenderer == null ? AUTO_TOOLTIP_RENDERER : global.tooltipRenderer;
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
