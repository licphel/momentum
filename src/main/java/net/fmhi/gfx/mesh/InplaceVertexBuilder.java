package net.fmhi.gfx.mesh;

import net.fmhi.codec.streaming.Buf;
import net.fmhi.math.Color;
import net.fmhi.math.MatrixStack;

/**
 * A {@link VertexBuilder} that stores vertex and index data in local heap buffers.
 *
 * <p>This implementation manages its own {@link Buf} staging buffers, a {@link MatrixStack}
 * for transforms, and a default color of white. Subclasses can extend it to add primitive
 * batching and state-change detection.
 *
 * @see VertexBuilder
 */
public class InplaceVertexBuilder implements VertexBuilder {
  protected final Buf vertexBuf = Buf.heap();
  protected final Buf indexBuf = Buf.heap();
  protected final MatrixStack matrixStack = new MatrixStack();
  protected Color color = Color.WHITE;
  protected int vertexCount;
  protected int indexCount;
  protected int flags = 0;

  @Override
  public MatrixStack transform() {
    return matrixStack;
  }

  @Override
  public Color color() {
    return color;
  }

  @Override
  public void setColor(Color color) {
    this.color = color;
  }

  @Override
  public int flags() {
    return flags;
  }

  @Override
  public void setFlags(int flags) {
    this.flags = flags;
  }

  @Override
  public Buf vertices() {
    return vertexBuf;
  }

  @Override
  public Buf indices() {
    return indexBuf;
  }

  @Override
  public int vertexCount() {
    return vertexCount;
  }

  @Override
  public int indexCount() {
    return indexCount;
  }

  @Override
  public void addVertex(int count) {
    vertexCount += count;
  }

  @Override
  public void addIndex(int count) {
    indexCount += count;
  }
}
