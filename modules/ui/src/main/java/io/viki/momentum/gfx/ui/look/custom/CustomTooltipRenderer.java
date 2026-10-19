package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.TooltipRenderer;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

import java.util.List;

/**
 * Immutable borrowed tooltip skin; rich text measurement and drawing run on the UI thread.
 * The renderer can be shared between tooltips while its drawable remains caller-owned.
 */
public final class CustomTooltipRenderer implements TooltipRenderer {
  private final Drawable2D background;
  private final int paddingX;
  private final int paddingY;
  private final int lineGap;

  /**
   * Creates a tooltip renderer with explicit text spacing.
   *
   * @param background drawable used for the tooltip surface
   * @param paddingX   x inset between the surface edge and text
   * @param paddingY   x inset between the surface edge and text
   * @param lineGap    vertical spacing between adjacent rich-text entries
   */
  public CustomTooltipRenderer(Drawable2D background, int paddingX, int paddingY, int lineGap) {
    this.background = background;
    this.paddingX = paddingX;
    this.paddingY = paddingY;
    this.lineGap = lineGap;
  }

  @Override
  public void render(Graphics graphics, List<Text> values, float x, float y) {
    if (values.isEmpty()) {
      return;
    }
    float width = 0;
    float height = this.lineGap * (values.size() - 1);
    for (Text value : values) {
      Rectangle bounds = value.raster().bounds();
      width = Math.max(width, bounds.width());
      height += bounds.height();
    }
    // The Drawable receives measured content bounds and owns its sizing/texture policy.
    // No generated surface, border or shadow is layered over the supplied background.
    graphics.draw(this.background, Rectangle.of(x, y, width + this.paddingX * 2, height + this.paddingY * 2));
    float textY = y + this.paddingY;
    for (Text value : values) {
      graphics.drawText(value, x + this.paddingX, textY);
      textY += value.raster().bounds().height() + this.lineGap;
    }
  }
}
