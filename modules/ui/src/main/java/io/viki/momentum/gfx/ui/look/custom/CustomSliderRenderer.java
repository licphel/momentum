package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.Slider;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.Alignment;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable track/thumb skin; render only on the owning UI thread. */
public final class CustomSliderRenderer implements ElementRenderer {
  private static final Alignment VALUE_ALIGNMENT = new Alignment(-1, 0);
  private final Drawable2D track;
  private final Artworks thumb;

  /**
   * Creates a slider renderer with caller-supplied track and thumb artwork.
   *
   * @param track drawable used for the track
   * @param thumb interaction-state artwork used for the thumb
   */
  public CustomSliderRenderer(Drawable2D track, Artworks thumb) {
    this.track = track;
    this.thumb = thumb;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    Slider slider = (Slider) element;
    Rectangle track = slider.trackAreaForRender(area);
    float trackHeight = slider.trackThicknessForRender();
    graphics.draw(this.track, Rectangle.of(track.minX(), track.centralY() - trackHeight / 2,
        track.width(), trackHeight));
    float thumbWidth = slider.thumbWidthForRender();
    // Pointer input uses the whole track axis; center the thumb on that same value position.
    float x = track.minX() + track.width() * slider.normalizedValueForRender() - thumbWidth / 2;
    graphics.draw(this.thumb.select(slider.enabled(), slider.hovered() || slider.focused(),
        slider.dragging()), Rectangle.of(x, track.minY(), thumbWidth, track.height()));
    graphics.drawText(slider.displayValue(), area.maxX() + slider.valueGapForRender(),
        area.centralY(), VALUE_ALIGNMENT);
  }
}
