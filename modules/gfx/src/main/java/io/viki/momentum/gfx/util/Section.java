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
 *
 * <p>This class is not thread-safe. Mutate and close a section on the render thread or through
 * the owning graphics queue, and do not use it while its owning {@link Mesh} is being drawn.
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

  /**
   * Creates a retained mesh section with no CPU staging arrays.
   *
   * @param material the material used to render this section
   * @param vbo the vertex buffer retained by this section
   * @param ibo the index buffer, or {@code null} for non-indexed geometry
   * @param vertexCount the number of vertices to draw
   * @param firstVertex the first vertex to draw
   * @param firstIndex the first index to draw
   * @param indexCount the number of indices to draw
   * @param topology the primitive topology used for drawing
   */
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

  /**
   * Creates a section and retains the CPU staging arrays used to build it.
   *
   * <p>The arrays are retained by reference so a mesh rebuild can reuse their capacity.
   * Callers must not modify them while the section is being rebuilt or drawn.
   *
   * @param material the material used to render this section
   * @param vbo the vertex buffer retained by this section
   * @param ibo the index buffer, or {@code null} for non-indexed geometry
   * @param vertexCount the number of vertices to draw
   * @param firstVertex the first vertex to draw
   * @param firstIndex the first index to draw
   * @param indexCount the number of indices to draw
   * @param topology the primitive topology used for drawing
   * @param vertexStaging the retained CPU-side vertex data
   * @param indexStaging the retained CPU-side index data
   */
  public Section(Material material, BufferObject vbo, @Nullable BufferObject ibo,
                 int vertexCount, int firstVertex, int firstIndex, int indexCount,
                 Topology topology, byte[] vertexStaging, byte[] indexStaging) {
    this(material, vbo, ibo, vertexCount, firstVertex, firstIndex, indexCount, topology);
    this.vertexStaging = vertexStaging;
    this.indexStaging = indexStaging;
  }

  /**
   * Returns the material used to render this section.
   *
   * @return the retained material
   */
  public Material material() {
    return material;
  }

  /**
   * Returns the vertex buffer retained by this section.
   *
   * @return the vertex buffer
   */
  public BufferObject vbo() {
    return vbo;
  }

  /**
   * Returns the index buffer retained by this section.
   *
   * @return the index buffer, or {@code null} for non-indexed geometry
   */
  public @Nullable BufferObject ibo() {
    return ibo;
  }

  /**
   * Returns the number of vertices drawn for this section.
   *
   * @return the vertex count
   */
  public int vertexCount() {
    return vertexCount;
  }

  /**
   * Returns the first vertex offset used for non-indexed drawing.
   *
   * @return the first vertex offset
   */
  public int firstVertex() {
    return firstVertex;
  }

  /**
   * Returns the first index offset used for indexed drawing.
   *
   * @return the first index offset
   */
  public int firstIndex() {
    return firstIndex;
  }

  /**
   * Returns the number of indices drawn for this section.
   *
   * @return the index count
   */
  public int indexCount() {
    return indexCount;
  }

  /**
   * Returns the primitive topology used to draw this section.
   *
   * @return the section topology
   */
  public Topology topology() {
    return topology;
  }

  /**
   * Returns whether this section can retain its material and GPU buffers for a new primitive.
   *
   * <p>Compatibility requires identity equality for the pipeline and resource layout,
   * equal topology, and the same indexed or non-indexed mode.
   *
   * @param pipeline the replacement primitive's render pipeline
   * @param layout the replacement primitive's resource-set layout
   * @param topology the replacement primitive's topology
   * @param indexed whether the replacement uses an index buffer
   * @return {@code true} when this section's resources can be reused
   */
  public boolean isCompatibleWith(Pipeline pipeline, ResourceSetLayout layout, Topology topology,
                                  boolean indexed) {
    return material.pipeline() == pipeline
        && material.resourceSet().layout() == layout
        && this.topology == topology
        && (ibo != null) == indexed;
  }

  /**
   * Uploads replacement geometry into this section's retained buffers and updates its draw counts.
   *
   * <p>Both staging arrays are retained by reference. The first vertex and index offsets
   * are reset to zero. An index upload occurs only when this section has an index buffer
   * and {@code indexBytes} is positive.
   *
   * @param vertices the replacement vertex data
   * @param vertexBytes the number of vertex bytes to upload
   * @param indices the replacement index data
   * @param indexBytes the number of index bytes to upload
   * @param vertexCount the replacement vertex count
   * @param indexCount the replacement index count
   */
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

  /**
   * Returns the reusable CPU-side vertex backing array.
   *
   * @return the retained vertex staging array by reference
   */
  byte[] vertexStaging() {
    return vertexStaging;
  }

  /**
   * Returns the reusable CPU-side index backing array.
   *
   * @return the retained index staging array by reference
   */
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
