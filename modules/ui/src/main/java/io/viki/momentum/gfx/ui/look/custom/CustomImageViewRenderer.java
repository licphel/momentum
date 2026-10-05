package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ImageView;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable image-view skin; both its background and the view's image are borrowed UI-thread resources. */
public final class CustomImageViewRenderer implements ElementRenderer {
  private final Drawable2D background;

  /**
   * Creates an image renderer with a caller-supplied background.
   *
   * @param background drawable painted below the image
   */
  public CustomImageViewRenderer(Drawable2D background) {
    this.background = background;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    graphics.draw(this.background, area);
    graphics.pushScissor(area);
    try {
      graphics.draw(((ImageView) element).image(), area);
    } finally {
      graphics.popScissor();
    }
  }
}
