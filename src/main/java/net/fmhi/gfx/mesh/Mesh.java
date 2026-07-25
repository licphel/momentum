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
import net.fmhi.gfx.cmd.Encoder;
import net.fmhi.math.Matrix4x4;

import java.util.List;

/**
 * An immutable collection of GPU geometry sections ready for rendering.
 *
 * <p>A {@code Mesh} holds a uniform buffer object for view-projection data and a list of
 * {@link Section}s, each pairing a {@link Material} with vertex and index buffers. The mesh can
 * upload a view-projection matrix and draw all sections to an {@link Encoder}.
 *
 * <p>This class implements {@link AutoCloseable}; closing a mesh releases the uniform buffer,
 * all vertex and index buffers, and all materials.
 *
 * @see Section
 * @see Material
 */
public final class Mesh implements AutoCloseable {
  private final BufferObject ubo;
  private final List<Section> sections;

  /**
   * Creates a new {@code Mesh} with the given uniform buffer and sections.
   *
   * @param ubo      the uniform buffer for view-projection data
   * @param sections the list of geometry sections
   */
  public Mesh(BufferObject ubo, List<Section> sections) {
    this.ubo = ubo;
    this.sections = sections;
  }

  /**
   * Returns the sections of this mesh.
   *
   * @return the list of sections
   */
  public List<Section> sections() {
    return sections;
  }

  /**
   * Uploads a view-projection matrix to the uniform buffer.
   *
   * @param vpm the view-projection matrix to upload
   */
  public void uploadVP(Matrix4x4 vpm) {
    byte[] bytes = Matrix4x4.pack(vpm);
    ubo.submit(bytes, 0, bytes.length);
  }

  /**
   * Draws all sections of this mesh using the given encoder.
   *
   * <p>Each section's material is applied, then geometry is drawn using either indexed or
   * non-indexed draw calls depending on whether the section has an index buffer.
   *
   * @param encoder the encoder to record draw commands into
   * @param slot    the resource binding slot for the material
   */
  public void draw(Encoder encoder, int slot) {
    for (Section s : sections) {
      s.material().apply(encoder, slot);
      encoder.setVertexBuffer(s.vbo());
      encoder.setTopology(s.topology());
      if (s.ibo() != null) {
        encoder.setIndexBuffer(s.ibo());
        encoder.drawIndexed(s.indexCount(), s.firstIndex());
      } else {
        encoder.draw(s.vertexCount(), s.firstVertex());
      }
    }
  }

  /**
   * Releases all GPU resources held by this mesh, including the uniform buffer,
   * all vertex and index buffers, and all materials.
   */
  @Override
  public void close() {
    ubo.close();
    for (Section s : sections) {
      s.close();
    }
  }
}
