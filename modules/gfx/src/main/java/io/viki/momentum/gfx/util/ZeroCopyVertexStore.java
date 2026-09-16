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

import io.viki.momentum.codec.streaming.BinaryBuffer;

/**
 * Zero-copy staging area that hands out the buffers' live backing arrays.
 *
 * <p>Not thread-safe; each instance must be confined to a single thread.
 */
public class ZeroCopyVertexStore implements VertexStore {
  private final boolean volatileData;
  private BinaryBuffer vertexBuf;
  private BinaryBuffer indexBuf;
  private int vertexCount;
  private int indexCount;

  /**
   * Creates a zero-copy vertex store with empty heap buffers.
   *
   * @param volatileData whether the data changes every draw
   */
  public ZeroCopyVertexStore(boolean volatileData) {
    // LE
    this.vertexBuf = BinaryBuffer.heap();
    this.indexBuf = BinaryBuffer.heap();
    this.volatileData = volatileData;
  }

  /**
   * Creates a zero-copy vertex store with empty heap buffers.
   */
  public ZeroCopyVertexStore() {
    this(false);
  }

  @Override
  public BinaryBuffer vertices() {
    return vertexBuf;
  }

  @Override
  public BinaryBuffer indices() {
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
      vertexBuf = BinaryBuffer.heap(vertexBuf.capacity());
      indexBuf = BinaryBuffer.heap(indexBuf.capacity());
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
