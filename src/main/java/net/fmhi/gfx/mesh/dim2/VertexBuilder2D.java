package net.fmhi.gfx.mesh.dim2;

import net.fmhi.gfx.mesh.VertexBuilder;
import net.fmhi.gfx.text.Text;
import net.fmhi.gfx.text.raster.Glyph;
import net.fmhi.gfx.text.raster.Raster;
import net.fmhi.gfx.texture.FragileTexture;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.gfx.texture.TexturePart;
import net.fmhi.math.*;
import net.fmhi.math.dim2.Curve2D;
import org.jspecify.annotations.Nullable;

/**
 * A 2D vertex builder that extends {@link VertexBuilder} with texture, shape, and text drawing
 * operations.
 *
 * <p>All coordinates are in world units and are transformed by the current transform stack.
 * Draw flags, color tint, and the current sampler affect subsequent draw calls until changed.
 * Texture source regions are specified in texel coordinates.
 *
 * @see VertexBuilder
 * @see Graphics2D
 */
public interface VertexBuilder2D extends VertexBuilder {
  /**
   * Draws a region of a texture at the given position and size.
   *
   * <p>The source region {@code (u, v, uw, vh)} is specified in texel coordinates.
   * The destination rectangle {@code (x, y, w, h)} is in world units and is transformed by
   * the current transform stack. Current draw flags, color, and sampler are applied.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param x   the X position in world units
   * @param y   the Y position in world units
   * @param w   the width in world units
   * @param h   the height in world units
   * @param u   the U coordinate of the source region in texels
   * @param v   the V coordinate of the source region in texels
   * @param uw  the width of the source region in texels
   * @param vh  the height of the source region in texels
   */
  void drawTexture(@Nullable FragileTexture tex, float x, float y, float w, float h, float u, float v
      , float uw, float vh);

  /**
   * Draws a source region of a texture into a destination rectangle.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   * @param src the source region in texel coordinates
   */
  default void drawTexture(@Nullable FragileTexture tex, Box2D dst, Box2D src) {
    if (tex == null) {
      return;
    }
    drawTexture(tex, dst.minX(), dst.minY(), dst.width(), dst.height(), src.minX(), src.minY(), src.width(),
        src.height());
  }

  /**
   * Draws the entire texture into a destination rectangle.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   */
  default void drawTexture(@Nullable FragileTexture tex, Box2D dst) {
    if (tex == null) {
      return;
    }
    Texture pinned = tex.pin();
    if (pinned == null) {
      return;
    }
    drawTexture(tex, dst, Box2D.create(0.0F, 0.0F, pinned.width(), pinned.height()));
  }

  /**
   * Draws the entire texture at the given position and size.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param x   the X position in world units
   * @param y   the Y position in world units
   * @param w   the width in world units
   * @param h   the height in world units
   */
  default void drawTexture(@Nullable FragileTexture tex, float x, float y, float w, float h) {
    if (tex == null) {
      return;
    }
    Texture pinned = tex.pin();
    if (pinned == null) {
      return;
    }
    drawTexture(tex, x, y, w, h, 0, 0, pinned.width(), pinned.height());
  }

  /**
   * Draws a texture part's region into a destination rectangle.
   *
   * @param texPart the texture part defining the source texture and region
   * @param dst     the destination rectangle in world units
   */
  default void drawTexture(@Nullable TexturePart texPart, Box2D dst) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), dst, texPart.region());
  }

  /**
   * Draws a texture part with an additional source region offset.
   *
   * @param texPart the texture part
   * @param dst     the destination rectangle in world units
   * @param src     the source region relative to the texture part's UV origin, in texels
   */
  default void drawTexture(@Nullable TexturePart texPart, Box2D dst, Box2D src) {
    if (texPart == null) {
      return;
    }
    drawTexture(new TexturePart(texPart, src), dst);
  }

  /**
   * Draws a texture part's full region at the given position and size.
   *
   * @param texPart the texture part
   * @param x       the X position in world units
   * @param y       the Y position in world units
   * @param w       the width in world units
   * @param h       the height in world units
   */
  default void drawTexture(@Nullable TexturePart texPart, float x, float y, float w, float h) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), x, y, w, h, texPart.u(), texPart.v(), texPart.width(), texPart.height());
  }

  /**
   * Draws a region of a texture part at the given position and size.
   *
   * <p>The source region is offset by the texture part's UV origin.
   *
   * @param texPart the texture part
   * @param x       the X position in world units
   * @param y       the Y position in world units
   * @param w       the width in world units
   * @param h       the height in world units
   * @param u       the U coordinate offset relative to the part's UV origin, in texels
   * @param v       the V coordinate offset relative to the part's UV origin, in texels
   * @param uw      the width of the source region in texels
   * @param vh      the height of the source region in texels
   */
  default void drawTexture(@Nullable TexturePart texPart, float x, float y, float w, float h, float u, float v,
                           float uw, float vh) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), x, y, w, h, u + texPart.u(), v + texPart.v(), uw, vh);
  }

  /**
   * Draws a {@link Drawable2D} into a destination rectangle.
   *
   * @param t   the drawable to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   */
  default void draw(@Nullable Drawable2D t, Box2D dst) {
    if (t == null) {
      return;
    }
    t.draw(this, dst.minX(), dst.minY(), dst.width(), dst.height(), 0, 0, 0, 0);
  }

  /**
   * Draws a {@link Drawable2D} with a source region into a destination rectangle.
   *
   * @param t   the drawable to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   * @param src the source region in texel coordinates
   */
  default void draw(@Nullable Drawable2D t, Box2D dst, Box2D src) {
    if (t == null) {
      return;
    }
    t.draw(this, dst.minX(), dst.minY(), dst.width(), dst.height(), src.minX(), src.minY(), src.width(), src.height());
  }

  /**
   * Draws a {@link Drawable2D} at the given position and size.
   *
   * @param t the drawable to draw; does nothing if {@code null}
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  default void draw(@Nullable Drawable2D t, float x, float y, float w, float h) {
    if (t == null) {
      return;
    }
    t.draw(this, x, y, w, h, 0, 0, 0, 0);
  }

  /**
   * Draws a {@link Drawable2D} at the given position, size, and source region.
   *
   * @param t  the drawable to draw; does nothing if {@code null}
   * @param x  the X position in world units
   * @param y  the Y position in world units
   * @param w  the width in world units
   * @param h  the height in world units
   * @param u  the U coordinate offset in texels
   * @param v  the V coordinate offset in texels
   * @param uw the U coordinate range in texels
   * @param vh the V coordinate range in texels
   */
  default void draw(@Nullable Drawable2D t, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    if (t == null) {
      return;
    }
    t.draw(this, x, y, w, h, u, v, uw, vh);
  }

  /**
   * Draws a filled rectangle at the given position and size.
   *
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  void drawRectangle(float x, float y, float w, float h);

  /**
   * Draws a filled rectangle.
   *
   * @param dst the destination rectangle in world units
   */
  default void drawRectangle(Box2D dst) {
    drawRectangle(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws the outline of a rectangle as four line segments.
   *
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  default void drawRectangleFrame(float x, float y, float w, float h) {
    drawLine(x, y, x + w, y);
    drawLine(x, y, x, y + h);
    drawLine(x + w, y, x + w, y + h);
    drawLine(x, y + h, x + w, y + h);
  }

  /**
   * Draws the outline of a rectangle as four line segments.
   *
   * @param dst the destination rectangle in world units
   */
  default void drawRectangleFrame(Box2D dst) {
    drawRectangleFrame(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws a line segment between two points.
   *
   * @param x1 the X coordinate of the first endpoint
   * @param y1 the Y coordinate of the first endpoint
   * @param x2 the X coordinate of the second endpoint
   * @param y2 the Y coordinate of the second endpoint
   */
  void drawLine(float x1, float y1, float x2, float y2);

  /**
   * Draws a line segment between two vectors.
   *
   * @param from the first endpoint
   * @param to   the second endpoint
   */
  default void drawLine(Vector2 from, Vector2 to) {
    drawLine(from.x(), from.y(), to.x(), to.y());
  }

  /**
   * Draws a single point at the given coordinates.
   *
   * @param x the X coordinate in world units
   * @param y the Y coordinate in world units
   */
  void drawPoint(float x, float y);

  /**
   * Draws a single point at the given position.
   *
   * @param at the position in world units
   */
  default void drawPoint(Vector2 at) {
    drawPoint(at.x(), at.y());
  }

  /**
   * Draws a filled triangle.
   *
   * @param x1 the X coordinate of the first vertex
   * @param y1 the Y coordinate of the first vertex
   * @param x2 the X coordinate of the second vertex
   * @param y2 the Y coordinate of the second vertex
   * @param x3 the X coordinate of the third vertex
   * @param y3 the Y coordinate of the third vertex
   */
  void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3);

  /**
   * Draws a filled triangle.
   *
   * @param a the first vertex
   * @param b the second vertex
   * @param c the third vertex
   */
  default void drawTriangle(Vector2 a, Vector2 b, Vector2 c) {
    drawTriangle(a.x(), a.y(), b.x(), b.y(), c.x(), c.y());
  }

  /**
   * Draws the outline of a triangle as three line segments.
   *
   * @param x1 the X coordinate of the first vertex
   * @param y1 the Y coordinate of the first vertex
   * @param x2 the X coordinate of the second vertex
   * @param y2 the Y coordinate of the second vertex
   * @param x3 the X coordinate of the third vertex
   * @param y3 the Y coordinate of the third vertex
   */
  default void drawTriangleFrame(float x1, float y1, float x2, float y2, float x3, float y3) {
    drawLine(x1, y1, x2, y2);
    drawLine(x2, y2, x3, y3);
    drawLine(x3, y3, x1, y1);
  }

  /**
   * Draws the outline of a triangle as three line segments.
   *
   * @param a the first vertex
   * @param b the second vertex
   * @param c the third vertex
   */
  default void drawTriangleFrame(Vector2 a, Vector2 b, Vector2 c) {
    drawTriangleFrame(a.x(), a.y(), b.x(), b.y(), c.x(), c.y());
  }

  /**
   * Draws a filled oval (axis-aligned ellipse).
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width
   * @param h the height
   */
  default void drawOval(float x, float y, float w, float h) {
    float rx = w / 2f, ry = h / 2f, cx = x + rx, cy = y + ry;
    int segments = computeOvalSegments(x, y, w, h);
    for (int i = 0; i < segments; i++) {
      float a1 = (float) (i * 2.0 * Math.PI / segments);
      float a2 = (float) ((i + 1) * 2.0 * Math.PI / segments);
      float[] sc1 = FastTrigonometric.sincos(a1);
      float ca1 = sc1[1];
      float sa1 = sc1[0];
      float[] sc2 = FastTrigonometric.sincos(a2);
      float ca2 = sc2[1];
      float sa2 = sc2[0];
      drawTriangle(cx, cy,
          cx + rx * ca1, cy + ry * sa1,
          cx + rx * ca2, cy + ry * sa2);
    }
  }

  /**
   * Draws a filled oval (axis-aligned ellipse).
   *
   * @param dst the bounding box in world units
   */
  default void drawOval(Box2D dst) {
    drawOval(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws the outline of an oval as line segments. The segment count is automatically
   * determined from the transformed size — larger ovals use more segments for smoothness.
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width
   * @param h the height
   */
  default void drawOvalFrame(float x, float y, float w, float h) {
    float rx = w / 2.0f;
    float ry = h / 2.0f;
    float cx = x + rx;
    float cy = y + ry;
    int segments = computeOvalSegments(x, y, w, h);

    for (int i = 0; i < segments; i++) {
      float a1 = (float) (i * 2.0 * Math.PI / segments);
      float a2 = (float) ((i + 1) * 2.0 * Math.PI / segments);
      float[] sc1 = FastTrigonometric.sincos(a1);
      float ca1 = sc1[1];
      float sa1 = sc1[0];
      float[] sc2 = FastTrigonometric.sincos(a2);
      float ca2 = sc2[1];
      float sa2 = sc2[0];
      drawLine(cx + rx * ca1, cy + ry * sa1,
               cx + rx * ca2, cy + ry * sa2);
    }
  }

  /**
   * Draws the outline of an oval as line segments. The segment count is automatically
   * determined from the transformed size — larger ovals use more segments for smoothness.
   *
   * @param dst the bounding box in world units
   */
  default void drawOvalFrame(Box2D dst) {
    drawOvalFrame(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws a parametric curve as line segments.
   *
   * <p>The curve is sampled at {@code segments} evenly-spaced values of {@code t} in
   * {@code [0, 1]} and drawn as connected line segments. More segments produce a smoother curve.
   *
   * @param curve    the parametric curve to evaluate
   * @param segments the number of line segments
   */
  default void drawCurve(Curve2D curve, int segments) {
    if (segments <= 0) {
      return;
    }

    Vector2 prev = curve.evaluate(0.0F);
    for (int i = 1; i <= segments; i++) {
      float t = (float) i / segments;
      Vector2 curr = curve.evaluate(t);
      drawLine(prev.x(), prev.y(), curr.x(), curr.y());
      prev = curr;
    }
  }

  /**
   * Draws a filled polygon as a triangle fan.
   *
   * <p>Does nothing if fewer than 3 vertices are provided.
   *
   * @param vertices the polygon vertices in order
   */
  default void drawPolygon(Vector2... vertices) {
    if (vertices.length < 3) return;
    Vector2 v0 = vertices[0];
    for (int i = 1; i < vertices.length - 1; i++) {
      drawTriangle(v0, vertices[i], vertices[i + 1]);
    }
  }

  /**
   * Draws the outline of a polygon as a closed line loop.
   *
   * <p>Does nothing if fewer than 2 vertices are provided.
   *
   * @param vertices the polygon vertices in order
   */
  default void drawPolygonFrame(Vector2... vertices) {
    if (vertices.length < 2) {
      return;
    }
    for (int i = 0; i < vertices.length; i++) {
      Vector2 a = vertices[i];
      Vector2 b = vertices[(i + 1) % vertices.length];
      drawLine(a, b);
    }
  }

  /**
   * Draws a text blob at the given position with the specified alignment.
   *
   * <p>The alignment determines how the text is positioned relative to the anchor point
   * {@code (x, y)}. For example, {@link Alignment#CENTRAL} centers the text both horizontally
   * and vertically.
   *
   * @param text      the text blob to draw; does nothing if {@code null}
   * @param x         the anchor X coordinate in world units
   * @param y         the anchor Y coordinate in world units
   * @param alignment the alignment relative to the anchor point
   */
  default void drawText(@Nullable Text text, float x, float y, Alignment alignment) {
    if (text == null) {
      return;
    }

    Raster raster = text.raster();
    Box2D rasterBd = raster.bounds();

    float tx = x;
    float ty = y;
    switch (alignment.horizontal()) {
      case 0 -> tx -= rasterBd.width() / 2.0F;
      case 1 -> tx -= rasterBd.width();
    }
    switch (alignment.vertical()) {
      case 0 -> ty -= rasterBd.height() / 2.0F;
      case 1 -> ty -= rasterBd.height();
    }

    // Normalize entry coordinates: rasterBd.minY() is the pen-space origin offset.
    // Subtract it so that the text block's visual top aligns with (tx, ty).
    float originY = rasterBd.minY();

    Color originalColor = color();

    for (Raster.Entry entry : raster.entries()) {
      Glyph cg = entry.glyph();
      if (cg == null) {
        continue;
      }

      // Bearings are baked in (gx, gy, pixelW, pixelH)
      setColor(entry.color());
      Box2D bounds = entry.bounds();
      drawTexture(cg.texPart(), tx + bounds.minX(), ty + bounds.minY() - originY, bounds.width(), bounds.height());
    }

    for (Raster.Stroke stroke : raster.strokes()) {
      setColor(stroke.color());
      Box2D bounds = stroke.bounds();
      drawRectangle(tx + bounds.minX(), ty + bounds.minY() - originY, bounds.width(), bounds.height());
    }

    setColor(originalColor);
  }

  /**
   * Draws a text blob at the given position with {@link Alignment#LEFT_UP} alignment.
   *
   * @param text the text blob to draw; does nothing if {@code null}
   * @param x    the anchor X coordinate in world units
   * @param y    the anchor Y coordinate in world units
   */
  default void drawText(@Nullable Text text, float x, float y) {
    drawText(text, x, y, Alignment.LEFT_UP);
  }

  /**
   * Draws a text blob at the given position with the specified alignment.
   *
   * @param text      the text blob to draw; does nothing if {@code null}
   * @param pos       the anchor position in world units
   * @param alignment the alignment relative to the anchor point
   */
  default void drawText(@Nullable Text text, Vector2 pos, Alignment alignment) {
    drawText(text, pos.x(), pos.y(), alignment);
  }

  /**
   * Draws a text blob at the given position with {@link Alignment#LEFT_UP} alignment.
   *
   * @param text the text blob to draw; does nothing if {@code null}
   * @param pos  the anchor position in world units
   */
  default void drawText(@Nullable Text text, Vector2 pos) {
    drawText(text, pos.x(), pos.y());
  }

  /**
   * Computes the number of segments for an oval based on its approximated transformed radius.
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width
   * @param h the height
   * @return the segment count, clamped to {@code [8, 128]}
   */
  private int computeOvalSegments(float x, float y, float w, float h) {
    float rx = w / 2.0f;
    float ry = h / 2.0f;
    float cx = x + rx;
    float cy = y + ry;
    Vector3 tc = transform().top().transform(new Vector3(cx, cy, 0));
    Vector3 tr = transform().top().transform(new Vector3(cx + rx, cy, 0));
    float dx = tr.x() - tc.x();
    float dy = tr.y() - tc.y();
    float screenRx = (float) Math.sqrt(dx * dx + dy * dy);
    int segments = (int) (screenRx * Math.PI * 0.5f);
    return Math.clamp(segments, 8, 128);
  }
}
