package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable borrowed surface; drawing and resource access belong to the UI thread. */
public final class CustomSurfaceRenderer implements ElementRenderer {
  private final Drawable2D background;

  /**
   * Creates a renderer for a borrowed surface drawable.
   *
   * @param background drawable displayed inside the element bounds
   */
  public CustomSurfaceRenderer(Drawable2D background) {
    this.background = background;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    graphics.draw(this.background, area);
  }
}
