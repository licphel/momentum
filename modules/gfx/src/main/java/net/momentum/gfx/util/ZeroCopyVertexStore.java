package net.momentum.gfx.util;

import net.momentum.codec.streaming.CursorBuffer;

/**
 * Zero-copy staging area that hands out the buffers' live backing arrays.
 *
 * <p>Not thread-safe; each instance must be confined to a single thread.
 */
public class ZeroCopyVertexStore implements VertexStore {
  private final boolean volatileData;
  private CursorBuffer vertexBuf;
  private CursorBuffer indexBuf;
  private int vertexCount;
  private int indexCount;

  /**
   * Creates a zero-copy vertex store with empty heap buffers.
   *
   * @param volatileData whether the data changes every draw
   */
  public ZeroCopyVertexStore(boolean volatileData) {
    // LE
    this.vertexBuf = CursorBuffer.heap();
    this.indexBuf = CursorBuffer.heap();
    this.volatileData = volatileData;
  }

  /**
   * Creates a zero-copy vertex store with empty heap buffers.
   */
  public ZeroCopyVertexStore() {
    this(false);
  }

  @Override
  public CursorBuffer vertices() {
    return vertexBuf;
  }

  @Override
  public CursorBuffer indices() {
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

  @Override
  public void clear() {
    vertexCount = 0;
    indexCount = 0;

    if (volatileData) {
      // Changes every frame: safe to clear
      vertexBuf.clear();
      indexBuf.clear();
    } else {
      // Create new buffers to release references to old backing arrays.
      // This prevents accidental mutation of data that may still be in use.
      vertexBuf = CursorBuffer.heap(vertexBuf.capacity());
      indexBuf = CursorBuffer.heap(indexBuf.capacity());
    }
  }

  /**
   * Returns the backing array of the vertex buffer without copying.
   *
   * <p>The array is the buffer's live storage: modifying it modifies the buffer, and the
   * reference becomes invalid once the buffer is cleared.
   *
   * @return the backing byte array of the vertex buffer
   */
  @Override
  public byte[] recordVertices() {
    byte[] bytes = vertexBuf.backingArray();
    if (bytes != null) {
      return bytes;
    }
    return vertexBuf.copiedArray();
  }

  /**
   * Returns the backing array of the index buffer without copying.
   *
   * <p>The array is the buffer's live storage: modifying it modifies the buffer, and the
   * reference becomes invalid once the buffer is cleared.
   *
   * @return the backing byte array of the index buffer
   */
  @Override
  public byte[] recordIndices() {
    byte[] bytes = indexBuf.backingArray();
    if (bytes != null) {
      return bytes;
    }
    return indexBuf.copiedArray();
  }
}
