package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.CheckBox;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable checked/unchecked skin; render only on the owning UI thread. */
public final class CustomCheckBoxRenderer implements ElementRenderer {
  private static final Alignment LABEL_ALIGNMENT = new Alignment(-1, 0);
  private final Artworks unchecked;
  private final Artworks checked;
  private final int labelGap;

  /**
   * Creates a checkbox renderer with separate state artwork for both values.
   *
   * @param unchecked artwork states for an unchecked box
   * @param checked   artwork states for a checked box
   * @param labelGap  horizontal spacing between the box art and its rich-text label
   */
  public CustomCheckBoxRenderer(Artworks unchecked, Artworks checked, int labelGap) {
    this.unchecked = unchecked;
    this.checked = checked;
    this.labelGap = labelGap;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    CheckBox box = (CheckBox) element;
    float size = box.boxSizeForRender();
    Rectangle boxArea = Rectangle.of(area.minX(), area.centralY() - size / 2, size, size);
    graphics.draw((box.checked() ? this.checked : this.unchecked)
        .select(box.enabled(), box.hovered() || box.focused(), box.pressed()), boxArea);
    // The checked artwork contains the mark; no procedural mark or border is added.
    graphics.drawText(box.label(), boxArea.maxX() + this.labelGap,
        area.centralY(), LABEL_ALIGNMENT);
  }
}
