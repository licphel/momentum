package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ScrollPane;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable viewport skin; content and scrollbar skins are configured separately on the UI thread. */
public final class CustomScrollPaneRenderer implements ElementRenderer {
  private final Drawable2D background;

  /**
   * Creates a renderer for the fixed viewport area.
   *
   * @param background drawable shown behind the scrolling content
   */
  public CustomScrollPaneRenderer(Drawable2D background) {
    this.background = background;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    ScrollPane pane = (ScrollPane) element;
    // Paint only the fixed viewport. The dispatcher clips content and renders the bars afterwards.
    graphics.draw(this.background, Rectangle.of(area.minX(), area.minY(),
        pane.viewportWidth(), pane.viewportHeight()));
  }
}
