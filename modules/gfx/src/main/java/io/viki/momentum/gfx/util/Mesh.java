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
import io.viki.momentum.gfx.cmd.Encoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A retained collection of GPU geometry sections ready for rendering.
 *
 * <p>A {@code Mesh} holds a list of {@link Section}s, each pairing a {@link Material} with vertex
 * and index buffers. The section list can be replaced while compatible sections retain their GPU
 * buffers; this is used by chunk renderers when geometry changes.
 *
 * <p>This class implements {@link AutoCloseable}; closing a mesh releases the uniform buffer,
 * all vertex and index buffers, and all materials.
 *
 * @see Section
 * @see Material
 */
public final class Mesh implements AutoCloseable {
  private final List<Section> sections;
  private final List<Section> readonlySections;
  private boolean isEmpty;


  /**
   * Creates a new {@code Mesh} with the given uniform buffer and sections.
   *
   * @param sections the list of geometry sections
   */
  public Mesh(List<Section> sections) {
    this.sections = new ArrayList<>(sections);
    this.readonlySections = Collections.unmodifiableList(this.sections);
    updateEmptyState();
  }

  /**
   * Returns the sections of this mesh.
   *
   * @return the list of sections
   */
  public List<Section> sections() {
    return readonlySections;
  }

  /**
   * Replaces the section list and closes sections no longer retained by the mesh.
   *
   * <p>Sections present in both lists are compared by identity and remain open. The replacement
   * list becomes owned by this mesh.
   *
   * @param replacement the sections for the next mesh contents
   */
  public void replaceSections(List<Section> replacement) {
    for (Section old : sections) {
      boolean retained = false;
      for (Section next : replacement) {
        if (old == next) {
          retained = true;
          break;
        }
      }
      if (!retained) {
        old.close();
      }
    }
    sections.clear();
    sections.addAll(replacement);
    updateEmptyState();
  }

  /**
   * Returns whether the mesh has no content to draw.
   *
   * @return whether the mesh is empty
   */
  public boolean isEmpty() {
    return isEmpty;
  }

  /**
   * Draws all sections of this mesh using the given encoder.
   *
   * <p>Each section's material is applied, then geometry is drawn using either indexed or
   * non-indexed draw calls depending on whether the section has an index buffer.
   *
   * @param encoder the encoder to record draw commands into
   * @param slot    the resource binding slot for the material
   * @param ubo     the view-projection uniform buffer object
   */
  public void submit(Encoder encoder, int slot, BufferObject ubo) {
    for (Section s : sections) {
      if (s.vertexCount() == 0) {
        continue;
      }

      s.material().resourceSet().bindUniform(0, ubo, 64);
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
    for (Section s : sections) {
      s.close();
    }
  }

  private void updateEmptyState() {
    isEmpty = true;
    for (Section section : sections) {
      if (section.vertexCount() > 0) {
        isEmpty = false;
        return;
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(sections);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != this.getClass()) {
      return false;
    }
    var that = (Mesh) obj;
    return Objects.equals(this.sections, that.sections);
  }

  @Override
  public String toString() {
    return "Mesh[" +
        "sections=" + sections + ']';
  }
}
