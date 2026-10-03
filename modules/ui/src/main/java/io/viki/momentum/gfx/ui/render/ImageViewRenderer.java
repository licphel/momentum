package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ImageView;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Renders an {@link ImageView} within its destination bounds.
 *
 * <p>The renderer has no per-view state and is safe to share among views used by the same UI
 * thread.
 */
public final class ImageViewRenderer implements ElementRenderer {
  /** Shared renderer for image views. */
  public static final ImageViewRenderer INSTANCE = new ImageViewRenderer();

  private ImageViewRenderer() {
  }

  /**
   * Draws the image and clips it to the element's visible area.
   *
   * @param graphics graphics context receiving the image
   * @param element element expected to be an {@link ImageView}
   * @param area absolute destination bounds
   */
  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    graphics.pushScissor(area);
    try {
      ((ImageView) element).image().draw(graphics, area.minX(), area.minY(),
          area.width(), area.height());
    } finally {
      graphics.popScissor();
    }
  }
}
