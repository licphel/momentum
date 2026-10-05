package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.DropDown;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable popup-row skin; render only on the owning UI thread. */
public final class CustomDropDownOptionRenderer implements ElementRenderer {
  private static final Alignment LABEL_ALIGNMENT = new Alignment(-1, 0);
  private final Artworks background;
  private final int textPadding;

  /**
   * Creates an option renderer with state artwork and label inset.
   *
   * @param background  state artwork for each option row
   * @param textPadding horizontal inset before each rich-text label
   */
  public CustomDropDownOptionRenderer(Artworks background, int textPadding) {
    this.background = background;
    this.textPadding = textPadding;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    DropDown.OptionPart option = (DropDown.OptionPart) element;
    DropDown owner = option.owner();
    if (!owner.expanded()) {
      return;
    }
    // pressed represents the selected row; hover remains independent for other rows.
    graphics.draw(this.background.select(owner.enabled(), option.optionHovered(),
        option.index() == owner.selectedIndex()), area);
    graphics.drawText(owner.options().get(option.index()), area.minX() + this.textPadding,
        area.centralY(), LABEL_ALIGNMENT);
  }
}
