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

package net.fmhi.gfx.mesh;

import net.fmhi.gfx.buffer.BufferObject;
import net.fmhi.gfx.pipe.Topology;
import org.jspecify.annotations.Nullable;

/**
 * A subsection of a {@link Mesh}, pairing a {@link Material} with vertex and index buffers.
 *
 * <p>Each section defines a single draw call: the material determines the shading, the vertex
 * and index buffers provide the geometry data, and the topology controls primitive assembly.
 *
 * @param material    the material applied when drawing this section
 * @param vbo         the vertex buffer
 * @param ibo         the index buffer, or {@code null} for non-indexed draws
 * @param vertexCount the number of vertices
 * @param firstVertex the offset of the first vertex
 * @param firstIndex  the offset of the first index
 * @param indexCount  the number of indices
 * @param topology    the primitive topology
 */
public record Section(Material material,
                      BufferObject vbo,
                      @Nullable BufferObject ibo,
                      int vertexCount,
                      int firstVertex,
                      int firstIndex,
                      int indexCount,
                      Topology topology) implements AutoCloseable {
  /**
   * Releases the vertex buffer, index buffer, and material held by this section.
   */
  @Override
  public void close() {
    vbo.close();
    if (ibo != null) {
      ibo.close();
    }
    material.close();
  }
}
