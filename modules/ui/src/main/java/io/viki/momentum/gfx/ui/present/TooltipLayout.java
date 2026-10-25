package io.viki.momentum.gfx.ui.present;

import io.viki.momentum.gfx.text.TextBuilder;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.util.impl.Graphics;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures and positions rich-text tooltip contents for automatic and custom skins.
 *
 * <p>The layout keeps its line list immutable and does not mutate source text. Use it on the UI
 * thread because its text values may be mutable and its drawing operations use a UI graphics
 * context; instances are not thread-safe.
 *
 * @param bounds the tooltip bounds constrained to the canvas
 * @param lines the wrapped text lines and their measured bounds
 * @param paddingX horizontal inset between the tooltip edge and its contents
 * @param paddingY vertical inset between the tooltip edge and its contents
 * @param lineGap vertical spacing between adjacent lines
 */
public record TooltipLayout(Rectangle bounds, List<Line> lines, float paddingX, float paddingY, float lineGap) {
  /** Default maximum tooltip width including horizontal padding, in logical canvas units. */
  public static final float DEFAULT_MAX_WIDTH = 256F;
  /** Maximum inset between a tooltip and the visible canvas edge. */
  private static final float CANVAS_MARGIN = 2F;

  /**
   * Creates a layout with an immutable copy of its line list.
   *
   * @param bounds the tooltip bounds
   * @param lines the wrapped text lines and their measured bounds
   * @param paddingX horizontal inset between the tooltip edge and its contents
   * @param paddingY vertical inset between the tooltip edge and its contents
   * @param lineGap vertical spacing between adjacent lines
   */
  public TooltipLayout {
    lines = List.copyOf(lines);
  }

  /**
   * Creates a wrapped tooltip layout positioned near the requested origin.
   *
   * <p>The resulting bounds are constrained to the canvas, and the supplied text entries are
   * measured in their original order.
   *
   * @param values text entries to place on separate lines
   * @param origin requested top-left position of the tooltip
   * @param canvas visible area that contains the tooltip
   * @param settings spacing and width limits for the layout
   * @return the measured tooltip layout
   */
  public static TooltipLayout create(List<Text> values, Vector2 origin, Rectangle canvas, Settings settings) {
    float availableWidth = Math.max(0, Math.min(settings.maxWidth(), canvas.width() - CANVAS_MARGIN * 2));
    float px = Math.min(settings.paddingX(), availableWidth / 2);
    float py = Math.min(settings.paddingY(), Math.max(0, canvas.height() - CANVAS_MARGIN * 2) / 2);
    float textWidth = Math.max(1, availableWidth - px * 2);
    float naturalWidth = 0;
    for (Text value : values) {
      naturalWidth = Math.max(naturalWidth, value.raster().bounds().width());
    }
    textWidth = Math.max(1, Math.min(textWidth, naturalWidth));
    List<Line> lines = new ArrayList<>(values.size());
    float width = 0, height = 0;
    for (Text value : values) {
      Text wrapped = wrap(value, textWidth);
      Rectangle measured = wrapped.raster().bounds();
      lines.add(new Line(wrapped, measured));
      width = Math.max(width, measured.width());
      height += measured.height();
    }
    height += settings.lineGap() * Math.max(0, values.size() - 1);
    Rectangle area = constrain(Rectangle.of(origin.x(), origin.y(),
        Math.min(availableWidth, width + px * 2), height + py * 2), canvas);
    return new TooltipLayout(area, lines, px, py, settings.lineGap());
  }

  /**
   * Creates a mutable text wrapper around source text with a maximum line width.
   *
   * @param source text to wrap without modifying it
   * @param width maximum line width in text-layout units
   * @return a new text value configured with the requested width
   */
  static Text wrap(Text source, float width) {
    return new TextBuilder().append(source).maxWidth(width).build();
  }

  /**
   * Constrains a requested rectangle to the canvas while preserving its position when possible.
   *
   * <p>The rectangle is reduced when it exceeds the available area, with an inset applied on all
   * four sides. Canvas origins other than zero are supported.
   *
   * @param requested desired popup bounds
   * @param canvas available canvas bounds
   * @return bounds fully contained within the canvas inset
   */
  public static Rectangle constrain(Rectangle requested, Rectangle canvas) {
    float margin = Math.min(CANVAS_MARGIN, Math.min(canvas.width(), canvas.height()) / 2);
    float width = Math.min(requested.width(), Math.max(0, canvas.width() - margin * 2));
    float height = Math.min(requested.height(), Math.max(0, canvas.height() - margin * 2));
    float x = Math.clamp(requested.minX(), canvas.minX() + margin, canvas.maxX() - margin - width);
    float y = Math.clamp(requested.minY(), canvas.minY() + margin, canvas.maxY() - margin - height);
    return Rectangle.of(x, y, width, height);
  }

  /**
   * Draws the wrapped lines inside the layout bounds, clipping content that exceeds the available
   * area.
   *
   * <p>Any scissor state pushed for clipping is restored before this method returns or propagates
   * a drawing failure.
   *
   * @param graphics graphics context that receives the text
   */
  public void drawContents(Graphics graphics) {
    Rectangle content = Rectangle.of(bounds.minX() + paddingX, bounds.minY() + paddingY,
        Math.max(0, bounds.width() - paddingX * 2), Math.max(0, bounds.height() - paddingY * 2));
    if (content.width() <= 0 || content.height() <= 0) {
      return;
    }
    float height = lineGap * Math.max(0, lines.size() - 1);
    boolean clip = false;
    for (Line line : lines) {
      height += line.bounds().height();
      clip |= line.bounds().width() > content.width();
    }
    clip |= height > content.height();
    if (clip) {
      graphics.pushScissor(content);
    }
    try {
      float y = content.minY();
      for (Line line : lines) {
        if (y >= content.maxY()) {
          break;
        }
        // Graphics already aligns the vertical line box; only horizontal ink bearings need adjustment.
        graphics.drawText(line.text(), content.minX() - line.bounds().minX(), y);
        y += line.bounds().height() + lineGap;
      }
    } finally {
      if (clip) {
        graphics.popScissor();
      }
    }
  }

  /**
   * Controls the padding, line spacing, and maximum width used to lay out tooltip text.
   *
   * <p>All components are primitive values, so instances are immutable and safe to share between
   * threads.
   *
   * @param paddingX horizontal inset, which must be nonnegative
   * @param paddingY vertical inset, which must be nonnegative
   * @param lineGap vertical gap between lines, which must be nonnegative
   * @param maxWidth maximum tooltip width including horizontal padding, in logical canvas units
   */
  public record Settings(float paddingX, float paddingY, float lineGap, float maxWidth) {
    /**
     * Creates validated tooltip layout settings.
     *
     * @param paddingX horizontal inset, which must be nonnegative
     * @param paddingY vertical inset, which must be nonnegative
     * @param lineGap vertical gap between lines, which must be nonnegative
     * @param maxWidth maximum tooltip width including horizontal padding, in logical canvas units
     * @throws IllegalArgumentException if a padding or gap is negative, or {@code maxWidth} is not positive
     */
    public Settings {
      if (paddingX < 0 || paddingY < 0 || lineGap < 0 || maxWidth <= 0) {
        throw new IllegalArgumentException("Tooltip padding/gap must be nonnegative and maximum width positive");
      }
    }
  }

  /**
   * Holds one wrapped text line and the bounds used to measure and draw it.
   *
   * <p>The record is an immutable carrier, but thread safety of {@code text} follows the supplied
   * {@link Text} implementation.
   *
   * @param text text value for this line
   * @param bounds measured bounds of the line
   */
  public record Line(Text text, Rectangle bounds) {
  }
}
