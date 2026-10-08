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
 * Reuses the CPU staging arrays retained by a mesh while rebuilding its sections.
 *
 * <p>{@link #clear()} advances to the next section rather than replacing the current backing
 * arrays. A buffer grows only when the new geometry exceeds the previous section's capacity.
 * This store is single-threaded and must not be used while the mesh is being drawn.
 */
public final class MeshRecycledVertexStore implements VertexStore {
  private final Mesh mesh;
  private BinaryBuffer vertices;
  private BinaryBuffer indices;
  private int sectionIndex;
  private int vertexCount;
  private int indexCount;

  /**
   * Creates a store positioned at the first section of {@code mesh}.
   *
   * @param mesh the old reused mesh
   */
  public MeshRecycledVertexStore(Mesh mesh) {
    this.mesh = mesh;
    this.vertices = BinaryBuffer.heap();
    this.indices = BinaryBuffer.heap();
    prepareSection();
  }

  @Override
  public BinaryBuffer vertices() {
    return vertices;
  }

  @Override
  public BinaryBuffer indices() {
    return indices;
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
    sectionIndex++;
    prepareSection();
  }

  @Override
  public byte[] recordVertices() {
    byte[] backing = vertices.backingArray();
    return backing != null ? backing : vertices.copiedArray();
  }

  @Override
  public byte[] recordIndices() {
    byte[] backing = indices.backingArray();
    return backing != null ? backing : indices.copiedArray();
  }

  private void prepareSection() {
    byte[] vertexBacking = new byte[0];
    byte[] indexBacking = new byte[0];
    if (sectionIndex < mesh.sections().size()) {
      Section section = mesh.sections().get(sectionIndex);
      vertexBacking = section.vertexStaging();
      indexBacking = section.indexStaging();
    }
    vertices = BinaryBuffer.wrap(vertexBacking);
    indices = BinaryBuffer.wrap(indexBacking);
    vertices.clear();
    indices.clear();
  }
}
