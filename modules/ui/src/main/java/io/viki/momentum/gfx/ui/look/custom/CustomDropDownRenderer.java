package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable drop-down header skin; render only on the owning UI thread. */
public final class CustomDropDownRenderer implements ElementRenderer {
  private static final Alignment LABEL_ALIGNMENT = new Alignment(-1, 0);
  private final Artworks background;
  private final int textPadding;

  /**
   * Creates a drop-down header renderer with state artwork and label inset.
   *
   * @param background  state artwork for the header
   * @param textPadding horizontal inset before the selected option label
   */
  public CustomDropDownRenderer(Artworks background, int textPadding) {
    this.background = background;
    this.textPadding = textPadding;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    DropDown menu = (DropDown) element;
    // pressed represents expansion. Any expand indicator is part of the supplied drawable.
    graphics.draw(this.background.select(menu.enabled(), menu.hovered() || menu.focused(),
        menu.expanded()), area);
    graphics.drawText(menu.selectedOption(), area.minX() + this.textPadding,
        area.centralY(), LABEL_ALIGNMENT);
  }
}
