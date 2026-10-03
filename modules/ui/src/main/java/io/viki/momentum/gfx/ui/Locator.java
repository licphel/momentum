package io.viki.momentum.gfx.ui;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.math.Vector2;

/**
 * Recomputes an element's bounds when its parent layout size changes.
 *
 * <p>The callback receives logical coordinates in the parent-local space. Implementations may
 * update the supplied element's bounds and may derive placement from the previous and current
 * parent sizes.
 */
@FunctionalInterface
public interface Locator {
  /**
   * Updates an element in response to a parent-size change.
   *
   * @param element element whose bounds should be recomputed
   * @param oldSize previous parent size in logical coordinates
   * @param newSize current parent size in logical coordinates
   */
  void locate(Element element, Vector2 oldSize, Vector2 newSize);
}
