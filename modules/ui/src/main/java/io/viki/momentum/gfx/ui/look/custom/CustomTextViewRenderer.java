package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.TextView;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable text-view skin; rich styles and layout cache remain owned by the view on its UI thread. */
public final class CustomTextViewRenderer implements ElementRenderer {
  private final Drawable2D background;

  /**
   * Creates a text-view renderer with a caller-supplied background.
   *
   * @param background drawable painted below the rich text
   */
  public CustomTextViewRenderer(Drawable2D background) {
    this.background = background;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    TextView view = (TextView) element;
    graphics.draw(this.background, area);
    // Use the view's cached rich layout, never flatten it to a String or replace its styles.
    graphics.pushScissor(area);
    try {
      graphics.drawText(view.layoutForRender(area.width()), area.minX(), area.minY());
    } finally {
      graphics.popScissor();
    }
  }
}
