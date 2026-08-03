package net.fmhi.gfx.brush;

import net.fmhi.codec.streaming.Buf;

/**
 * In-memory staging area for vertex and index data.
 *
 * <p>Holds the raw vertex and index buffers along with counts of written elements; the
 * counts are advanced through {@link #addVertex(int)} and {@link #addIndex(int)}.
 * Not thread-safe; each instance must be confined to a single thread.
 */
public class VertexData {
  protected final Buf vertexBuf = Buf.heap();
  protected final Buf indexBuf = Buf.heap();
  protected int vertexCount;
  protected int indexCount;

  /**
   * Returns the buffer holding interleaved vertex data.
   *
   * @return the vertex buffer
   */
  public Buf vertices() {
    return vertexBuf;
  }

  /**
   * Returns the buffer holding index data.
   *
   * @return the index buffer
   */
  public Buf indices() {
    return indexBuf;
  }

  /**
   * Returns the number of vertices written so far.
   *
   * @return the vertex count
   */
  public int vertexCount() {
    return vertexCount;
  }

  /**
   * Returns the number of indices written so far.
   *
   * @return the index count
   */
  public int indexCount() {
    return indexCount;
  }

  /**
   * Records the given number of additional vertices.
   *
   * @param count the number of vertices written
   */
  public void addVertex(int count) {
    vertexCount += count;
  }

  /**
   * Records the given number of additional indices.
   *
   * @param count the number of indices written
   */
  public void addIndex(int count) {
    indexCount += count;
  }

  /**
   * Resets the recorded counts and clears both buffers.
   */
  public void clear() {
    vertexCount = 0;
    indexCount = 0;
    vertexBuf.clear();
    indexBuf.clear();
  }
}
