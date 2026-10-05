package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.Window;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable window skin; render only on the owning UI thread. Backdrop effects belong to the dispatcher. */
public final class CustomWindowRenderer implements ElementRenderer {
  private static final Alignment TITLE_ALIGNMENT = new Alignment(-1, 0);
  private final Drawable2D background;
  private final Drawable2D titleBar;
  private final Artworks close;
  private final Artworks minimize;
  private final int titlePadding;

  /**
   * Creates a window renderer with separate body, title bar, and control artwork.
   *
   * @param background   drawable for the window body
   * @param titleBar     drawable for the title bar
   * @param close        interaction artwork for the close control
   * @param minimize     interaction artwork for the minimize control
   * @param titlePadding horizontal inset before the title
   */
  public CustomWindowRenderer(Drawable2D background, Drawable2D titleBar,
                              Artworks close, Artworks minimize, int titlePadding) {
    this.background = background;
    this.titleBar = titleBar;
    this.close = close;
    this.minimize = minimize;
    this.titlePadding = titlePadding;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    Window window = (Window) element;
    graphics.draw(this.background, area);
    float height = window.titleHeightForRender();
    Rectangle titleArea = Rectangle.of(area.minX(), area.minY(), area.width(), height);
    graphics.draw(this.titleBar, titleArea);
    graphics.drawText(window.title(), area.minX() + this.titlePadding,
        titleArea.centralY(), TITLE_ALIGNMENT);
    // Keep control rectangles identical to the window's input regions.
    // Icons, hover effects and borders are all provided by the artwork.
    if (window.closable()) {
      graphics.draw(this.close.select(true, window.closeHovered(), window.closePressed()),
          Rectangle.of(area.maxX() - height, area.minY(), height, height));
    }
    if (window.minimizable()) {
      graphics.draw(this.minimize.select(true, window.minimizeHovered(), window.minimizePressed()),
          Rectangle.of(area.maxX() - height * (window.closable() ? 2 : 1),
              area.minY(), height, height));
    }
  }
}
