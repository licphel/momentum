package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable popup surface; render only on the owning UI thread. */
public final class CustomDropDownPopupRenderer implements ElementRenderer {
  private final Drawable2D background;

  /**
   * Creates a renderer for the popup surface.
   *
   * @param background drawable used behind the option rows
   */
  public CustomDropDownPopupRenderer(Drawable2D background) {
    this.background = background;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    if (((DropDown.Popup) element).owner().expanded()) {
      graphics.draw(this.background, area);
    }
  }
}
