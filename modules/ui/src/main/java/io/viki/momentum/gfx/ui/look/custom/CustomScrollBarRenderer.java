package io.viki.momentum.gfx.ui.look.custom;

import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.ui.present.ElementRenderer;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.ScrollBar;
import io.viki.momentum.gfx.ui.present.Artworks;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/** Immutable track/thumb skin; render only on the owning UI thread. */
public final class CustomScrollBarRenderer implements ElementRenderer {
  private final Drawable2D track;
  private final Artworks thumb;

  /**
   * Creates a scrollbar renderer for both horizontal and vertical bars.
   *
   * @param track drawable used for the track
   * @param thumb state artwork used for the movable thumb
   */
  public CustomScrollBarRenderer(Drawable2D track, Artworks thumb) {
    this.track = track;
    this.thumb = thumb;
  }

  @Override
  public void render(Graphics graphics, Element element, Rectangle area) {
    ScrollBar bar = (ScrollBar) element;
    graphics.draw(this.track, area);
    // Share logical thumb geometry with dragging, for either orientation.
    graphics.draw(this.thumb.select(true, bar.hovered() || bar.focused(), bar.dragging()),
        bar.thumbBounds(area));
  }
}
