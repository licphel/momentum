/*
 * MIT License
 *
 * Copyright (c) 2026 Licphel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.viki.momentum.gfx.ui.render;

import io.viki.momentum.gfx.tint.Color;
import io.viki.momentum.gfx.ui.element.Element;
import io.viki.momentum.gfx.ui.element.Slider;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.shape.Rectangle;

/**
 * Draws numeric or discrete slider tracks, thumbs, and formatted values.
 *
 * <p>The slider widget owns value conversion and step handling; this renderer only maps the
 * normalized value to a visual track and reads the widget's interaction state.
 */
public final class SliderRenderer implements ElementRenderer {
  /** Shared stateless renderer used by sliders. */
  public static final SliderRenderer INSTANCE = new SliderRenderer();

  private SliderRenderer() {
  }

  /**
   * Draws a slider using its current value and interaction state.
   *
   * @param graphics graphics context receiving the slider
   * @param raw slider element to render
   * @param area absolute slider bounds
   */
  @Override
  public void render(Graphics graphics, Element raw, Rectangle area) {
    Slider slider = (Slider) raw;
    Rectangle track = slider.trackAreaForRender(area);
    float trackHeight = slider.trackThicknessForRender();
    float thumbWidth = Math.min(track.width(), slider.thumbWidthForRender());
    float value = Math.clamp(slider.normalizedValueForRender(), 0.0F, 1.0F);
    // Draw the hover/pressed/focused state wash behind the slider track.
    drawInteractionSurface(graphics, slider, track);
    // Draw the unfilled slider track.
    drawTrack(graphics, slider, track, trackHeight);
    // Draw the filled portion of the slider track.
    drawTrackFill(graphics, slider, track, trackHeight, value);
    // Draw the rounded slider thumb at the normalized value.
    drawThumb(graphics, slider, track, thumbWidth, value);
    // Draw the formatted value outside the slider bounds.
    drawValue(graphics, slider, area);
  }

  private static void drawInteractionSurface(Graphics graphics, Slider slider, Rectangle track) {
    if (slider.hovered() || slider.dragging() || slider.focused()) {
      RendererSupport.fill(graphics, track,
          slider.dragging() ? RendererSupport.STATE_PRESSED : RendererSupport.STATE_HOVER);
    }
  }

  private static void drawTrack(Graphics graphics, Slider slider, Rectangle track,
                                float trackHeight) {
    Color trackColor = slider.enabled() ? RendererSupport.TRACK
        : new Color(RendererSupport.TRACK, 0.45F);
    float y = track.centralY() - Math.max(1.0F, trackHeight) * 0.5F;
    float h = Math.max(1.0F, trackHeight);
    graphics.setTint(trackColor);
    graphics.drawRectangle(track.minX(), y, track.width(), h);
    graphics.setTint(Color.WHITE);
  }

  private static void drawTrackFill(Graphics graphics, Slider slider, Rectangle track,
                                    float trackHeight, float value) {
    Color fillColor = slider.enabled() ? RendererSupport.TRACK_FILL
        : new Color(RendererSupport.TRACK_FILL, 0.40F);
    float y = track.centralY() - Math.max(1.0F, trackHeight) * 0.5F;
    float h = Math.max(1.0F, trackHeight);
    graphics.setTint(fillColor);
    graphics.drawRectangle(track.minX(), y, track.width() * value, h);
    graphics.setTint(Color.WHITE);
  }

  private static void drawThumb(Graphics graphics, Slider slider, Rectangle track,
                                float thumbWidth, float value) {
    Color thumbColor = slider.enabled() ? RendererSupport.THUMB
        : new Color(RendererSupport.THUMB, 0.38F);
    float width = Math.min(track.width(), Math.max(1.0F, thumbWidth));
    float thumbHeight = Math.min(track.height(),
        Math.max(RendererSupport.SLIDER_THUMB_MIN_HEIGHT,
            track.height() - RendererSupport.SLIDER_THUMB_VERTICAL_INSET * 2.0F));
    float thumbX = track.minX() + (track.width() - width) * value;
    float thumbY = track.centralY() - thumbHeight * 0.5F;
    graphics.setTint(thumbColor);
    graphics.drawRoundRect(thumbX, thumbY, width, thumbHeight,
        Math.min(RendererSupport.SLIDER_THUMB_RADIUS, thumbHeight * 0.5F));
    graphics.setTint(Color.WHITE);
  }

  private static void drawValue(Graphics graphics, Slider slider, Rectangle area) {
    RendererSupport.drawText(graphics, slider.displayValue(),
        area.maxX() + slider.valueGapForRender(), area.centralY(),
        RendererSupport.LEFT_CENTER, RendererSupport.FOREGROUND);
  }
}
