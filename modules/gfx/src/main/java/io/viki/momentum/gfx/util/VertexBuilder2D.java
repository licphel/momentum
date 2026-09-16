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

package io.viki.momentum.gfx.util;

import io.viki.momentum.gfx.tint.Gradient;
import io.viki.momentum.gfx.math.TransformHandler;
import io.viki.momentum.gfx.text.Text;
import io.viki.momentum.gfx.text.raster.Glyph;
import io.viki.momentum.gfx.text.raster.Raster;
import io.viki.momentum.gfx.texture.Drawable2D;
import io.viki.momentum.gfx.texture.FragileTexture;
import io.viki.momentum.gfx.texture.Texture;
import io.viki.momentum.gfx.texture.TexturePart;
import io.viki.momentum.math.shape.Poly;
import io.viki.momentum.math.shape.Rectangle;
import io.viki.momentum.math.util.FastTrigonometric;
import io.viki.momentum.math.Vector2;
import io.viki.momentum.math.Vector3;
import io.viki.momentum.math.curve.Curve;
import org.jspecify.annotations.Nullable;

/**
 * A 2D vertex builder extending {@link VertexBuilder} with texture, shape, and text drawing.
 *
 * <p>All coordinates are in world units and pass through the current transform stack. Draw
 * flags, gradient tint, and the bound texture affect subsequent draw calls until changed;
 * texture source regions are specified in texel coordinates. Not thread-safe; each instance
 * must be confined to a single thread.
 *
 * @see VertexBuilder
 */
public class VertexBuilder2D extends VertexBuilder {
  protected @Nullable Primitive2D currentPrimitive;
  protected @Nullable Texture currentTexture;
  /** Whether subsequent texture vertices use the active camera's vertically flipped convention. */
  protected boolean uvYFlipped;

  /**
   * Creates a new {@code VertexBuilder2D} writing into the given staging area.
   *
   * @param data             the staging area receiving vertices and indices
   * @param transformHandler the transform handler used for coordinate conversion
   */
  public VertexBuilder2D(VertexStore data, TransformHandler transformHandler) {
    super(data, transformHandler);
  }

  /**
   * Returns whether subsequent textured vertices use the vertically flipped UV convention.
   *
   * <p>This is a rendering-state value supplied by the camera-owning layer; the builder does
   * not retain a camera reference.
   *
   * @return {@code true} when the active UV convention is vertically flipped
   */
  public boolean isUvYFlipped() {
    return uvYFlipped;
  }

  /**
   * Sets the vertical UV convention for subsequently recorded textured vertices.
   *
   * <p>This setting does not rewrite vertices that have already been recorded. A graphics
   * implementation should change it only after flushing the current batch; meshes therefore
   * retain the convention that was active when their vertices were recorded.
   *
   * @param uvYFlipped {@code true} when the active camera uses a vertically flipped convention
   */
  public void setUvYFlipped(boolean uvYFlipped) {
    this.uvYFlipped = uvYFlipped;
  }

  /**
   * Draws a region of a texture into the given destination rectangle.
   *
   * <p>The source region {@code (u, v, uw, vh)} is specified in texel coordinates; the
   * destination rectangle is in world units and passes through the current transform stack.
   * The current draw flags, gradient, and texture are applied.
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
  public void drawTexture(@Nullable FragileTexture tex, float x, float y, float w, float h, float u, float v
      , float uw, float vh) {
    if (tex == null) {
      return;
    }
    Texture pinned = tex.pin();
    if (pinned == null) {
      return;
    }
    setPrimitive(Primitive2D.TEXTURE_TRIANGLE_INDEXED);
    setTexture(pinned);

    float u1 = transformHandler.u(pinned, u);
    float v1 = transformHandler.v(pinned, v);
    float u2 = transformHandler.u(pinned, u + uw);
    float v2 = transformHandler.v(pinned, v + vh);

    if ((flags & DrawFlags.FLIP_X) != 0) {
      float t = u1;
      u1 = u2;
      u2 = t;
    }
    if (((flags & DrawFlags.FLIP_Y) == 0) ^ uvYFlipped) {
      float t = v1;
      v1 = v2;
      v2 = t;
    }

    putPosColorUv(x, y, 0, gradient.getColor(0), u1, v1);
    putPosColorUv(x + w, y, 0, gradient.getColor(1), u2, v1);
    putPosColorUv(x + w, y + h, 0, gradient.getColor(2), u2, v2);
    putPosColorUv(x, y + h, 0, gradient.getColor(3), u1, v2);
    endQuad();
  }

  /**
   * Draws the region of a texture given by {@code src} into the destination rectangle.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   * @param src the source region in texel coordinates
   */
  public void drawTexture(@Nullable FragileTexture tex, Rectangle dst, Rectangle src) {
    if (tex == null) {
      return;
    }
    drawTexture(tex, dst.minX(), dst.minY(), dst.width(), dst.height(), src.minX(), src.minY(), src.width(),
        src.height());
  }

  /**
   * Draws the full texture into a destination rectangle.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   */
  public void drawTexture(@Nullable FragileTexture tex, Rectangle dst) {
    if (tex == null) {
      return;
    }
    Texture pinned = tex.pin();
    if (pinned == null) {
      return;
    }
    drawTexture(tex, dst, Rectangle.of(0.0F, 0.0F, pinned.width(), pinned.height()));
  }

  /**
   * Draws the full texture at the given position and size.
   *
   * @param tex the texture to draw; does nothing if {@code null}
   * @param x   the X position in world units
   * @param y   the Y position in world units
   * @param w   the width in world units
   * @param h   the height in world units
   */
  public void drawTexture(@Nullable FragileTexture tex, float x, float y, float w, float h) {
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
   * Draws the region of a texture part into a destination rectangle.
   *
   * @param texPart the texture part providing the source texture and region
   * @param dst     the destination rectangle in world units
   */
  public void drawTexture(@Nullable TexturePart texPart, Rectangle dst) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), dst, texPart.region());
  }

  /**
   * Draws a source region of a texture part into a destination rectangle.
   *
   * <p>The source region is offset from the texture part's UV origin.
   *
   * @param texPart the texture part
   * @param dst     the destination rectangle in world units
   * @param src     the source region relative to the texture part's UV origin, in texels
   */
  public void drawTexture(@Nullable TexturePart texPart, Rectangle dst, Rectangle src) {
    if (texPart == null) {
      return;
    }
    drawTexture(new TexturePart(texPart, src), dst);
  }

  /**
   * Draws the full region of a texture part at the given position and size.
   *
   * @param texPart the texture part
   * @param x       the X position in world units
   * @param y       the Y position in world units
   * @param w       the width in world units
   * @param h       the height in world units
   */
  public void drawTexture(@Nullable TexturePart texPart, float x, float y, float w, float h) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), x, y, w, h, texPart.u(), texPart.v(), texPart.width(), texPart.height());
  }

  /**
   * Draws a region of a texture part at the given position and size.
   *
   * <p>The source region is offset from the texture part's UV origin.
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
  public void drawTexture(@Nullable TexturePart texPart, float x, float y, float w, float h, float u, float v,
                          float uw, float vh) {
    if (texPart == null) {
      return;
    }
    drawTexture(texPart.src(), x, y, w, h, u + texPart.u(), v + texPart.v(), uw, vh);
  }

  /**
   * Draws a {@link Drawable2D} scaled to fit a destination rectangle.
   *
   * @param t   the drawable to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   */
  public void draw(@Nullable Drawable2D t, Rectangle dst) {
    if (t == null) {
      return;
    }
    t.draw(this, dst.minX(), dst.minY(), dst.width(), dst.height(), 0, 0, 0, 0);
  }

  /**
   * Draws a {@link Drawable2D}, mapping a source region onto a destination rectangle.
   *
   * @param t   the drawable to draw; does nothing if {@code null}
   * @param dst the destination rectangle in world units
   * @param src the source region in texel coordinates
   */
  public void draw(@Nullable Drawable2D t, Rectangle dst, Rectangle src) {
    if (t == null) {
      return;
    }
    t.draw(this, dst.minX(), dst.minY(), dst.width(), dst.height(), src.minX(), src.minY(), src.width(), src.height());
  }

  /**
   * Draws a {@link Drawable2D} scaled to the given position and size.
   *
   * @param t the drawable to draw; does nothing if {@code null}
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  public void draw(@Nullable Drawable2D t, float x, float y, float w, float h) {
    if (t == null) {
      return;
    }
    t.draw(this, x, y, w, h, 0, 0, 0, 0);
  }

  /**
   * Draws a {@link Drawable2D}, mapping a source region onto the given position and size.
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
  public void draw(@Nullable Drawable2D t, float x, float y, float w, float h, float u, float v, float uw, float vh) {
    if (t == null) {
      return;
    }
    t.draw(this, x, y, w, h, u, v, uw, vh);
  }

  /**
   * Draws a filled rectangle with the given dimensions.
   *
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  public void drawRectangle(float x, float y, float w, float h) {
    setPrimitive(Primitive2D.COLOR_TRIANGLE_INDEXED);

    putPosColor(x, y, 0, gradient.getColor(0));
    putPosColor(x + w, y, 0, gradient.getColor(1));
    putPosColor(x + w, y + h, 0, gradient.getColor(2));
    putPosColor(x, y + h, 0, gradient.getColor(3));
    endQuad();
  }

  /**
   * Draws a filled rectangle covering the given destination rectangle.
   *
   * @param dst the destination rectangle in world units
   */
  public void drawRectangle(Rectangle dst) {
    drawRectangle(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws the outline of a rectangle as four connected line segments.
   *
   * @param x the X position in world units
   * @param y the Y position in world units
   * @param w the width in world units
   * @param h the height in world units
   */
  public void drawRectangleFrame(float x, float y, float w, float h) {
    drawLine(x, y, x + w, y);
    drawLine(x, y, x, y + h);
    drawLine(x + w, y, x + w, y + h);
    drawLine(x, y + h, x + w, y + h);
  }

  /**
   * Draws the outline of a rectangle as four connected line segments.
   *
   * @param dst the destination rectangle in world units
   */
  public void drawRectangleFrame(Rectangle dst) {
    drawRectangleFrame(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws a line segment between two endpoints.
   *
   * @param x1 the X coordinate of the first endpoint
   * @param y1 the Y coordinate of the first endpoint
   * @param x2 the X coordinate of the second endpoint
   * @param y2 the Y coordinate of the second endpoint
   */
  public void drawLine(float x1, float y1, float x2, float y2) {
    setPrimitive(Primitive2D.COLOR_LINE);

    putPosColor(x1, y1, 0, gradient.getColor(0));
    putPosColor(x2, y2, 0, gradient.getColor(1));
    data.addVertex(2);
  }

  /**
   * Draws a line segment between two positions.
   *
   * @param from the first endpoint in world units
   * @param to   the second endpoint in world units
   */
  public void drawLine(Vector2 from, Vector2 to) {
    drawLine(from.x(), from.y(), to.x(), to.y());
  }

  /**
   * Draws a point at the specified coordinates.
   *
   * @param x the X coordinate in world units
   * @param y the Y coordinate in world units
   */
  public void drawPoint(float x, float y) {
    setPrimitive(Primitive2D.COLOR_POINT);

    putPosColor(x, y, 0, gradient.getColor(0));
    data.addVertex(1);
  }

  /**
   * Draws a point at the specified position.
   *
   * @param at the position in world units
   */
  public void drawPoint(Vector2 at) {
    drawPoint(at.x(), at.y());
  }

  /**
   * Draws a filled triangle from three vertex positions.
   *
   * @param x1 the X coordinate of the first vertex
   * @param y1 the Y coordinate of the first vertex
   * @param x2 the X coordinate of the second vertex
   * @param y2 the Y coordinate of the second vertex
   * @param x3 the X coordinate of the third vertex
   * @param y3 the Y coordinate of the third vertex
   */
  public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
    setPrimitive(Primitive2D.COLOR_TRIANGLE);

    putPosColor(x1, y1, 0, gradient.getColor(0));
    putPosColor(x2, y2, 0, gradient.getColor(1));
    putPosColor(x3, y3, 0, gradient.getColor(2));
    data.addVertex(3);
  }

  /**
   * Draws a filled triangle from three vertex positions.
   *
   * @param a the first vertex in world units
   * @param b the second vertex in world units
   * @param c the third vertex in world units
   */
  public void drawTriangle(Vector2 a, Vector2 b, Vector2 c) {
    drawTriangle(a.x(), a.y(), b.x(), b.y(), c.x(), c.y());
  }

  /**
   * Draws the outline of a triangle as three connected line segments.
   *
   * @param x1 the X coordinate of the first vertex
   * @param y1 the Y coordinate of the first vertex
   * @param x2 the X coordinate of the second vertex
   * @param y2 the Y coordinate of the second vertex
   * @param x3 the X coordinate of the third vertex
   * @param y3 the Y coordinate of the third vertex
   */
  public void drawTriangleFrame(float x1, float y1, float x2, float y2, float x3, float y3) {
    drawLine(x1, y1, x2, y2);
    drawLine(x2, y2, x3, y3);
    drawLine(x3, y3, x1, y1);
  }

  /**
   * Draws the outline of a triangle as three connected line segments.
   *
   * @param a the first vertex in world units
   * @param b the second vertex in world units
   * @param c the third vertex in world units
   */
  public void drawTriangleFrame(Vector2 a, Vector2 b, Vector2 c) {
    drawTriangleFrame(a.x(), a.y(), b.x(), b.y(), c.x(), c.y());
  }

  /**
   * Draws a filled oval (axis-aligned ellipse) inscribed in the given bounding box.
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width of the bounding box
   * @param h the height of the bounding box
   */
  public void drawOval(float x, float y, float w, float h) {
    float rx = w / 2F;
    float ry = h / 2F;
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
      drawTriangle(cx, cy,
          cx + rx * ca1, cy + ry * sa1,
          cx + rx * ca2, cy + ry * sa2);
    }
  }

  /**
   * Draws a filled oval (axis-aligned ellipse) inscribed in the given bounding box.
   *
   * @param dst the bounding box in world units
   */
  public void drawOval(Rectangle dst) {
    drawOval(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws the outline of an oval as connected line segments.
   *
   * <p>The segment count adapts to the transformed size so that larger ovals stay smooth.
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width of the bounding box
   * @param h the height of the bounding box
   */
  public void drawOvalFrame(float x, float y, float w, float h) {
    float rx = w / 2.0F;
    float ry = h / 2.0F;
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
   * Draws the outline of an oval as connected line segments.
   *
   * <p>The segment count adapts to the transformed size so that larger ovals stay smooth.
   *
   * @param dst the bounding box in world units
   */
  public void drawOvalFrame(Rectangle dst) {
    drawOvalFrame(dst.minX(), dst.minY(), dst.width(), dst.height());
  }

  /**
   * Draws a parametric curve as connected line segments.
   *
   * <p>The curve is sampled at {@code segments} evenly spaced parameter values in
   * {@code [0, 1]}; more segments produce a smoother result.
   *
   * @param curve    the parametric curve to evaluate
   * @param segments the number of line segments
   */
  public void drawCurve(Curve curve, int segments) {
    if (segments <= 0) {
      return;
    }

    setPrimitive(Primitive2D.COLOR_LINE);

    Vector3 prev = curve.evaluate(0.0F);
    for (int i = 1; i <= segments; i++) {
      float t = (float) i / segments;
      Vector3 curr = curve.evaluate(t);

      // Inlining of drawLine, for depth
      putPosColor(prev.x(), prev.y(), prev.z(), gradient.getColor(0));
      putPosColor(curr.x(), curr.y(), curr.z(), gradient.getColor(1));

      prev = curr;
    }

    data.addVertex(2 * segments);
  }

  /**
   * Draws a filled polygon as a triangle fan.
   *
   * <p>Does nothing if fewer than 3 vertices are provided.
   *
   * @param vertices the polygon vertices in winding order
   */
  public void drawPoly(Vector2... vertices) {
    if (vertices.length < 3) {
      return;
    }
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
   * @param vertices the polygon vertices in winding order
   */
  public void drawPolyFrame(Vector2... vertices) {
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
   * Draws a filled polygon from its convex components.
   *
   * <p>Each convex component is triangulated as a fan around its first vertex. Empty polygons
   * produce no vertices.
   *
   * @param poly the polygon to draw
   */
  public void drawPoly(Poly poly) {
    for (int convexIndex = 0; convexIndex < poly.convexCount(); convexIndex++) {
      int vertexCount = poly.vertexCount(convexIndex);
      if (vertexCount < 3) {
        continue;
      }

      float firstX = poly.vertexX(convexIndex, 0);
      float firstY = poly.vertexY(convexIndex, 0);
      for (int vertexIndex = 1; vertexIndex < vertexCount - 1; vertexIndex++) {
        drawTriangle(firstX, firstY,
            poly.vertexX(convexIndex, vertexIndex), poly.vertexY(convexIndex, vertexIndex),
            poly.vertexX(convexIndex, vertexIndex + 1), poly.vertexY(convexIndex, vertexIndex + 1));
      }
    }
  }

  /**
   * Draws the outline of each convex component in a polygon as closed line loops.
   *
   * <p>For a concave or compound polygon, shared or decomposition edges are drawn as part of
   * the individual component outlines.
   *
   * @param poly the polygon to outline
   */
  public void drawPolyFrame(Poly poly) {
    for (int convexIndex = 0; convexIndex < poly.convexCount(); convexIndex++) {
      int vertexCount = poly.vertexCount(convexIndex);
      if (vertexCount < 2) {
        continue;
      }

      for (int vertexIndex = 0; vertexIndex < vertexCount; vertexIndex++) {
        int nextIndex = (vertexIndex + 1) % vertexCount;
        drawLine(
            poly.vertexX(convexIndex, vertexIndex), poly.vertexY(convexIndex, vertexIndex),
            poly.vertexX(convexIndex, nextIndex), poly.vertexY(convexIndex, nextIndex));
      }
    }
  }

  /**
   * Draws a text blob anchored at the given position with the specified alignment.
   *
   * <p>The alignment determines how the text is positioned relative to the anchor point
   * {@code (x, y)}. For example, {@link Alignment#CENTRAL} centers the text both
   * horizontally and vertically.
   *
   * @param text      the text blob to draw; does nothing if {@code null}
   * @param x         the anchor X coordinate in world units
   * @param y         the anchor Y coordinate in world units
   * @param alignment the alignment relative to the anchor point
   */
  public void drawText(@Nullable Text text, float x, float y, Alignment alignment) {
    if (text == null) {
      return;
    }

    Raster raster = text.raster();
    Rectangle rasterBd = raster.bounds();

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

    Gradient originalColor = gradient();
    for (Raster.Entry entry : raster.entries()) {
      Glyph cg = entry.glyph();
      if (cg == null) {
        continue;
      }

      // Bearings are baked in (gx, gy, pixelW, pixelH)
      setTint(entry.gradient());
      Rectangle bounds = entry.bounds();
      drawTexture(cg.texPart(), tx + bounds.minX(), ty + bounds.minY() - originY, bounds.width(), bounds.height());
    }

    for (Raster.Stroke stroke : raster.strokes()) {
      setTint(stroke.gradient());
      Rectangle bounds = stroke.bounds();
      drawRectangle(tx + bounds.minX(), ty + bounds.minY() - originY, bounds.width(), bounds.height());
    }

    setTint(originalColor);
  }

  /**
   * Draws a text blob anchored at the given position with {@link Alignment#LEFT_UP}.
   *
   * @param text the text blob to draw; does nothing if {@code null}
   * @param x    the anchor X coordinate in world units
   * @param y    the anchor Y coordinate in world units
   */
  public void drawText(@Nullable Text text, float x, float y) {
    drawText(text, x, y, Alignment.LEFT_UP);
  }

  /**
   * Draws a text blob anchored at the given position with the specified alignment.
   *
   * @param text      the text blob to draw; does nothing if {@code null}
   * @param pos       the anchor position in world units
   * @param alignment the alignment relative to the anchor point
   */
  public void drawText(@Nullable Text text, Vector2 pos, Alignment alignment) {
    drawText(text, pos.x(), pos.y(), alignment);
  }

  /**
   * Draws a text blob anchored at the given position with {@link Alignment#LEFT_UP}.
   *
   * @param text the text blob to draw; does nothing if {@code null}
   * @param pos  the anchor position in world units
   */
  public void drawText(@Nullable Text text, Vector2 pos) {
    drawText(text, pos.x(), pos.y());
  }

  /**
   * Computes the segment count for an oval from its transformed radius, so that detail
   * scales with the on-screen size.
   *
   * @param x the X position of the bounding box
   * @param y the Y position of the bounding box
   * @param w the width
   * @param h the height
   * @return the segment count, clamped to {@code [8, 128]}
   */
  private int computeOvalSegments(float x, float y, float w, float h) {
    float rx = w / 2.0F;
    float ry = h / 2.0F;
    float cx = x + rx;
    float cy = y + ry;
    Vector3 tc = transform().top().transform(new Vector3(cx, cy, 0));
    Vector3 tr = transform().top().transform(new Vector3(cx + rx, cy, 0));
    float dx = tr.x() - tc.x();
    float dy = tr.y() - tc.y();
    float screenRx = (float) Math.sqrt(dx * dx + dy * dy);
    int segments = (int) (screenRx * Math.PI * 0.5F);
    return Math.clamp(segments, 8, 128);
  }

  /**
   * Hook invoked just before the current primitive or texture is replaced, allowing
   * subclasses to flush pending state tied to the previous values.
   */
  protected void flush0() {
  }

  /**
   * Selects the primitive type for subsequent draws.
   *
   * <p>Changing the primitive flushes pending draws first; a switch to the already active
   * primitive does nothing.
   *
   * @param primitive the primitive type to use
   */
  public void setPrimitive(@Nullable Primitive2D primitive) {
    if (primitive != currentPrimitive) {
      flush0();
      currentPrimitive = primitive;
    }
  }

  /**
   * Binds the texture for subsequent draws.
   *
   * <p>Changing the texture flushes pending draws first; a switch to the already bound
   * texture does nothing.
   *
   * @param tex the texture to bind; {@code null} unbinds
   */
  public void setTexture(@Nullable Texture tex) {
    if (tex != currentTexture) {
      flush0();
      currentTexture = tex;
    }
  }
}
