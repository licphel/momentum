package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Button;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable four-state button skin with borrowed drawables; use on the owning UI thread. */
public final class CustomButtonRenderer implements ElementRenderer {
  private final Drawable2D idle;
  private final Drawable2D hovered;
  private final Drawable2D pressed;
  private final Drawable2D disabled;

  /**
   * Creates a button renderer with artwork for every interaction state.
   *
   * @param idle     artwork for an idle button
   * @param hovered  artwork for a hovered or focused button
   * @param pressed  artwork for a pressed button
   * @param disabled artwork for a disabled button
   */
  public CustomButtonRenderer(Drawable2D idle, Drawable2D hovered, Drawable2D pressed, Drawable2D disabled) {
    this.idle = idle;
    this.hovered = hovered;
    this.pressed = pressed;
    this.disabled = disabled;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    Button button = (Button) element;
    Drawable2D background = switch (button.state()) {
      case IDLE -> button.focused() ? this.hovered : this.idle;
      case HOVERED -> this.hovered;
      case PRESSED -> this.pressed;
      case DISABLED -> this.disabled;
    };
    graphics.draw(background, area);
    // Labels remain rich Text, preserving the font and styles chosen by the caller.
    graphics.drawText(button.label(), Math.round(area.centralX()), Math.round(area.centralY()),
        Alignment.CENTRAL);
  }
}
