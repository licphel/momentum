package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.text.TextFormat;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.TextBox;
import io.viki.momentum.gfx.ui.present.TextBoxContent;
import io.viki.momentum.gfx.ui.present.TextBoxPresentation;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Immutable textbox skin with a borrowed background and shared editor presentation.
 * Use on the UI thread. The supplied font controls both rendering and input geometry.
 */
public final class CustomTextBoxRenderer implements ElementRenderer, TextBoxPresentation {
  private final Drawable2D background;
  private final TextBoxContent content;

  /**
   * Creates a textbox skin with the supplied editor font and formatting.
   *
   * @param background drawable shown behind editor content
   * @param format     text format used for editor content, measurement, and pointer hit testing
   */
  public CustomTextBoxRenderer(Drawable2D background, TextFormat format) {
    this.background = background;
    this.content = new TextBoxContent(format);
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    // Custom skins never paint the automatic control frame, including inside scroll panes.
    graphics.draw(this.background, area);
    this.content.render(graphics, element, area);
  }

  @Override
  public int hitIndex(TextBox textBox, float x, float y) {
    return this.content.hitIndex(textBox, x, y);
  }

  @Override
  public void scrollCursorIntoView(TextBox textBox) {
    this.content.scrollCursorIntoView(textBox);
  }
}
