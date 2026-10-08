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

import io.viki.momentum.gfx.buffer.BufferObject;
import io.viki.momentum.gfx.pipe.Pipeline;
import io.viki.momentum.gfx.pipe.Topology;
import io.viki.momentum.gfx.shader.ResourceSetLayout;
import org.jspecify.annotations.Nullable;

/**
 * A subsection of a {@link Mesh}, pairing a material with retained vertex and index buffers.
 *
 * <p>The geometry counters are mutable so a retained mesh can upload a replacement without
 * allocating another pair of GPU buffers. A section is compatible with a replacement when its
 * pipeline, resource layout, topology, and indexed/non-indexed mode are unchanged.
 */
public final class Section implements AutoCloseable {
  private final Material material;
  private final BufferObject vbo;
  private final @Nullable BufferObject ibo;
  private int vertexCount;
  private int firstVertex;
  private int firstIndex;
  private int indexCount;
  private final Topology topology;
  private byte[] vertexStaging = new byte[0];
  private byte[] indexStaging = new byte[0];

  /** Creates a retained mesh section. */
  public Section(Material material, BufferObject vbo, @Nullable BufferObject ibo,
                 int vertexCount, int firstVertex, int firstIndex, int indexCount,
                 Topology topology) {
    this.material = material;
    this.vbo = vbo;
    this.ibo = ibo;
    this.vertexCount = vertexCount;
    this.firstVertex = firstVertex;
    this.firstIndex = firstIndex;
    this.indexCount = indexCount;
    this.topology = topology;
  }

  /** Creates a section and retains the CPU staging arrays used to build it. */
  public Section(Material material, BufferObject vbo, @Nullable BufferObject ibo,
                 int vertexCount, int firstVertex, int firstIndex, int indexCount,
                 Topology topology, byte[] vertexStaging, byte[] indexStaging) {
    this(material, vbo, ibo, vertexCount, firstVertex, firstIndex, indexCount, topology);
    this.vertexStaging = vertexStaging;
    this.indexStaging = indexStaging;
  }

  public Material material() {
    return material;
  }

  public BufferObject vbo() {
    return vbo;
  }

  public @Nullable BufferObject ibo() {
    return ibo;
  }

  public int vertexCount() {
    return vertexCount;
  }

  public int firstVertex() {
    return firstVertex;
  }

  public int firstIndex() {
    return firstIndex;
  }

  public int indexCount() {
    return indexCount;
  }

  public Topology topology() {
    return topology;
  }

  /** Returns whether this section can retain its material and GPU buffers for a new primitive. */
  public boolean compatible(Pipeline pipeline, ResourceSetLayout layout, Topology topology,
                            boolean indexed) {
    return material.pipeline() == pipeline
        && material.resourceSet().layout() == layout
        && this.topology == topology
        && (ibo != null) == indexed;
  }

  /** Uploads replacement geometry into this section's retained buffers. */
  public void updateGeometry(byte[] vertices, int vertexBytes, byte[] indices, int indexBytes,
                             int vertexCount, int indexCount) {
    vbo.replace(vertices, vertexBytes);
    if (ibo != null && indexBytes > 0) {
      ibo.replace(indices, indexBytes);
    }
    this.vertexCount = vertexCount;
    this.firstVertex = 0;
    this.firstIndex = 0;
    this.indexCount = indexCount;
    this.vertexStaging = vertices;
    this.indexStaging = indices;
  }

  /** Returns the reusable CPU-side vertex backing array. */
  byte[] vertexStaging() {
    return vertexStaging;
  }

  /** Returns the reusable CPU-side index backing array. */
  byte[] indexStaging() {
    return indexStaging;
  }

  /** Releases the vertex buffer, index buffer, and material held by this section. */
  @Override
  public void close() {
    vbo.close();
    if (ibo != null) {
      ibo.close();
    }
    material.close();
  }
}
