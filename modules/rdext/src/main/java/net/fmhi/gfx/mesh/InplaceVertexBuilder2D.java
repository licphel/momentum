package net.fmhi.gfx.mesh;

import net.fmhi.gfx.math.TransformHandler;
import net.fmhi.gfx.texture.FragileTexture;
import net.fmhi.gfx.texture.Texture;
import net.fmhi.math.Vector3;
import org.jspecify.annotations.Nullable;

/**
 * An inplace implementation of {@link VertexBuilder2D} that transforms vertex positions through
 * the current transform stack and writes directly to local staging buffers.
 *
 * <p>This class provides the core drawing primitives: textured sprites, colored rectangles,
 * lines, and points. Each draw call transforms world-space coordinates, applies flip flags,
 * packs the current color, and appends vertex and index data. Subclasses can override
 * {@link #assertPrimitive} and {@link #assertTexture} to detect state changes and trigger flushes.
 *
 * @see VertexBuilder2D
 * @see AbstractStatefulGraphics2D
 */
public class InplaceVertexBuilder2D extends InplaceVertexBuilder implements VertexBuilder2D {
  protected final TransformHandler transformHandler;

  /**
   * Creates an inplace vertex builder 2D.
   *
   * @param transformHandler the backend-specific transform handler
   */
  public InplaceVertexBuilder2D(TransformHandler transformHandler) {
    this.transformHandler = transformHandler;
  }

  @Override
  public void drawTexture(@Nullable FragileTexture tex, float x, float y, float w, float h, float u, float v,
                          float uw, float vh) {
    if (tex == null) {
      return;
    }
    Texture pinned = tex.pin();
    if (pinned == null) {
      return;
    }
    assertPrimitive(Primitive2D.TEXTURE_TRIANGLE_INDEXED);
    assertTexture(pinned);

    float u1 = transformHandler.u(pinned, u);
    float v1 = transformHandler.v(pinned, v);
    float u2 = transformHandler.u(pinned, u + uw);
    float v2 = transformHandler.v(pinned, v + vh);

    if ((flags & DrawingFlags.FLIP_X) != 0) {
      float t = u1;
      u1 = u2;
      u2 = t;
    }
    if ((flags & DrawingFlags.FLIP_Y) != 0) {
      float t = v1;
      v1 = v2;
      v2 = t;
    }

    Vector3 p0 = transform().top().transform(new Vector3(x, y, 0));
    Vector3 p1 = transform().top().transform(new Vector3(x + w, y, 0));
    Vector3 p2 = transform().top().transform(new Vector3(x + w, y + h, 0));
    Vector3 p3 = transform().top().transform(new Vector3(x, y + h, 0));
    float dMid = (p0.z() + p2.z()) * 0.5F;
    long col = color.pack();

    putPosColorUv(p0.x(), p0.y(), p0.z(), col, u1, v1);
    putPosColorUv(p1.x(), p1.y(), dMid, col, u2, v1);
    putPosColorUv(p2.x(), p2.y(), p2.z(), col, u2, v2);
    putPosColorUv(p3.x(), p3.y(), dMid, col, u1, v2);
    putQuadIndices(vertexCount());

    addVertex(4);
    addIndex(6);
  }

  @Override
  public void drawRectangle(float x, float y, float w, float h) {
    assertPrimitive(Primitive2D.COLOR_TRIANGLE_INDEXED);

    Vector3 p0 = transform().top().transform(new Vector3(x, y, 0));
    Vector3 p1 = transform().top().transform(new Vector3(x + w, y, 0));
    Vector3 p2 = transform().top().transform(new Vector3(x + w, y + h, 0));
    Vector3 p3 = transform().top().transform(new Vector3(x, y + h, 0));
    float dMid = (p0.z() + p2.z()) * 0.5F;
    long col = color.pack();

    putPosColor(p0.x(), p0.y(), p0.z(), col);
    putPosColor(p1.x(), p1.y(), dMid, col);
    putPosColor(p2.x(), p2.y(), p2.z(), col);
    putPosColor(p3.x(), p3.y(), dMid, col);
    putQuadIndices(vertexCount);

    addVertex(4);
    addIndex(6);
  }

  @Override
  public void drawLine(float x1, float y1, float x2, float y2) {
    assertPrimitive(Primitive2D.COLOR_LINE);

    Vector3 t1 = transform().top().transform(new Vector3(x1, y1, 0));
    Vector3 t2 = transform().top().transform(new Vector3(x2, y2, 0));
    long col = color.pack();

    putPosColor(t1.x(), t1.y(), t1.z(), col);
    putPosColor(t2.x(), t2.y(), t2.z(), col);

    addVertex(2);
  }

  @Override
  public void drawPoint(float x, float y) {
    assertPrimitive(Primitive2D.COLOR_POINT);

    Vector3 t = transform().top().transform(new Vector3(x, y, 0));
    putPosColor(t.x(), t.y(), t.z(), color.pack());

    addVertex(1);
  }

  @Override
  public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
    assertPrimitive(Primitive2D.COLOR_TRIANGLE);

    Vector3 t1 = transform().top().transform(new Vector3(x1, y1, 0));
    Vector3 t2 = transform().top().transform(new Vector3(x2, y2, 0));
    Vector3 t3 = transform().top().transform(new Vector3(x3, y3, 0));

    // ensure CCW winding after transform
    // since we use y-flip in 2D rendering, cross > 0, instead, means CW.
    float cross = (t2.x() - t1.x()) * (t3.y() - t1.y()) - (t2.y() - t1.y()) * (t3.x() - t1.x());
    if (cross > 0) {
      Vector3 tmp = t2;
      t2 = t3;
      t3 = tmp;
    }

    long col = color.pack();
    putPosColor(t1.x(), t1.y(), t1.z(), col);
    putPosColor(t2.x(), t2.y(), t2.z(), col);
    putPosColor(t3.x(), t3.y(), t3.z(), col);

    addVertex(3);
  }

  protected void assertPrimitive(Primitive2D primitive) {
  }

  protected void assertTexture(Texture tex) {
  }
}
